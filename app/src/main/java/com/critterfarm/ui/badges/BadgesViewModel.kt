package com.critterfarm.ui.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.BadgeCatalog
import com.critterfarm.data.BadgeProgress
import com.critterfarm.data.BadgeRules
import com.critterfarm.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BadgesUiState(
    val isLoading: Boolean = true,
    val unlockedCount: Int = 0,
    val totalCount: Int = BadgeCatalog.ALL.size,
    /** Unlocked badges first (hardest tier first, as the "just earned" spotlight), then locked. */
    val badges: List<BadgeProgress> = emptyList(),
)

/**
 * Owns the Badges screen's state. Every badge is recomputed from [GameRepository.dailyLogs] on
 * every emission — badges have no storage of their own, so there is nothing here that can drift
 * from the history the rest of the app already shows.
 */
class BadgesViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(BadgesUiState())
    val uiState: StateFlow<BadgesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.dailyLogs.collect { logs ->
                val progress = BadgeCatalog.ALL.map { BadgeRules.progressFor(logs, it) }
                val (unlocked, locked) = progress.partition { it.unlocked }
                val orderedUnlocked = unlocked.sortedByDescending { it.badge.tier.ordinal }
                val orderedLocked = locked.sortedByDescending { it.current.toDouble() / it.target }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        unlockedCount = unlocked.size,
                        totalCount = progress.size,
                        badges = orderedUnlocked + orderedLocked,
                    )
                }
            }
        }
    }
}

class BadgesViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BadgesViewModel(gameRepository) as T
}
