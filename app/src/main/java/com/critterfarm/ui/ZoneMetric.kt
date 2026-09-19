package com.critterfarm.ui

import com.critterfarm.data.Metric
import com.critterfarm.ui.model.GameZone

/**
 * A tapped stat row is a UI concept (a zone) but the history maths speaks in metrics, so the
 * mapping lives here rather than leaking either layer into the other. Shared by [MainActivity]
 * (building history routes) and `FarmViewModel` (per-zone streak chips) so it exists in one place.
 */
fun GameZone.toMetric(): Metric = when (this) {
    GameZone.PASTURE_ROAM -> Metric.STEPS
    GameZone.GROWTH_SPARK -> Metric.DEFICIT
    GameZone.FRESH_POND -> Metric.HYDRATION
    GameZone.GYM_BARN -> Metric.WORKOUTS
    GameZone.COZY_BARN -> Metric.SLEEP
    GameZone.EVOLUTION_SCALE -> Metric.WEIGHT
}
