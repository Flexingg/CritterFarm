package com.critterfarm.ui.farm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

/**
 * Counts up from 0 to [target] using an [Animatable] — the arcade slot-machine tally the
 * celebration modal needs, rather than just snapping the label to its final value.
 */
@Composable
fun AnimatedCounter(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    durationMillis: Int = 900,
) {
    val animatable = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        )
    }
    Text(text = "${animatable.value.roundToInt()}", style = style, color = color, modifier = modifier)
}
