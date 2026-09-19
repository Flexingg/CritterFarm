package com.critterfarm.ui.farm

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.ui.model.GameZone
import com.critterfarm.ui.theme.BerryPink
import com.critterfarm.ui.theme.DormantGray
import com.critterfarm.ui.theme.DormantGraySurface
import com.critterfarm.ui.theme.EmberOrange
import com.critterfarm.ui.theme.PondBlueDeep
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SlumberLavender
import com.critterfarm.ui.theme.SproutGreen
import com.critterfarm.ui.theme.TreatBrown

/**
 * One line of truth about a metric: what it is, where you are, and where you need to be.
 *
 * Deliberately not a Composable so it can be built and asserted on in plain unit tests.
 */
data class StatTarget(
    val zone: GameZone,
    val currentText: String,
    val targetText: String,
    val progress: Float,
    val statusText: String,
    val linked: Boolean,
    val met: Boolean,
    val accent: Color,
    val streakDays: Int = 0,
) {
    /** Screen-reader sentence for the whole row. */
    val spokenText: String
        get() = "${zone.displayName}: $currentText of $targetText, $statusText"
}

/**
 * Builds the six rows in a fixed order. Pure: same inputs, same rows, no Android needed.
 *
 * A dormant zone still shows its TARGET — that is the point of the redesign. Not knowing a value
 * is never the same as the value being zero, so unlinked rows render [StatsFormat.NO_VALUE]
 * rather than a fake 0.
 */
fun buildStatTargets(
    log: DailySummaryLogEntity?,
    dormantZones: Set<GameZone>,
    zoneStreaks: Map<GameZone, Int> = emptyMap(),
): List<StatTarget> {
    fun dormant(zone: GameZone) = dormantZones.contains(zone)

    val stepsGoal = StatsFormat.STEPS_GOAL
    val steps = log?.steps
    val stepsDormant = dormant(GameZone.PASTURE_ROAM)
    val stepsRow = StatTarget(
        zone = GameZone.PASTURE_ROAM,
        currentText = if (stepsDormant || steps == null) StatsFormat.NO_VALUE else StatsFormat.steps(steps),
        targetText = "${StatsFormat.steps(stepsGoal)} steps",
        progress = if (!stepsDormant && steps != null) StatsFormat.progress(steps, stepsGoal) else 0f,
        statusText = rowStatus(
            linked = !stepsDormant,
            hasValue = steps != null,
            met = steps != null && StatsFormat.goalMet(steps, stepsGoal),
            toGo = { StatsFormat.toGoLabel(steps ?: 0L, stepsGoal, "steps", thousands = true) },
        ),
        linked = !stepsDormant,
        met = !stepsDormant && steps != null && StatsFormat.goalMet(steps, stepsGoal),
        accent = SkyBlue,
    )

    val deficitGoal = StatsFormat.DEFICIT_GOAL_KCAL
    val deficit = log?.deficit?.coerceAtLeast(0.0)
    val deficitDormant = dormant(GameZone.GROWTH_SPARK)
    val deficitRow = StatTarget(
        zone = GameZone.GROWTH_SPARK,
        currentText = if (deficitDormant || deficit == null) StatsFormat.NO_VALUE else StatsFormat.kcalLabel(deficit),
        targetText = "${StatsFormat.kcalLabel(deficitGoal)} kcal deficit",
        progress = if (!deficitDormant && deficit != null) StatsFormat.progress(deficit, deficitGoal) else 0f,
        statusText = rowStatus(
            linked = !deficitDormant,
            hasValue = deficit != null,
            met = deficit != null && StatsFormat.goalMet(deficit, deficitGoal),
            toGo = { StatsFormat.toGoLabel(deficit ?: 0.0, deficitGoal, "kcal") },
        ),
        linked = !deficitDormant,
        met = !deficitDormant && deficit != null && StatsFormat.goalMet(deficit, deficitGoal),
        accent = EmberOrange,
    )

    val hydrationGoal = StatsFormat.HYDRATION_GOAL_FL_OZ
    val hydrationMl = log?.hydrationMl
    val hydrationFlOz = hydrationMl?.let { StatsFormat.mlToFlOz(it) }
    val pondDormant = dormant(GameZone.FRESH_POND)
    val hydrationRow = StatTarget(
        zone = GameZone.FRESH_POND,
        currentText = if (pondDormant || hydrationFlOz == null) {
            StatsFormat.NO_VALUE
        } else {
            StatsFormat.flOzLabel(hydrationMl!!)
        },
        targetText = "${StatsFormat.kcalLabel(hydrationGoal)} fl oz",
        progress = if (!pondDormant && hydrationFlOz != null) {
            StatsFormat.progress(hydrationFlOz, hydrationGoal)
        } else {
            0f
        },
        statusText = rowStatus(
            linked = !pondDormant,
            hasValue = hydrationFlOz != null,
            met = hydrationFlOz != null && StatsFormat.goalMet(hydrationFlOz, hydrationGoal),
            toGo = { StatsFormat.toGoLabel(hydrationFlOz ?: 0.0, hydrationGoal, "fl oz") },
        ),
        linked = !pondDormant,
        met = !pondDormant && hydrationFlOz != null && StatsFormat.goalMet(hydrationFlOz, hydrationGoal),
        accent = PondBlueDeep,
    )

    val workoutsGoal = StatsFormat.WORKOUTS_GOAL
    val workouts = log?.workouts
    val gymDormant = dormant(GameZone.GYM_BARN)
    val workoutRow = StatTarget(
        zone = GameZone.GYM_BARN,
        currentText = if (gymDormant || workouts == null) StatsFormat.NO_VALUE else "$workouts",
        targetText = StatsFormat.plural(workoutsGoal, "session"),
        progress = if (!gymDormant && workouts != null) {
            StatsFormat.progress(workouts.toDouble(), workoutsGoal.toDouble())
        } else {
            0f
        },
        statusText = rowStatus(
            linked = !gymDormant,
            hasValue = workouts != null,
            met = workouts != null && StatsFormat.goalMet(
                workouts.toDouble(),
                workoutsGoal.toDouble(),
            ),
            toGo = {
                StatsFormat.toGoLabel(
                    (workouts ?: 0).toDouble(),
                    workoutsGoal.toDouble(),
                    "session",
                )
            },
        ),
        linked = !gymDormant,
        met = !gymDormant && workouts != null && StatsFormat.goalMet(
            workouts.toDouble(),
            workoutsGoal.toDouble(),
        ),
        accent = BerryPink,
    )

    val sleepGoal = StatsFormat.SLEEP_GOAL_MINUTES
    val sleep = log?.sleepMinutes
    val sleepDormant = dormant(GameZone.COZY_BARN)
    val sleepRow = StatTarget(
        zone = GameZone.COZY_BARN,
        currentText = if (sleepDormant || sleep == null) StatsFormat.NO_VALUE else StatsFormat.sleepLabel(sleep),
        targetText = StatsFormat.sleepLabel(sleepGoal),
        progress = if (!sleepDormant && sleep != null) StatsFormat.progress(sleep, sleepGoal) else 0f,
        statusText = rowStatus(
            linked = !sleepDormant,
            hasValue = sleep != null,
            met = sleep != null && StatsFormat.goalMet(sleep, sleepGoal),
            toGo = { StatsFormat.sleepLabel(sleepGoal - (sleep ?: 0L)).let { "$it to go" } },
        ),
        linked = !sleepDormant,
        met = !sleepDormant && sleep != null && StatsFormat.goalMet(sleep, sleepGoal),
        accent = SlumberLavender,
    )

    val weightKg = log?.weightKg
    val scaleDormant = dormant(GameZone.EVOLUTION_SCALE)
    val weighedToday = weightKg != null
    val weightRow = StatTarget(
        zone = GameZone.EVOLUTION_SCALE,
        currentText = if (scaleDormant || weightKg == null) StatsFormat.NO_VALUE else "${StatsFormat.weightLbLabel(weightKg)} lb",
        targetText = "1 weigh-in today",
        progress = if (!scaleDormant && weighedToday) 1f else 0f,
        statusText = when {
            scaleDormant -> StatsFormat.LINK_HEALTH
            weighedToday -> "Logged today"
            else -> "No weigh-in yet"
        },
        linked = !scaleDormant,
        met = !scaleDormant && weighedToday,
        accent = TreatBrown,
    )

    return listOf(stepsRow, deficitRow, hydrationRow, workoutRow, sleepRow, weightRow)
        .map { row -> row.copy(streakDays = zoneStreaks[row.zone] ?: 0) }
}

