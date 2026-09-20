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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.critterfarm.data.Species
import com.critterfarm.data.SpeciesCatalog
import com.critterfarm.data.local.CritterMood
import com.critterfarm.ui.theme.BerryPink
import com.critterfarm.ui.theme.CoinGold
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.InkBrown
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SunshineYellow
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Stage 0/1/2/3 grow the whole canvas by this factor. Every drawing measurement below is derived
 * from `size` (the canvas's own pixel size), so scaling the canvas uniformly scales body, face
 * and headroom together — the hat-headroom ratio validated for stage 0 (see [drawHat]) survives
 * every stage and species unchanged. This is the "visibly bigger" half of the evolution payoff.
 * Stage 3 (Mythic) steps up clearly again rather than nudging, per the spec — a summit, not a tweak.
 */
private fun stageScale(stage: Int): Float = when {
    stage >= 3 -> 1.42f
    stage == 2 -> 1.26f
    stage == 1 -> 1.12f
    else -> 1f
}

/**
 * A critter, drawn entirely with [Canvas] so its expression can react to [mood] in real time —
 * no sprite sheet needed. The continuous idle motion runs on an [rememberInfiniteTransition];
 * the "pop" whenever [mood] changes runs on a spring via [animateFloatAsState], per spec.
 *
 * [speciesKey] picks the silhouette and palette ([SpeciesCatalog]); [stage] (0/1/2) picks the
 * size and the extra evolution flourish. An unrecognised key falls back to the starter blob
 * rather than drawing nothing.
 */
@Composable
fun CritterCanvas(
    mood: CritterMood,
    speciesKey: String,
    stage: Int,
    hatId: String? = null,
    modifier: Modifier = Modifier,
) {
    val species = SpeciesCatalog.bySpecies(speciesKey) ?: SpeciesCatalog.BLOB
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
    // Species colour is the base; mood still tints it (sleepy fades toward gray, celebrating
    // warms toward berry) so the face isn't the only thing telling you how Sprout feels.
    val bodyColor = when (mood) {
        CritterMood.SLUGGISH_TIRED -> lerp(species.primaryColor, DormantGray, 0.5f)
        CritterMood.CELEBRATING -> lerp(species.primaryColor, BerryPink, 0.25f)
        else -> species.primaryColor
    }
    val sleepyEyes = speciesKey == SpeciesCatalog.SLOTH.key || mood == CritterMood.SLUGGISH_TIRED

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

    Canvas(modifier = modifier.size(180.dp * stageScale(stage))) {
        val bounceOffset = -abs(sin(cycle * 2f * PI.toFloat())) * bounceAmplitude
        // The body deliberately sits LOW in the canvas: hats are drawn above the head, and a
        // centred body left only ~11% headroom, so every hat was clipped off the top edge and
        // looked like it was never drawn at all. Every measurement here is relative to `size`,
        // so growing the whole canvas for stage 1/2 (above) scales body, face and headroom
        // together — the tightest case (the halo hat) keeps the same safety margin at every
        // stage and species.
        val bodyRadius = size.minDimension / 3.0f
        val center = Offset(size.width / 2f, size.height * 0.63f + bounceOffset)

        scale(scale, pivot = center) {
            // The aura sits behind everything else — a flourish, not a distraction. Mythic gets
            // its own bigger, animated version rather than a scaled-up stage-2 aura.
            when {
                stage >= 3 -> drawMythicAura(center, bodyRadius, species.accentColor, cycle)
                stage == 2 -> drawAura(center, bodyRadius, species.accentColor)
            }
            // Ears/horns/wings sit behind the body so the body circle covers their base, like
            // they're actually attached rather than floating.
            drawSpeciesFeatures(species, center, bodyRadius)
            if (stage >= 3) drawMythicFlourish(species, center, bodyRadius)

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

            drawEyes(mood, center, bodyRadius, cycle, sleepy = sleepyEyes)
            drawMouth(mood, center, bodyRadius)
            // Stage 1+ gets one small extra detail — visible proof an evolution happened.
            if (stage >= 1) drawStageMark(center, bodyRadius, species.accentColor)
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
    sleepy: Boolean = false,
) {
    val eyeOffsetX = bodyRadius * 0.42f
    val eyeOffsetY = -bodyRadius * 0.1f
    val leftEye = center + Offset(-eyeOffsetX, eyeOffsetY)
    val rightEye = center + Offset(eyeOffsetX, eyeOffsetY)
    val eyeRadius = bodyRadius * 0.16f

    if (sleepy || mood == CritterMood.SLUGGISH_TIRED) {
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
 * The silhouette that tells species apart at a glance, drawn *behind* the body circle so it
 * reads as attached (ears, horns and wings all poke out from underneath). Every measurement is
 * a fraction of [bodyRadius], and every feature stays well under the ~0.84×[bodyRadius] the
 * tallest hat (the halo) needs above the head — see [drawHat] — so species art never competes
 * with a worn hat for headroom.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpeciesFeatures(
    species: Species,
    center: Offset,
    bodyRadius: Float,
) {
    val headTop = center.y - bodyRadius
    when (species.key) {
        SpeciesCatalog.BUNNY.key -> {
            val earHeight = bodyRadius * 0.75f
            val earWidth = bodyRadius * 0.26f
            for (side in listOf(-1f, 1f)) {
                val earBase = center + Offset(side * bodyRadius * 0.35f, -bodyRadius * 0.1f)
                drawOval(
                    color = species.primaryColor,
                    topLeft = Offset(earBase.x - earWidth / 2f, earBase.y - earHeight),
                    size = Size(earWidth, earHeight),
                )
                drawOval(
                    color = species.accentColor.copy(alpha = 0.6f),
                    topLeft = Offset(earBase.x - earWidth * 0.3f, earBase.y - earHeight * 0.85f),
                    size = Size(earWidth * 0.6f, earHeight * 0.65f),
                )
            }
        }

        SpeciesCatalog.CHICK.key -> {
            val beak = Path().apply {
                moveTo(center.x - bodyRadius * 0.14f, center.y + bodyRadius * 0.02f)
                lineTo(center.x + bodyRadius * 0.14f, center.y + bodyRadius * 0.02f)
                lineTo(center.x, center.y + bodyRadius * 0.22f)
                close()
            }
            drawPath(beak, species.accentColor)
            for (side in listOf(-1f, 1f)) {
                drawOval(
                    color = species.accentColor.copy(alpha = 0.85f),
                    topLeft = Offset(
                        center.x + side * bodyRadius * 0.75f - bodyRadius * 0.18f,
                        center.y - bodyRadius * 0.05f,
                    ),
                    size = Size(bodyRadius * 0.36f, bodyRadius * 0.5f),
                )
            }
        }

        SpeciesCatalog.AXOLOTL.key -> {
            for (side in listOf(-1f, 1f)) {
                repeat(3) { i ->
                    val y = center.y - bodyRadius * 0.15f + i * bodyRadius * 0.22f
                    drawLine(
                        color = species.accentColor,
                        start = Offset(center.x + side * bodyRadius * 0.95f, y),
                        end = Offset(center.x + side * bodyRadius * 1.25f, y - bodyRadius * 0.12f),
                        strokeWidth = bodyRadius * 0.09f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        SpeciesCatalog.DRAGON.key -> {
            for (side in listOf(-1f, 1f)) {
                val horn = Path().apply {
                    moveTo(center.x + side * bodyRadius * 0.28f, headTop + bodyRadius * 0.15f)
                    lineTo(center.x + side * bodyRadius * 0.48f, headTop - bodyRadius * 0.35f)
                    lineTo(center.x + side * bodyRadius * 0.14f, headTop + bodyRadius * 0.05f)
                    close()
                }
                drawPath(horn, species.accentColor)
                drawOval(
                    color = species.accentColor.copy(alpha = 0.7f),
                    topLeft = Offset(
                        center.x + side * bodyRadius * 0.85f - bodyRadius * 0.2f,
                        center.y - bodyRadius * 0.3f,
                    ),
                    size = Size(bodyRadius * 0.4f, bodyRadius * 0.6f),
                )
            }
        }

        // Sloth's tell is the sleepy eyes (forced on regardless of mood, see sleepyEyes above)
        // and blob has no extra feature at all — it's still just the circle, per spec.
        else -> Unit
    }
}

/** Stage 1's "one extra detail": a small sparkle mark, sized off the body so it scales with it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStageMark(
    center: Offset,
    bodyRadius: Float,
    color: Color,
) {
    val markCenter = center + Offset(bodyRadius * 0.55f, -bodyRadius * 0.55f)
    val arm = bodyRadius * 0.14f
    val strokeWidth = arm * 0.45f
    drawLine(color, markCenter - Offset(arm, 0f), markCenter + Offset(arm, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color, markCenter - Offset(0f, arm), markCenter + Offset(0f, arm), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

/**
 * Stage 2's "visible aura or extra flourish": two translucent rings around the body. The outer
 * ring reaches 1.35×[bodyRadius] from the centre — only ~0.35×[bodyRadius] above the head, well
 * under the halo hat's ~0.84×[bodyRadius] headroom budget, so it never pushes a worn hat off
 * the top of the canvas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAura(
    center: Offset,
    bodyRadius: Float,
    color: Color,
) {
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = bodyRadius * 1.22f,
        center = center,
        style = Stroke(width = bodyRadius * 0.16f),
    )
    drawCircle(
        color = color.copy(alpha = 0.18f),
        radius = bodyRadius * 1.35f,
        center = center,
        style = Stroke(width = bodyRadius * 0.10f),
    )
}

/** Angles for the Mythic aura's orbiting runes, precomputed once — the draw loop allocates nothing. */
private const val MYTHIC_RUNE_COUNT = 6
private val MYTHIC_RUNE_ANGLES = FloatArray(MYTHIC_RUNE_COUNT) { (2f * PI.toFloat() / MYTHIC_RUNE_COUNT) * it }

/**
 * Stage 3's unmistakable aura: two wide rings plus a handful of runes slowly orbiting the body,
 * driven by the same [cycle] the idle animation already uses so nothing extra needs to be
 * allocated per frame — [MYTHIC_RUNE_ANGLES] is the only geometry, computed once above. The
 * outermost point (rings 1.58×[bodyRadius], runes at 1.55×) stays under the halo hat's
 * ~0.84×[bodyRadius] headroom budget (see [drawHat]), so the largest hat on the largest stage
 * never gets crowded off the canvas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMythicAura(
    center: Offset,
    bodyRadius: Float,
    color: Color,
    cycle: Float,
) {
    drawCircle(
        color = color.copy(alpha = 0.32f),
        radius = bodyRadius * 1.40f,
        center = center,
        style = Stroke(width = bodyRadius * 0.15f),
    )
    drawCircle(
        color = SunshineYellow.copy(alpha = 0.16f),
        radius = bodyRadius * 1.58f,
        center = center,
        style = Stroke(width = bodyRadius * 0.08f),
    )
    val runeDistance = bodyRadius * 1.55f
    val rotation = cycle * 2f * PI.toFloat()
    for (i in 0 until MYTHIC_RUNE_COUNT) {
        val angle = MYTHIC_RUNE_ANGLES[i] + rotation
        val position = center + Offset(cos(angle) * runeDistance, sin(angle) * runeDistance)
        drawCircle(color = color.copy(alpha = 0.6f), radius = bodyRadius * 0.09f, center = position)
        drawCircle(color = SunshineYellow.copy(alpha = 0.8f), radius = bodyRadius * 0.035f, center = position)
    }
}

/**
 * Stage 3's per-species flourish, layered on top of the ordinary [drawSpeciesFeatures] silhouette
 * so every critter gets a Mythic tell distinct from its regular one. Every measurement stays
 * within ~0.5×[bodyRadius] above the head — well under the halo's headroom budget — because these
 * are meant to read alongside a worn hat, not compete with it for space above the head.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMythicFlourish(
    species: Species,
    center: Offset,
    bodyRadius: Float,
) {
    when (species.key) {
        SpeciesCatalog.DRAGON.key -> {
            // Wings spread wide to both sides.
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(center.x + side * bodyRadius * 0.3f, center.y - bodyRadius * 0.1f)
                    lineTo(center.x + side * bodyRadius * 1.9f, center.y - bodyRadius * 0.55f)
                    lineTo(center.x + side * bodyRadius * 1.6f, center.y + bodyRadius * 0.15f)
                    lineTo(center.x + side * bodyRadius * 1.85f, center.y + bodyRadius * 0.35f)
                    lineTo(center.x + side * bodyRadius * 0.35f, center.y + bodyRadius * 0.45f)
                    close()
                }
                drawPath(wing, species.accentColor.copy(alpha = 0.75f))
            }
        }

        SpeciesCatalog.AXOLOTL.key -> {
            // The existing gill frills, redrawn bigger with a soft glow behind them.
            for (side in listOf(-1f, 1f)) {
                repeat(3) { i ->
                    val y = center.y - bodyRadius * 0.15f + i * bodyRadius * 0.22f
                    val tip = Offset(center.x + side * bodyRadius * 1.5f, y - bodyRadius * 0.18f)
                    drawCircle(color = species.accentColor.copy(alpha = 0.35f), radius = bodyRadius * 0.16f, center = tip)
                    drawLine(
                        color = species.accentColor,
                        start = Offset(center.x + side * bodyRadius * 0.95f, y),
                        end = tip,
                        strokeWidth = bodyRadius * 0.10f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        SpeciesCatalog.SLOTH.key -> {
            // Draped over a branch behind the body.
            drawLine(
                color = species.accentColor,
                start = Offset(center.x - bodyRadius * 1.5f, center.y + bodyRadius * 0.4f),
                end = Offset(center.x + bodyRadius * 1.5f, center.y + bodyRadius * 0.4f),
                strokeWidth = bodyRadius * 0.22f,
                cap = StrokeCap.Round,
            )
        }

        SpeciesCatalog.CHICK.key -> {
            // Sunrise rays fanning out behind, low enough to clear the halo's headroom.
            repeat(5) { i ->
                val angle = -PI.toFloat() / 2f + (i - 2) * (PI.toFloat() / 7f)
                val start = center + Offset(cos(angle) * bodyRadius * 1.1f, sin(angle) * bodyRadius * 1.1f)
                val end = center + Offset(cos(angle) * bodyRadius * 1.45f, sin(angle) * bodyRadius * 1.45f)
                drawLine(species.accentColor, start, end, strokeWidth = bodyRadius * 0.08f, cap = StrokeCap.Round)
            }
        }

        SpeciesCatalog.BUNNY.key -> {
            // A little sparkle at each ear tip.
            val earHeight = bodyRadius * 0.75f
            for (side in listOf(-1f, 1f)) {
                val tip = center + Offset(side * bodyRadius * 0.35f, -bodyRadius * 0.1f - earHeight)
                drawCircle(color = SunshineYellow.copy(alpha = 0.85f), radius = bodyRadius * 0.09f, center = tip)
            }
        }

        SpeciesCatalog.BLOB.key -> {
            // Bioluminescent spots across the body — the only flourish that sits on top of it.
            val spots = listOf(-0.3f to -0.1f, 0.25f to 0.05f, 0.0f to 0.35f)
            spots.forEach { (dx, dy) ->
                drawCircle(
                    color = species.accentColor.copy(alpha = 0.5f),
                    radius = bodyRadius * 0.08f,
                    center = center + Offset(bodyRadius * dx, bodyRadius * dy),
                )
            }
        }

        SpeciesCatalog.PHOENIX.key -> {
            // A flowing tail of feathers trailing below and behind.
            val tail = Path().apply {
                moveTo(center.x - bodyRadius * 0.2f, center.y + bodyRadius * 0.7f)
                lineTo(center.x - bodyRadius * 0.9f, center.y + bodyRadius * 1.7f)
                lineTo(center.x - bodyRadius * 0.1f, center.y + bodyRadius * 1.2f)
                lineTo(center.x + bodyRadius * 0.5f, center.y + bodyRadius * 1.9f)
                lineTo(center.x + bodyRadius * 0.25f, center.y + bodyRadius * 0.8f)
                close()
            }
            drawPath(tail, species.primaryColor.copy(alpha = 0.8f))
            drawPath(tail, style = Stroke(width = bodyRadius * 0.04f), color = species.accentColor)
        }

        else -> Unit
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
