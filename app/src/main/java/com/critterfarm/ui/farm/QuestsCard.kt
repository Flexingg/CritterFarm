package com.critterfarm.ui.farm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.Quest
import com.critterfarm.data.QuestRules
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.SproutGreen

/** One quest row's state, shaped for the card. Pure, so it can be built and asserted on directly. */
data class QuestCardItem(
    val quest: Quest,
    val progress: Float,
    val complete: Boolean,
    val claimed: Boolean,
)

/** Builds the three quest rows from today's log and today's claims. Pure — no Android needed. */
fun buildQuestCardItems(
    quests: List<Quest>,
    log: DailySummaryLogEntity?,
    claimedQuestIds: Set<String>,
): List<QuestCardItem> = quests.map { quest ->
    QuestCardItem(
        quest = quest,
        progress = QuestRules.progress(quest, log),
        complete = QuestRules.isComplete(quest, log),
        claimed = quest.id in claimedQuestIds,
    )
}

/** Reward text for a quest, e.g. "+20 🪙" or "+5 🍬". */
private fun Quest.rewardText(): String = when {
    rewardCoins > 0 && rewardTreats > 0 -> "+$rewardCoins 🪙 +$rewardTreats 🍬"
    rewardTreats > 0 -> "+$rewardTreats 🍬"
    else -> "+$rewardCoins 🪙"
}

/**
 * Today's three quests: progress bars, reward icons and a Claim button once complete. Sits
 * directly under the streak card — short-term goals right next to the medium-term chain.
 */
@Composable
fun QuestsCard(
    items: List<QuestCardItem>,
    onClaim: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Today's quests", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Three fresh quests every day, picked from today's date — come back tomorrow " +
                        "for three more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items.forEach { item -> QuestRow(item = item, onClaim = { onClaim(item.quest.id) }) }
        }
    }
}

@Composable
private fun QuestRow(item: QuestCardItem, onClaim: () -> Unit) {
    val quest = item.quest
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(quest.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    quest.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    quest.rewardText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoinGold,
                )
            }
            when {
                item.claimed -> Text(
                    "✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = SproutGreen,
                    fontWeight = FontWeight.Bold,
                )
                item.complete -> Button(onClick = onClaim) { Text("Claim") }
                else -> Text(
                    "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (item.claimed || item.complete) SproutGreen else MaterialTheme.colorScheme.primary,
        )
    }
}
