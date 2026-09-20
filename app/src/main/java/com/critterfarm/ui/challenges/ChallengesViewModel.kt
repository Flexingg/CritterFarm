package com.critterfarm.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.ChallengeCatalog
import com.critterfarm.data.ChallengeClaimResult
import com.critterfarm.data.ChallengeProgress
import com.critterfarm.data.ChallengeRules
import com.critterfarm.data.ChallengeWindow
import com.critterfarm.data.EventCatalog
import com.critterfarm.data.FarmEvent
import com.critterfarm.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed class ChallengesIntent {
    data class Claim(val challengeId: String) : ChallengesIntent()

    data object DismissMessage : ChallengesIntent()
}

/** One challenge card's worth of state: the maths from [ChallengeRules] plus whether it is claimed. */
data class ChallengeCardState(
    val progress: ChallengeProgress,
    val claimed: Boolean,
)

data class ChallengesUiState(
    val isLoading: Boolean = true,
    val activeEvent: FarmEvent? = null,
    val eventDaysLeft: Int = 0,
    val eventChallenge: ChallengeCardState? = null,
    val weekly: List<ChallengeCardState> = emptyList(),
    val monthly: List<ChallengeCardState> = emptyList(),
    val message: String? = null,
)

/**
 * Owns the Challenges screen's state. Every card is recomputed from [GameRepository.dailyLogs] and
 * [GameRepository.observeChallengeClaims] on every emission — challenges have no cached progress of
 * their own, exactly like [com.critterfarm.ui.badges.BadgesViewModel], so a card can never show a
 * number the day's actual logs disagree with.
 */
class ChallengesViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengesUiState())
    val uiState: StateFlow<ChallengesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                gameRepository.dailyLogs,
                gameRepository.observeChallengeClaims(),
            ) { logs, claims -> logs to claims }.collect { (logs, claims) ->
                val today = LocalDate.now()
                val activeEvent = EventCatalog.activeOn(today)

                fun cardFor(id: String) = ChallengeCatalog.byId(id)?.let { spec ->
                    val progress = ChallengeRules.progressFor(spec, logs, today)
                    val claimed = claims.any {
                        it.periodKey == progress.periodKey && it.challengeId == spec.id
                    }
                    ChallengeCardState(progress, claimed)
                }

                val weekly = ChallengeCatalog.ALL
                    .filter { it.window == ChallengeWindow.WEEKLY }
                    .mapNotNull { cardFor(it.id) }
                val monthly = ChallengeCatalog.ALL
                    .filter { it.window == ChallengeWindow.MONTHLY }
                    .mapNotNull { cardFor(it.id) }
                val eventChallenge = activeEvent?.let { event ->
                    ChallengeCatalog.ALL
                        .firstOrNull { it.window == ChallengeWindow.EVENT && it.eventId == event.id }
                        ?.let { cardFor(it.id) }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        activeEvent = activeEvent,
                        eventDaysLeft = activeEvent
                            ?.let { event -> ChronoUnit.DAYS.between(today, event.end).toInt().coerceAtLeast(0) }
                            ?: 0,
                        eventChallenge = eventChallenge,
                        weekly = weekly,
                        monthly = monthly,
                    )
                }
            }
        }
    }

    fun onIntent(intent: ChallengesIntent) {
        when (intent) {
            is ChallengesIntent.Claim -> claim(intent.challengeId)
            ChallengesIntent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun claim(challengeId: String) {
        viewModelScope.launch {
            val spec = ChallengeCatalog.byId(challengeId) ?: return@launch
            when (val result = gameRepository.claimChallenge(spec, LocalDate.now())) {
                is ChallengeClaimResult.Claimed -> _uiState.update {
                    it.copy(message = "${spec.title} complete! ${rewardCopy(result.coins, result.treats, result.sparks)}")
                }
                ChallengeClaimResult.AlreadyClaimed -> _uiState.update {
                    it.copy(message = "Already claimed — that one's done for this period.")
                }
                is ChallengeClaimResult.NotComplete -> _uiState.update {
                    it.copy(message = "Not quite there yet — keep going!")
                }
                ChallengeClaimResult.UnknownChallenge -> _uiState.update {
                    it.copy(message = "Couldn't claim that one — try again in a moment.")
                }
            }
        }
    }

    private fun rewardCopy(coins: Int, treats: Int, sparks: Int): String {
        val parts = buildList {
            if (coins > 0) add("+$coins 🪙")
            if (treats > 0) add("+$treats 🍬")
            if (sparks > 0) add("+$sparks 🔮")
        }
        return if (parts.isEmpty()) "nice work!" else parts.joinToString(" ")
    }
}

class ChallengesViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChallengesViewModel(gameRepository) as T
}
