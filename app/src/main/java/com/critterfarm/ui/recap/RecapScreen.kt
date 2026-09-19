package com.critterfarm.ui.recap

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.WeekStats
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The week in review: what actually happened over the last seven days, with the week before it for
 * context. Facts, not a verdict — and if it was a thin week the screen says so kindly, because the
 * fastest way to lose someone is to make them feel judged for resting.
 */
@Composable
fun RecapScreen(uiState: RecapUiState, onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold { padding ->
        if (uiState.isLoading || uiState.week == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val week = uiState.week
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Your week on the farm", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${shortDate(week)} · ${week.daysLogged} of 7 days logged",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onBack) { Text("← Farm") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    uiState.highlights.forEach { line ->
                        Text("✨ $line", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Against last week", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    uiState.deltas.forEachIndexed { index, delta ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(delta.label, style = MaterialTheme.typography.bodyMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                val arrow = when {
                                    delta.same -> "·"
                                    delta.up -> "▲"
                                    else -> "▼"
                                }
                                Text(
                                    text = "$arrow ${delta.thisWeek}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "last week ${delta.vsLastWeek}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index != uiState.deltas.lastIndex) HorizontalDivider()
                    }
                }
            }

            Text("The numbers", style = MaterialTheme.typography.titleMedium)
            Totals(week)

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, uiState.shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Share your week"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Share my week")
            }
            Text(
                text = "Shares a short text summary only — no images, no weight, no personal data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Totals(week: WeekStats) {
    val rows = listOf(
        "Steps" to String.format(Locale.US, "%,d", week.totalSteps),
        "Workouts" to week.totalWorkouts.toString(),
        "Water" to "${week.totalWaterFlOz.toInt()} fl oz",
        "Sleep" to "${week.totalSleepMinutes / 60}h ${week.totalSleepMinutes % 60}m",
        "Days with 4+ targets hit" to week.metTargetDays.toString(),
        "Best day" to "${week.bestDayScore} of 6",
        "Coins earned" to week.coinsEarned.toString(),
        "Treats earned" to week.treatsEarned.toString(),
        "Mana Sparks earned" to week.sparksEarned.toString(),
    )
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                if (index != rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun shortDate(week: WeekStats): String =
    "${week.startDate.format(SHORT_DATE)} – ${week.endDate.format(SHORT_DATE)}"
