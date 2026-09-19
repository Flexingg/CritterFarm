package com.critterfarm.ui.farm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.critterfarm.ui.theme.BerryPink
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SlumberLavender
import com.critterfarm.ui.theme.SproutGreen
import com.critterfarm.ui.theme.SunshineYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One particle of the burst, worked out once and reused. Deliberately a plain data class built
 * outside the draw loop: nothing in `drawCircle` should be allocating when this runs 22 times a
 * frame for a second.
 */
private data class ConfettiParticle(
    val angle: Float,
    val radius: Float,
    val colorIndex: Int,
)

private fun buildParticles(): List<ConfettiParticle> = List(PARTICLE_COUNT) { index ->
    ConfettiParticle(
        // Spread evenly around the circle, nudged so the ring does not look stamped out.
        angle = index * (2f * PI.toFloat() / PARTICLE_COUNT) + (index % 3) * 0.13f,
        radius = 5f + (index % 4) * 2.2f,
        colorIndex = index % PARTICLE_COLORS.size,
    )
}

private const val PARTICLE_COUNT = 22
private const val BURST_MILLIS = 950

private val PARTICLE_COLORS = listOf(
    SproutGreen,
    SunshineYellow,
    CoinGold,
    SkyBlue,
    BerryPink,
    SlumberLavender,
)

/**
 * A cheap confetti burst. Re-fires whenever [trigger] changes (pass a counter or a non-null id),
 * draws nothing at all when idle, and never blocks the dialog it celebrates — the celebration is
 * the modal, this is just the shout.
 */
@Composable
fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = BURST_MILLIS))
    }

    if (progress.value >= 1f) return

    val particles = remember { buildParticles() }

    Canvas(modifier = modifier) {
        val t = progress.value
        val reach = size.minDimension * 0.62f
        // Ease out: fast at the start, drifting at the end.
        val eased = 1f - (1f - t) * (1f - t)
        particles.forEach { particle ->
            val distance = eased * reach
            val dx = center.x + cos(particle.angle) * distance
            // A touch of gravity so the ring settles downward instead of hanging in mid-air.
            val dy = center.y + sin(particle.angle) * distance * 0.85f + t * t * size.height * 0.16f
            val radius = (1f - t) * particle.radius
            if (radius > 0.4f) {
                drawCircle(
                    color = PARTICLE_COLORS[particle.colorIndex].copy(alpha = 1f - t * 0.65f),
                    radius = radius,
                    center = Offset(dx, dy),
                )
            }
        }
    }
}
