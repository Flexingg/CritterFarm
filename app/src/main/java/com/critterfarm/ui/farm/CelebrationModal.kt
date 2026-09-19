package com.critterfarm.ui.farm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.critterfarm.data.ClaimResult
import com.critterfarm.ui.theme.CoinGold

/**
 * The dopamine moment: today's spoils tallying up with an arcade count-up, shown after
 * "Claim Daily Turn" succeeds.
 */
@Composable
fun CelebrationModal(result: ClaimResult.Claimed, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Nice!")
            }
        },
        title = {
            Text(
                text = "Daily Turn Claimed! 🎉",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Sprout tallied up the day's adventures:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                StreakBanner(
                    streakDays = result.streakDays,
                    multiplier = result.multiplier,
                    freezeUsed = result.freezeUsed,
                )
                SpoilRow(emoji = "✨", label = "XP", target = result.xpEarned)
                SpoilRow(emoji = "🪙", label = "Coins", target = result.coinsEarned)
                SpoilRow(emoji = "🍬", label = "Treats", target = result.treatsEarned)
                SpoilRow(emoji = "🔮", label = "Mana Sparks", target = result.manaSparksEarned)
                result.supportiveMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

/**
 * The chain, front and centre. The streak is what compounds, so the modal says out loud what
 * it just paid — a 7-day run turning ×2 on every single reward.
 */
@Composable
private fun StreakBanner(streakDays: Int, multiplier: Double, freezeUsed: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CoinGold.copy(alpha = 0.22f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (streakDays > 1) "🔥 $streakDays-day streak" else "🔥 Streak started",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (multiplier > 1.0) {
                Text(
                    text = "Everything above already paid ×${trimTrailingZero(multiplier)}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "Reach 3 days and every claim pays ×1.5",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            if (freezeUsed) {
                Text(
                    text = "🧊 A Streak Freeze kept the chain alive",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun trimTrailingZero(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun SpoilRow(emoji: String, label: String, target: Int) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "$emoji $label", style = MaterialTheme.typography.bodyLarge)
        AnimatedCounter(target = target, style = MaterialTheme.typography.titleLarge)
    }
}
