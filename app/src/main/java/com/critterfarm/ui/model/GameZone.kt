package com.critterfarm.ui.model

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * Maps each area of the farm to the Health Connect permission(s) it needs. A zone with any
 * ungranted permission is a "Dormant Zone" — never a dead end, just sleepy until linked.
 */
enum class GameZone(
    val displayName: String,
    val emoji: String,
    val dormantMessage: String,
    val requiredPermissions: Set<String>,
) {
    GROWTH_SPARK(
        displayName = "Growth Spark",
        emoji = "✨",
        dormantMessage = "Growth Spark is napping until calorie tracking is linked.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
        ),
    ),
    PASTURE_ROAM(
        displayName = "Pasture Roam",
        emoji = "🐾",
        dormantMessage = "Pasture Roam is waiting for step tracking to wake it up.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        ),
    ),
    FRESH_POND(
        displayName = "Fresh Pond",
        emoji = "💧",
        dormantMessage = "The Pond is sleeping until water tracking is linked.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(HydrationRecord::class),
        ),
    ),
    GYM_BARN(
        displayName = "Gym Barn",
        emoji = "🏋",
        dormantMessage = "The Gym Barn is quiet until workout tracking is linked.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        ),
    ),
    COZY_BARN(
        displayName = "Cozy Barn",
        emoji = "💤",
        dormantMessage = "The Cozy Barn is dim until sleep tracking is linked.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        ),
    ),
    EVOLUTION_SCALE(
        displayName = "Evolution Scale",
        emoji = "⚖️",
        dormantMessage = "The Evolution Scale is still until weigh-ins are linked.",
        requiredPermissions = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
        ),
    ),
    ;

    fun isDormant(grantedPermissions: Set<String>): Boolean =
        !grantedPermissions.containsAll(requiredPermissions)
}