/** Shared status copy so every row reads the same way. */
private fun rowStatus(
    linked: Boolean,
    hasValue: Boolean,
    met: Boolean,
    toGo: () -> String,
): String = when {
    !linked -> StatsFormat.LINK_HEALTH
    !hasValue -> "No data yet today"
    met -> StatsFormat.GOAL_MET
    else -> toGo()
}

/** The whole day at a glance: six rows, numbers first, always showing the target. */
@Composable
fun TodayStatsCard(
    log: DailySummaryLogEntity?,
    dormantZones: Set<GameZone>,
    zoneStreaks: Map<GameZone, Int> = emptyMap(),
    onRowClick: (GameZone) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val targets = buildStatTargets(log, dormantZones, zoneStreaks)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("Today on the farm", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Today's number against its target, for every zone. Tap a row for its history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            targets.forEach { target ->
                StatRow(target = target, onClick = { onRowClick(target.zone) })
            }
        }
    }
}

@Composable
private fun StatRow(target: StatTarget, onClick: () -> Unit) {
    val barColor = when {
        !target.linked -> DormantGray
        target.met -> SproutGreen
        else -> target.accent
    }
    val animatedProgress by animateFloatAsState(
        targetValue = target.progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "statBar",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = target.spokenText },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = target.zone.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = target.zone.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (target.streakDays >= 2) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🔥 ${target.streakDays}-day",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmberOrange,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = target.statusText,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !target.linked -> DormantGray
                    target.met -> SproutGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // The loudest thing on the row: where you are today.
            Text(
                text = target.currentText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (target.linked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    DormantGray
                },
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "/ ${target.targetText}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(DormantGraySurface),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(barColor),
            )
        }
    }
}
