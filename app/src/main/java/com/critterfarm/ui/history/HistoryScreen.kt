package com.critterfarm.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.DayCell
import com.critterfarm.data.GameGoals
import com.critterfarm.data.Metric
import com.critterfarm.data.PersonalRecord
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.SproutGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("MMM d")

/**
 * The long view: a GitHub-style consistency heatmap, per-metric detail, and records.
 *
 * Everything here is built from the daily logs the app already stores, so it works offline, with
 * zero Health Connect permissions, and cannot leak anything.
 */
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onBack: () -> Unit,
    onClearFocus: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Farm history", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("← Farm") }
            }
        }

        if (!uiState.hasHistory) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No history yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Claim your first Daily Turn and this page starts filling in. " +
                                "Every day you log becomes a square.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Text(
                "${uiState.daysLogged} days logged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.focus?.let { metric ->
            item { MetricDetailCard(metric, uiState, onClearFocus) }
        }

        item { HeatmapCard(uiState.weeks) }

        if (uiState.records.isNotEmpty()) {
            item { RecordsCard(uiState.records) }
        }
    }
}

@Composable
private fun MetricDetailCard(metric: Metric, uiState: HistoryUiState, onClearFocus: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(metric.label, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClearFocus) { Text("Show all") }
            }

            val summary = uiState.focusSummary
            if (summary == null || !summary.hasData) {
                Text(
                    "Nothing recorded for this yet — keep logging and the graph fills in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            Text(
                "Best: ${metric.format(summary.best)}" +
                    (summary.bestDate?.let { " on ${it.format(DAY_FORMAT)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Average: ${metric.format(summary.average)} · ${summary.daysWithData} days recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            BarChart(uiState.focusSeries)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Last ${uiState.focusSeries.size} days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BarChart(series: List<Pair<LocalDate, Double?>>) {
    val max = series.mapNotNull { it.second }.maxOrNull() ?: 0.0
    Row(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        series.forEach { (_, value) ->
            val fraction = if (max <= 0.0 || value == null) 0f else (value / max).toFloat()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((80f * fraction).coerceAtLeast(if (value == null) 3f else 6f).dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (value == null) DormantGraySurface else SproutGreen),
            )
        }
    }
}

@Composable
private fun HeatmapCard(weeks: List<List<DayCell?>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Consistency", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Each square is a day. Greener = more of your ${
                    GameGoals.TARGET_COUNT
                } daily targets hit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        week.forEach { cell -> HeatmapSquare(cell) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "none",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                (0..4).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(intensityColor(level)),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    "all ${GameGoals.TARGET_COUNT}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeatmapSquare(cell: DayCell?) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    cell == null -> Color.Transparent
                    cell.isFuture -> DormantGraySurface.copy(alpha = 0.4f)
                    else -> intensityColor(cell.intensity)
                },
            ),
    )
}

private fun intensityColor(level: Int): Color = when (level) {
    1 -> SproutGreen.copy(alpha = 0.30f)
    2 -> SproutGreen.copy(alpha = 0.55f)
    3 -> SproutGreen.copy(alpha = 0.78f)
    4 -> SproutGreen
    else -> DormantGraySurface
}

@Composable
private fun RecordsCard(records: List<PersonalRecord>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Records", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Your best days so far. Weight is a milestone, never a race.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            records.forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(record.emoji, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            record.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = DormantGray,
                        )
                    }
                    Text(
                        record.value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CoinGold,
                    )
                }
            }
        }
    }
}
