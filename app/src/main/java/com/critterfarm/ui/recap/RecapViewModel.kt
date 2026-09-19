package com.critterfarm.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.GameRepository
import com.critterfarm.data.RecapDelta
import com.critterfarm.data.WeekStats
import com.critterfarm.data.WeeklyRecap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecapUiState(
    val isLoading: Boolean = true,
    val week: WeekStats? = null,
    val previous: WeekStats? = null,
    val deltas: List<RecapDelta> = emptyList(),
    val highlights: List<String> = emptyList(),
    val shareText: String = "",
)

/**
 * Reads the last seven days (and the seven before that) out of the stored logs. Everything is
 * derived on each emission, so the recap can never disagree with the numbers on the farm screen.
 */
class RecapViewModel(
    private val gameRepository: GameRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecapUiState())
    val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.dailyLogs.collect { logs ->
                val thisWeekStart = WeeklyRecap.weekStart(today)
                val week = WeeklyRecap.statsFor(logs, thisWeekStart)
                val previous = WeeklyRecap.statsFor(logs, thisWeekStart.minusDays(7))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        week = week,
                        previous = previous,
                        deltas = WeeklyRecap.deltas(week, previous),
                        highlights = WeeklyRecap.highlights(week),
                        shareText = WeeklyRecap.shareText(week),
                    )
                }
            }
        }
    }
}

class RecapViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecapViewModel(gameRepository) as T
}
