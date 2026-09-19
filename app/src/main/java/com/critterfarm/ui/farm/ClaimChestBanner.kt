package com.critterfarm.ui.farm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.InkBrown
import com.critterfarm.ui.theme.SunshineYellow

/**
 * The home-screen interactive chest. Unclaimed, it gently bobs to invite a tap; claimed, it
 * settles into a quiet "come back tomorrow" state.
 */
@Composable
fun ClaimChestBanner(
    isClaimed: Boolean,
    isClaiming: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chestBob")
    val bob by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isClaimed) 1f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bobScale",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isClaiming) 0.92f else bob,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pressScale",
    )

    Surface(
        onClick = onClaim,
        enabled = !isClaiming,
        shape = RoundedCornerShape(28.dp),
        color = if (isClaimed) DormantGraySurface else SunshineYellow,
        modifier = modifier.scale(pressScale),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (isClaiming) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp), color = InkBrown)
            } else {
                Text(
                    text = if (isClaimed) "✨" else "🎁",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = if (isClaimed) "Chest claimed" else "Claim Daily Turn",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkBrown,
                )
                Text(
                    text = if (isClaimed) "Come back tomorrow for more" else "Tap to collect today's spoils",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkBrown,
                )
            }
        }
    }
}
