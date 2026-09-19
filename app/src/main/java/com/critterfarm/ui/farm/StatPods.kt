package com.critterfarm.ui.farm

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.EmberOrange
import com.critterfarm.ui.theme.EmberRed
import com.critterfarm.ui.theme.PondBlueDeep
import com.critterfarm.ui.theme.SkyBlue
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Circular progress ring for today's steps against [goal]. */
@Composable
fun StepProgressRing(steps: Long, goal: Long = 10_000L, modifier: Modifier = Modifier) {
    val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "stepsProgress",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(96.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = DormantGraySurface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = SkyBlue,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$steps", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(text = "steps", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A flame that grows and shifts from ember to gold as the day's calorie deficit approaches
 * the safe ceiling ([goalKcal]). It never claims credit for more than that ceiling — matching
 * the guardrail that deficit rewards flat-line past a medically sane band.
 */
@Composable
fun CalorieFlame(deficitKcal: Double, goalKcal: Double = 500.0, modifier: Modifier = Modifier) {
    val ratio = (deficitKcal / goalKcal).toFloat().coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "flameRatio",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "flameFlicker")
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flicker",
    )
    val flameColor = if (ratio >= 1f) CoinGold else EmberOrange

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(96.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val flameHeight = size.height * (0.25f + 0.65f * animatedRatio) * (0.94f + 0.06f * flicker)
            val flameWidth = size.width * 0.5f * (0.9f + 0.1f * flicker)
            val baseY = size.height * 0.92f
            val path = Path().apply {
                moveTo(size.width / 2f, baseY - flameHeight)
                cubicTo(
                    size.width / 2f - flameWidth / 2f, baseY - flameHeight * 0.55f,
                    size.width / 2f - flameWidth * 0.35f, baseY - flameHeight * 0.1f,
                    size.width / 2f, baseY,
                )
                cubicTo(
                    size.width / 2f + flameWidth * 0.35f, baseY - flameHeight * 0.1f,
                    size.width / 2f + flameWidth / 2f, baseY - flameHeight * 0.55f,
                    size.width / 2f, baseY - flameHeight,
                )
                close()
            }
            drawPath(path = path, color = flameColor)
            drawPath(path = path, color = EmberRed.copy(alpha = 0.25f), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** A pond that fills toward [goalMl] of daily hydration, with a gently animated water line. */
@Composable
fun PondWaterBar(hydrationMl: Double, goalMl: Double = 2000.0, modifier: Modifier = Modifier) {
    val ratio = (hydrationMl / goalMl).toFloat().coerceIn(0f, 1f)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pondRatio",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pondWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
        ),
        label = "wavePhase",
    )

    Box(modifier = modifier.size(width = 120.dp, height = 64.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pondShape = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(Offset.Zero, size),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    ),
                )
            }
            drawPath(path = pondShape, color = DormantGraySurface)

            clipPath(pondShape) {
                val waterLevel = size.height * (1f - animatedRatio)
                val amplitude = min(size.height * 0.06f, 6.dp.toPx())
                val waterPath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, waterLevel)
                    var x = 0f
                    while (x <= size.width) {
                        val y = waterLevel + sin((x / size.width) * 2f * PI.toFloat() + wavePhase) * amplitude
                        lineTo(x, y)
                        x += size.width / 24f
                    }
                    lineTo(size.width, waterLevel)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path = waterPath, color = SkyBlue)
                drawPath(path = waterPath, color = PondBlueDeep.copy(alpha = 0.3f), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}
