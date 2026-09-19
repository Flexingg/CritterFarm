package com.critterfarm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.DayCell
import com.critterfarm.data.GameRepository
import com.critterfarm.data.HistoryRules
import com.critterfarm.data.Metric
import com.critterfarm.data.MetricHistory
import com.critterfarm.data.MetricSummary
import com.critterfarm.data.PersonalRecord
import com.critterfarm.data.local.DailySummaryLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoryUiState(
    val isLoading: Boolean = true,
    val daysLogged: Int = 0,
    val weeks: List<List<DayCell?>> = emptyList(),
    val records: List<PersonalRecord> = emptyList(),
    /** Set when the user tapped a stat row on the farm and wants that metric's detail. */
    val focus: Metric? = null,
    val focusSeries: List<Pair<LocalDate, Double?>> = emptyList(),
    val focusSummary: MetricSummary? = null,
) {
    val hasHistory: Boolean get() = daysLogged > 0
}

/**
 * Owns the history view. Reads the daily logs the app has been storing since v1.0 — no new
 * Health Connect permissions, no new data, just the days that were already written down.
 */
class HistoryViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var logs: List<DailySummaryLogEntity> = emptyList()
    private var focus: Metric? = null

    init {
        viewModelScope.launch {
            gameRepository.dailyLogs.collect { latest ->
                logs = latest
                recompute()
            }
        }
    }

    /** Called with the metric behind a tapped stat row, or null for the plain history view. */
    fun focusOn(metric: Metric?) {
        focus = metric
        recompute()
    }

    private fun recompute() {
        val metric = focus
        _uiState.update {
            it.copy(
                isLoading = false,
                daysLogged = logs.size,
                weeks = HistoryRules.toWeeks(HistoryRules.cells(logs)),
                records = HistoryRules.records(logs),
                focus = metric,
                focusSeries = metric?.let { m -> MetricHistory.series(logs, m) } ?: emptyList(),
                focusSummary = metric?.let { m -> MetricHistory.summary(logs, m) },
            )
        }
    }
}

class HistoryViewModelFactory(private val gameRepository: GameRepository) :
    ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(gameRepository) as T
}
