package com.critterfarm.health

import java.time.Instant
import java.time.LocalDate

/**
 * Result of [HealthConnectClient.getSdkStatus], normalized so the UI can render a warm
 * fallback instead of a raw SDK integer.
 */
sealed class HealthConnectAvailability {
    data object Available : HealthConnectAvailability()
    data object NotInstalled : HealthConnectAvailability()
    data object UpdateRequired : HealthConnectAvailability()
    data object Unavailable : HealthConnectAvailability()
}

/** A single workout session, trimmed down to what the Gym Barn animation needs. */
data class ExerciseSessionSummary(
    val startTime: Instant,
    val endTime: Instant,
    val exerciseType: Int,
    val title: String?,
)

/**
 * Every field is nullable on purpose: null means "this record type could not be read"
 * (permission revoked, provider hiccup), while 0/0.0 means "read succeeded, no activity
 * happened." Collapsing those two cases would make Dormant Zones indistinguishable from a
 * genuinely quiet day.
 */
data class TodayHealthSnapshot(
    val date: LocalDate,
    val steps: Long?,
    val totalCaloriesBurnedKcal: Double?,
    val activeCaloriesBurnedKcal: Double?,
    val dietaryEnergyKcal: Double?,
    val hydrationMl: Double?,
    val distanceMeters: Double?,
    val sleepMinutesLastNight: Long?,
    val exerciseSessions: List<ExerciseSessionSummary>?,
    val latestWeightKg: Double?,
)

/** The 48h catch-up delta, computed since the last recorded sync timestamp. */
data class HealthDelta(
    val periodStart: Instant,
    val periodEnd: Instant,
    val steps: Long?,
    val totalCaloriesBurnedKcal: Double?,
    val activeCaloriesBurnedKcal: Double?,
    val dietaryEnergyKcal: Double?,
    val hydrationMl: Double?,
    val distanceMeters: Double?,
    val sleepMinutes: Long?,
    val exerciseSessions: List<ExerciseSessionSummary>?,
    val latestWeightKg: Double?,
)
