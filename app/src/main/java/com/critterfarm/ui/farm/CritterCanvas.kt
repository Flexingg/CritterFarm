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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.critterfarm.data.local.CritterMood
import com.critterfarm.ui.theme.BerryPink
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.InkBrown
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SproutGreen
import com.critterfarm.ui.theme.SunshineYellow
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sprout, drawn entirely with [Canvas] so its expression can react to [mood] in real time —
 * no sprite sheet needed. The continuous idle motion runs on an [rememberInfiniteTransition];
 * the "pop" whenever [mood] changes runs on a spring via [animateFloatAsState], per spec.
 */
@Composable
fun CritterCanvas(mood: CritterMood, hatId: String? = null, modifier: Modifier = Modifier) {
    val cyclePeriodMs = when (mood) {
        CritterMood.CELEBRATING -> 650
        CritterMood.BOUNCING_HAPPY -> 1100
        CritterMood.SLUGGISH_TIRED -> 2600
        CritterMood.NEUTRAL -> 1900
    }
    val bounceAmplitude = when (mood) {
        CritterMood.CELEBRATING -> 26f
        CritterMood.BOUNCING_HAPPY -> 16f
        CritterMood.SLUGGISH_TIRED -> 3f
        CritterMood.NEUTRAL -> 7f
    }
    val bodyColor = when (mood) {
        CritterMood.CELEBRATING -> BerryPink
        CritterMood.BOUNCING_HAPPY -> SproutGreen
        CritterMood.SLUGGISH_TIRED -> DormantGray
        CritterMood.NEUTRAL -> SproutGreen
    }

    val infiniteTransition = rememberInfiniteTransition(label = "critterIdle")
    val cycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cyclePeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )

    // A spring "pop" every time the mood changes — the explicitly-requested
    // animateFloatAsState + spring physics on top of the continuous idle motion above.
    var pulseTarget by remember { mutableFloatStateOf(1f) }
    val scale by animateFloatAsState(
        targetValue = pulseTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "moodPop",
    )
    LaunchedEffect(mood) {
        pulseTarget = 1.18f
        delay(160)
        pulseTarget = 1f
    }

    Canvas(modifier = modifier.size(180.dp)) {
        val bounceOffset = -abs(sin(cycle * 2f * PI.toFloat())) * bounceAmplitude
        // The body deliberately sits LOW in the canvas: hats are drawn above the head, and a
        // centred body left only ~11% headroom, so every hat was clipped off the top edge and
        // looked like it was never drawn at all.
        val bodyRadius = size.minDimension / 3.0f
        val center = Offset(size.width / 2f, size.height * 0.63f + bounceOffset)

        scale(scale, pivot = center) {
            drawCircle(color = bodyColor, radius = bodyRadius, center = center)

            // Cheeks
            val cheekOffsetX = bodyRadius * 0.6f
            val cheekOffsetY = bodyRadius * 0.25f
            drawCircle(
                color = BerryPink.copy(alpha = 0.35f),
                radius = bodyRadius * 0.18f,
                center = center + Offset(-cheekOffsetX, cheekOffsetY),
            )
            drawCircle(
                color = BerryPink.copy(alpha = 0.35f),
                radius = bodyRadius * 0.18f,
                center = center + Offset(cheekOffsetX, cheekOffsetY),
            )

            drawEyes(mood, center, bodyRadius, cycle)
            drawMouth(mood, center, bodyRadius)
            // Whatever you bought, Sprout actually wears it.
            hatId?.let { drawHat(it, center, bodyRadius) }
        }

        if (mood == CritterMood.CELEBRATING) {
            drawSparkles(center, bodyRadius, cycle)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    mood: CritterMood,
    center: Offset,
    bodyRadius: Float,
    cycle: Float,
) {
    val eyeOffsetX = bodyRadius * 0.42f
    val eyeOffsetY = -bodyRadius * 0.1f
    val leftEye = center + Offset(-eyeOffsetX, eyeOffsetY)
    val rightEye = center + Offset(eyeOffsetX, eyeOffsetY)
    val eyeRadius = bodyRadius * 0.16f

    if (mood == CritterMood.SLUGGISH_TIRED) {
        // Half-closed, drowsy eyes: a flat arc instead of a full circle.
        val strokeWidth = eyeRadius * 0.6f
        drawArc(
            color = InkBrown,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = leftEye - Offset(eyeRadius, eyeRadius),
            size = androidx.compose.ui.geometry.Size(eyeRadius * 2, eyeRadius * 2),
            style = Stroke(width = strokeWidth),
        )
        drawArc(
            color = InkBrown,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = rightEye - Offset(eyeRadius, eyeRadius),
            size = androidx.compose.ui.geometry.Size(eyeRadius * 2, eyeRadius * 2),
            style = Stroke(width = strokeWidth),
        )
        return
    }

    // A quick blink every cycle keeps even the idle states feeling alive.
    val blinking = cycle > 0.94f
    if (blinking) {
        drawLine(InkBrown, leftEye - Offset(eyeRadius, 0f), leftEye + Offset(eyeRadius, 0f), strokeWidth = eyeRadius * 0.5f)
        drawLine(InkBrown, rightEye - Offset(eyeRadius, 0f), rightEye + Offset(eyeRadius, 0f), strokeWidth = eyeRadius * 0.5f)
        return
    }

    drawCircle(color = InkBrown, radius = eyeRadius, center = leftEye)
    drawCircle(color = InkBrown, radius = eyeRadius, center = rightEye)
    // Sparkle highlight
    drawCircle(color = Color.White, radius = eyeRadius * 0.3f, center = leftEye + Offset(-eyeRadius * 0.3f, -eyeRadius * 0.3f))
    drawCircle(color = Color.White, radius = eyeRadius * 0.3f, center = rightEye + Offset(-eyeRadius * 0.3f, -eyeRadius * 0.3f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMouth(
    mood: CritterMood,
    center: Offset,
    bodyRadius: Float,
) {
    val mouthCenter = center + Offset(0f, bodyRadius * 0.32f)
    val mouthWidth = bodyRadius * 0.5f
    val strokeWidth = bodyRadius * 0.09f
    when (mood) {
        CritterMood.SLUGGISH_TIRED -> {
            // A flat, tired line.
            drawLine(
                color = InkBrown,
                start = mouthCenter - Offset(mouthWidth * 0.5f, 0f),
                end = mouthCenter + Offset(mouthWidth * 0.5f, 0f),
                strokeWidth = strokeWidth,
            )
        }
        CritterMood.CELEBRATING -> {
            // A big open cheer.
            drawArc(
                color = InkBrown,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = mouthCenter - Offset(mouthWidth * 0.5f, mouthWidth * 0.2f),
                size = androidx.compose.ui.geometry.Size(mouthWidth, mouthWidth * 0.7f),
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        else -> {
            // A gentle smile.
            drawArc(
                color = InkBrown,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = mouthCenter - Offset(mouthWidth * 0.5f, mouthWidth * 0.15f),
                size = androidx.compose.ui.geometry.Size(mouthWidth, mouthWidth * 0.5f),
                style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSparkles(
    center: Offset,
    bodyRadius: Float,
    cycle: Float,
) {
    val sparkleCount = 5
    repeat(sparkleCount) { index ->
        val angle = (2 * PI.toFloat() / sparkleCount) * index + cycle * 2f * PI.toFloat()
        val distance = bodyRadius * (1.5f + 0.25f * sin(cycle * 2f * PI.toFloat() + index))
        val position = center + Offset(cos(angle) * distance, sin(angle) * distance)
        val sparkleSize = bodyRadius * 0.12f
        rotate(degrees = angle * 180f / PI.toFloat(), pivot = position) {
            drawLine(SunshineYellow, position - Offset(sparkleSize, 0f), position + Offset(sparkleSize, 0f), strokeWidth = sparkleSize * 0.35f)
            drawLine(SunshineYellow, position - Offset(0f, sparkleSize), position + Offset(0f, sparkleSize), strokeWidth = sparkleSize * 0.35f)
        }
    }
}

/**
 * The payoff for spending coins: whatever hat is equipped, Sprout actually wears it.
 *
 * Every hat is plain vector work (ovals, paths, rects) rather than assets, so a new cosmetic is
 * a few lines of drawing code and needs no designer. [hatId] values match [ShopCatalog] ids.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHat(
    hatId: String,
    center: Offset,
    bodyRadius: Float,
) {
    val headTop = center.y - bodyRadius
    val hatWidth = bodyRadius * 1.5f
    val straw = Color(0xFFE8C36A)
    val strawBand = Color(0xFFB98A3A)
    val leather = Color(0xFF6B4423)

    when (hatId) {
        "hat_straw" -> {
            drawOval(
                color = straw,
                topLeft = Offset(center.x - hatWidth * 0.8f, headTop - bodyRadius * 0.10f),
                size = Size(hatWidth * 1.6f, bodyRadius * 0.45f),
            )
            drawArc(
                color = straw,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - hatWidth * 0.34f, headTop - bodyRadius * 0.62f),
                size = Size(hatWidth * 0.68f, bodyRadius * 0.72f),
            )
            drawRect(
                color = strawBand,
                topLeft = Offset(center.x - hatWidth * 0.34f, headTop - bodyRadius * 0.22f),
                size = Size(hatWidth * 0.68f, bodyRadius * 0.10f),
            )
        }

        "hat_party" -> {
            val cone = Path().apply {
                moveTo(center.x, headTop - bodyRadius * 0.82f)
                lineTo(center.x - bodyRadius * 0.34f, headTop - bodyRadius * 0.02f)
                lineTo(center.x + bodyRadius * 0.34f, headTop - bodyRadius * 0.02f)
                close()
            }
            drawPath(cone, BerryPink)
            drawLine(
                color = SunshineYellow,
                start = Offset(center.x - bodyRadius * 0.18f, headTop - bodyRadius * 0.40f),
                end = Offset(center.x + bodyRadius * 0.18f, headTop - bodyRadius * 0.40f),
                strokeWidth = bodyRadius * 0.07f,
            )
            drawCircle(
                color = SunshineYellow,
                radius = bodyRadius * 0.11f,
                center = Offset(center.x, headTop - bodyRadius * 0.85f),
            )
        }

        "hat_beanie" -> {
            drawArc(
                color = SkyBlue,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(center.x - hatWidth * 0.42f, headTop - bodyRadius * 0.50f),
                size = Size(hatWidth * 0.84f, bodyRadius * 0.60f),
            )
            drawRect(
                color = SkyBlue,
                topLeft = Offset(center.x - hatWidth * 0.42f, headTop - bodyRadius * 0.14f),
                size = Size(hatWidth * 0.84f, bodyRadius * 0.22f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = bodyRadius * 0.08f,
                center = Offset(center.x, headTop - bodyRadius * 0.52f),
            )
        }

        "hat_cowboy" -> {
            drawOval(
                color = leather,
                topLeft = Offset(center.x - hatWidth * 0.95f, headTop - bodyRadius * 0.06f),
                size = Size(hatWidth * 1.9f, bodyRadius * 0.40f),
            )
            val crown = Path().apply {
                moveTo(center.x - hatWidth * 0.34f, headTop + bodyRadius * 0.06f)
                lineTo(center.x - hatWidth * 0.28f, headTop - bodyRadius * 0.50f)
                lineTo(center.x, headTop - bodyRadius * 0.68f)
                lineTo(center.x + hatWidth * 0.28f, headTop - bodyRadius * 0.50f)
                lineTo(center.x + hatWidth * 0.34f, headTop + bodyRadius * 0.06f)
                close()
            }
            drawPath(crown, leather)
            drawRect(
                color = strawBand,
                topLeft = Offset(center.x - hatWidth * 0.34f, headTop - bodyRadius * 0.14f),
                size = Size(hatWidth * 0.68f, bodyRadius * 0.10f),
            )
        }

        "hat_crown" -> {
            val crown = Path().apply {
                moveTo(center.x - bodyRadius * 0.50f, headTop - bodyRadius * 0.02f)
                lineTo(center.x - bodyRadius * 0.50f, headTop - bodyRadius * 0.52f)
                lineTo(center.x - bodyRadius * 0.24f, headTop - bodyRadius * 0.20f)
                lineTo(center.x, headTop - bodyRadius * 0.62f)
                lineTo(center.x + bodyRadius * 0.24f, headTop - bodyRadius * 0.20f)
                lineTo(center.x + bodyRadius * 0.50f, headTop - bodyRadius * 0.52f)
                lineTo(center.x + bodyRadius * 0.50f, headTop - bodyRadius * 0.02f)
                close()
            }
            drawPath(crown, CoinGold)
            drawCircle(
                color = BerryPink,
                radius = bodyRadius * 0.07f,
                center = Offset(center.x, headTop - bodyRadius * 0.30f),
            )
        }

        "hat_halo" -> {
            drawOval(
                color = CoinGold,
                topLeft = Offset(center.x - bodyRadius * 0.45f, headTop - bodyRadius * 0.72f),
                size = Size(bodyRadius * 0.90f, bodyRadius * 0.28f),
                style = Stroke(width = bodyRadius * 0.10f),
            )
            drawOval(
                color = SunshineYellow.copy(alpha = 0.45f),
                topLeft = Offset(center.x - bodyRadius * 0.56f, headTop - bodyRadius * 0.84f),
                size = Size(bodyRadius * 1.12f, bodyRadius * 0.40f),
                style = Stroke(width = bodyRadius * 0.05f),
            )
        }
    }
}
