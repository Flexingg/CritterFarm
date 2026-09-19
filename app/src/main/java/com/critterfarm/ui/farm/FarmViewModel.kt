package com.critterfarm.ui.farm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.ClaimResult
import com.critterfarm.data.FeedResult
import com.critterfarm.data.GameRepository
import com.critterfarm.data.GameRules
import com.critterfarm.data.Quest
import com.critterfarm.data.QuestCatalog
import com.critterfarm.data.QuestClaimResult
import com.critterfarm.data.QuestsForDay
import com.critterfarm.data.StreakRules
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.FarmInventoryEntity
import com.critterfarm.health.HealthConnectAvailability
import com.critterfarm.health.HealthConnectManager
import com.critterfarm.ui.model.GameZone
import com.critterfarm.ui.toMetric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Intents in, state out — the Farm screen never mutates state directly. */
sealed class FarmIntent {
    /** Re-check everything: local game state, permission status, and a health sync. */
    data object Refresh : FarmIntent()

    /** Tap on the chest. */
    data object ClaimDailyTurn : FarmIntent()

    /** Celebration modal was dismissed. */
    data object DismissCelebration : FarmIntent()

    /** Snackbar message was dismissed or timed out. */
    data object DismissMessage : FarmIntent()

    /** Spend one treat on the critter — the daily ritual that gives treats a purpose. */
    data object FeedCritter : FarmIntent()

    /** Tap Claim on a completed-unclaimed quest. */
    data class ClaimQuest(val questId: String) : FarmIntent()
}

data class FarmUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isClaiming: Boolean = false,
    val critter: CritterEntity? = null,
    val inventory: FarmInventoryEntity? = null,
    val todayLog: DailySummaryLogEntity? = null,
    val healthAvailability: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    val grantedPermissions: Set<String> = emptySet(),
    val celebration: ClaimResult.Claimed? = null,
    val snackbarMessage: String? = null,
    /** Today's three quests, seeded by the date — the same day always shows the same three. */
    val todayQuests: List<Quest> = QuestsForDay.forDate(LocalDate.now(), QuestCatalog.ALL),
    val claimedQuestIds: Set<String> = emptySet(),
    val zoneStreaks: Map<GameZone, Int> = emptyMap(),
) {
    val dormantZones: List<GameZone>
        get() = GameZone.entries.filter { it.isDormant(grantedPermissions) }

    val hasAnyHealthConnection: Boolean
        get() = grantedPermissions.isNotEmpty()

    /** The chain: consecutive claimed days, what it pays, and the insurance you hold. */
    val streakDays: Int get() = inventory?.claimStreak ?: 0
    val bestStreak: Int get() = inventory?.bestStreak ?: 0
    val streakFreezes: Int get() = inventory?.streakFreezes ?: 0
    val streakMultiplier: Double get() = GameRules.claimMultiplier(streakDays)
}

/**
 * Owns the Farm screen's state. All reward math and persistence live in [GameRepository];
 * this class only orchestrates when to call it and shapes the result into [FarmUiState].
 */
