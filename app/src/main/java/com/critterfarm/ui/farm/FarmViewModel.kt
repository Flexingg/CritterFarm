package com.critterfarm.ui.farm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.ClaimResult
import com.critterfarm.data.GameRepository
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.FarmInventoryEntity
import com.critterfarm.health.HealthConnectAvailability
import com.critterfarm.health.HealthConnectManager
import com.critterfarm.ui.model.GameZone
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
) {
    val dormantZones: List<GameZone>
        get() = GameZone.entries.filter { it.isDormant(grantedPermissions) }

    val hasAnyHealthConnection: Boolean
        get() = grantedPermissions.isNotEmpty()
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
        }
    }

    private fun observeLocalState() {
        viewModelScope.launch {
            combine(
                gameRepository.critter,
                gameRepository.inventory,
                gameRepository.observeDailyLog(LocalDate.now()),
            ) { critter, inventory, todayLog ->
                Triple(critter, inventory, todayLog)
            }.collect { (critter, inventory, todayLog) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        critter = critter,
                        inventory = inventory,
                        todayLog = todayLog,
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
