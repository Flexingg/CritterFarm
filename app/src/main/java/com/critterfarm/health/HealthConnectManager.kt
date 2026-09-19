package com.critterfarm.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
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
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only door between the app and Android Health Connect. Every read is wrapped so a
 * revoked permission, a missing provider, or a flaky OEM implementation degrades to "we
 * don't know" (null) rather than crashing the farm.
 */
class HealthConnectManager(private val context: Context) {

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    /** Whether Health Connect is installed, needs an update, or is ready to use. */
    fun getAvailability(): HealthConnectAvailability {
        val status = HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE_NAME)
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UpdateRequired
            else -> if (isProviderPackageInstalled()) {
                HealthConnectAvailability.Unavailable
            } else {
                HealthConnectAvailability.NotInstalled
            }
        }
    }

    private fun isProviderPackageInstalled(): Boolean =
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PROVIDER_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /** Permissions currently granted by the user, or empty if the check itself fails. */
    suspend fun getGrantedPermissions(): Set<String> = withContext(Dispatchers.IO) {
        try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: SecurityException) {
            emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun hasAllRequiredPermissions(): Boolean =
        getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)

    /** Aggregated stats for the current calendar day, used to render the farm's stat pods. */
    suspend fun getTodaySnapshot(): TodayHealthSnapshot = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val todayRange = TimeRangeFilter.between(startOfDay, now)
        val lastNightRange = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)

        TodayHealthSnapshot(
            date = today,
            steps = aggregateMetric(StepsRecord.COUNT_TOTAL, todayRange),
            totalCaloriesBurnedKcal = aggregateMetric(TotalCaloriesBurnedRecord.ENERGY_TOTAL, todayRange)
                ?.inKilocalories,
            activeCaloriesBurnedKcal = aggregateMetric(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, todayRange)
                ?.inKilocalories,
            dietaryEnergyKcal = aggregateMetric(NutritionRecord.ENERGY_TOTAL, todayRange)?.inKilocalories,
            hydrationMl = aggregateMetric(HydrationRecord.VOLUME_TOTAL, todayRange)?.inMilliliters,
            distanceMeters = aggregateMetric(DistanceRecord.DISTANCE_TOTAL, todayRange)?.inMeters,
            sleepMinutesLastNight = aggregateMetric(SleepSessionRecord.SLEEP_DURATION_TOTAL, lastNightRange)
                ?.toMinutes(),
            exerciseSessions = readExerciseSessions(todayRange),
            latestWeightKg = readLatestWeightKg(),
        )
    }

    /**
     * The 48h catch-up: on app launch we look back at most 48 hours, but never further back
     * than [since] (the last recorded sync timestamp), so a delta since yesterday isn't
     * inflated by two full days of data.
     */
    suspend fun computeDelta(since: Instant): HealthDelta = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val earliestAllowed = now.minus(Duration.ofHours(48))
        val periodStart = if (since.isBefore(earliestAllowed)) earliestAllowed else since
        val range = TimeRangeFilter.between(periodStart, now)

        HealthDelta(
            periodStart = periodStart,
            periodEnd = now,
            steps = aggregateMetric(StepsRecord.COUNT_TOTAL, range),
            totalCaloriesBurnedKcal = aggregateMetric(TotalCaloriesBurnedRecord.ENERGY_TOTAL, range)
                ?.inKilocalories,
            activeCaloriesBurnedKcal = aggregateMetric(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, range)
                ?.inKilocalories,
            dietaryEnergyKcal = aggregateMetric(NutritionRecord.ENERGY_TOTAL, range)?.inKilocalories,
            hydrationMl = aggregateMetric(HydrationRecord.VOLUME_TOTAL, range)?.inMilliliters,
            distanceMeters = aggregateMetric(DistanceRecord.DISTANCE_TOTAL, range)?.inMeters,
            sleepMinutes = aggregateMetric(SleepSessionRecord.SLEEP_DURATION_TOTAL, range)?.toMinutes(),
            exerciseSessions = readExerciseSessions(range),
            latestWeightKg = readLatestWeightKg(),
        )
    }

    private suspend fun <T : Any> aggregateMetric(
        metric: AggregateMetric<T>,
        range: TimeRangeFilter,
    ): T? =
        try {
            val result = healthConnectClient.aggregate(AggregateRequest(setOf(metric), range))
            result[metric]
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

    private suspend fun readExerciseSessions(range: TimeRangeFilter): List<ExerciseSessionSummary>? =
        try {
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = range,
            )
            healthConnectClient.readRecords(request).records.map { record ->
                ExerciseSessionSummary(
                    startTime = record.startTime,
                    endTime = record.endTime,
                    exerciseType = record.exerciseType,
                    title = record.title,
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

    private suspend fun readLatestWeightKg(): Double? =
        try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                ascendingOrder = false,
                pageSize = 1,
            )
            healthConnectClient.readRecords(request).records.firstOrNull()?.weight?.inKilograms
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }

    companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"

        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
        )
    }
}