class FarmViewModel(
    private val gameRepository: GameRepository,
    private val healthConnectManager: HealthConnectManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FarmUiState())
    val uiState: StateFlow<FarmUiState> = _uiState.asStateFlow()

    init {
        observeLocalState()
        onIntent(FarmIntent.Refresh)
    }

    fun onIntent(intent: FarmIntent) {
        when (intent) {
            FarmIntent.Refresh -> refresh()
            FarmIntent.ClaimDailyTurn -> claimDailyTurn()
            FarmIntent.DismissCelebration -> _uiState.update { it.copy(celebration = null) }
            FarmIntent.DismissMessage -> _uiState.update { it.copy(snackbarMessage = null) }
            FarmIntent.FeedCritter -> feedCritter()
            is FarmIntent.ClaimQuest -> claimQuest(intent.questId)
        }
    }

    private data class LocalState(
        val critter: CritterEntity?,
        val inventory: FarmInventoryEntity?,
        val todayLog: DailySummaryLogEntity?,
        val claimedQuestIds: Set<String>,
        val zoneStreaks: Map<GameZone, Int>,
    )

    private fun observeLocalState() {
        viewModelScope.launch {
            val today = LocalDate.now()
            combine(
                gameRepository.critter,
                gameRepository.inventory,
                gameRepository.observeDailyLog(today),
                gameRepository.observeQuestClaims(today),
                gameRepository.dailyLogs,
            ) { critter, inventory, todayLog, claims, allLogs ->
                LocalState(
                    critter = critter,
                    inventory = inventory,
                    todayLog = todayLog,
                    claimedQuestIds = claims.map { it.questId }.toSet(),
                    zoneStreaks = GameZone.entries.associateWith { zone ->
                        StreakRules.currentStreak(allLogs, zone.toMetric(), today)
                    },
                )
            }.collect { state ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        critter = state.critter,
                        inventory = state.inventory,
                        todayLog = state.todayLog,
                        claimedQuestIds = state.claimedQuestIds,
                        zoneStreaks = state.zoneStreaks,
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            gameRepository.ensureInitialGameState()
            refreshPermissionState()
            syncHealthData()
        }
    }

    private suspend fun refreshPermissionState() {
        val availability = healthConnectManager.getAvailability()
        val granted = if (availability is HealthConnectAvailability.Available) {
            healthConnectManager.getGrantedPermissions()
        } else {
            emptySet()
        }
        _uiState.update { it.copy(healthAvailability = availability, grantedPermissions = granted) }
    }

    private suspend fun syncHealthData() {
        val state = _uiState.value
        if (state.healthAvailability !is HealthConnectAvailability.Available || !state.hasAnyHealthConnection) {
            return
        }
        _uiState.update { it.copy(isSyncing = true) }
        try {
            // Exercise the spec'd 48h catch-up so a sync always reflects the full window since
            // the last time the app synced, even if the app wasn't opened yesterday.
            val since = gameRepository.getLastSyncedAt() ?: Instant.now().minus(Duration.ofHours(48))
            healthConnectManager.computeDelta(since)

            // DailySummaryLogEntity stores whole-day totals with replace semantics, so the
            // authoritative write uses the always-accurate "since midnight" snapshot rather
            // than the delta above (which would double-count on a second sync within the day).
            val snapshot = healthConnectManager.getTodaySnapshot()
            gameRepository.upsertDailyStats(
                date = snapshot.date,
                steps = snapshot.steps ?: 0L,
                caloriesBurnedKcal = snapshot.totalCaloriesBurnedKcal
                    ?: snapshot.activeCaloriesBurnedKcal
                    ?: 0.0,
                caloriesConsumedKcal = snapshot.dietaryEnergyKcal ?: 0.0,
                hydrationMl = snapshot.hydrationMl ?: 0.0,
                sleepMinutes = snapshot.sleepMinutesLastNight ?: 0L,
                workouts = snapshot.exerciseSessions?.size ?: 0,
                weightKg = snapshot.latestWeightKg,
                syncedAt = Instant.now(),
            )
        } catch (e: SecurityException) {
            _uiState.update {
                it.copy(
                    snackbarMessage = "A permission changed mid-sync — the farm still works, " +
                        "just with a bit less data for now.",
                )
            }
        } finally {
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    private fun claimDailyTurn() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClaiming = true) }
            when (val result = gameRepository.claimDailyTurn(LocalDate.now())) {
                is ClaimResult.Claimed -> _uiState.update {
                    it.copy(isClaiming = false, celebration = result)
                }
                is ClaimResult.AlreadyClaimed -> _uiState.update {
                    it.copy(
                        isClaiming = false,
                        snackbarMessage = "You've already opened today's chest — Sprout will " +
                            "have a fresh one tomorrow!",
                    )
                }
                ClaimResult.NothingToClaim -> _uiState.update {
                    it.copy(
                        isClaiming = false,
                        snackbarMessage = "No activity synced yet today — take a step or sip " +
                            "some water, then try again!",
                    )
                }
            }
        }
    }

    /**
     * Spend a treat on Sprout. This closes the loop that v1.1 left open: train → earn treats →
     * care for the critter. Before this, treats were a counter that only ever went up.
     */
    private fun feedCritter() {
        viewModelScope.launch {
            when (val result = gameRepository.feedCritter()) {
                is FeedResult.Fed -> _uiState.update {
                    it.copy(
                        snackbarMessage = "Sprout munched a treat! Hunger ${result.hunger}, " +
                            "happiness ${result.happiness} — ${result.treatsLeft} treats left.",
                    )
                }
                FeedResult.NoTreats -> _uiState.update {
                    it.copy(
                        snackbarMessage = "The treat jar is empty — one workout earns 5 treats " +
                            "for Sprout.",
                    )
                }
            }
        }
    }

    private fun claimQuest(questId: String) {
        viewModelScope.launch {
            when (val result = gameRepository.claimQuest(LocalDate.now(), questId)) {
                is QuestClaimResult.Claimed -> _uiState.update {
                    it.copy(
                        snackbarMessage = "Quest complete! ${result.quest.text} — " +
                            rewardCopy(result.coinsEarned, result.treatsEarned),
                    )
                }
                QuestClaimResult.AlreadyClaimed -> _uiState.update {
                    it.copy(snackbarMessage = "Already claimed — that one's done for today.")
                }
                QuestClaimResult.NotComplete -> _uiState.update {
                    it.copy(snackbarMessage = "Not quite there yet — keep going!")
                }
                QuestClaimResult.UnknownQuest, QuestClaimResult.NoInventory -> _uiState.update {
                    it.copy(snackbarMessage = "Couldn't claim that one — try again in a moment.")
                }
            }
        }
    }

    private fun rewardCopy(coins: Int, treats: Int): String = when {
        coins > 0 && treats > 0 -> "+$coins 🪙 +$treats 🍬"
        coins > 0 -> "+$coins 🪙"
        treats > 0 -> "+$treats 🍬"
        else -> "nice work!"
    }
}

class FarmViewModelFactory(
    private val gameRepository: GameRepository,
    private val healthConnectManager: HealthConnectManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FarmViewModel(gameRepository, healthConnectManager) as T
    }
}
