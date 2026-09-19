package com.critterfarm.data

import com.critterfarm.data.local.CritterDao
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import com.critterfarm.data.local.DailySummaryLogDao
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.FarmInventoryDao
import com.critterfarm.data.local.FarmInventoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Outcome of tapping the "Claim Daily Turn" chest. */
sealed class ClaimResult {
    /** No sync has happened for that day yet, so there is nothing to claim. */
    data object NothingToClaim : ClaimResult()

    /** The chest for that day was already opened; no additional rewards are paid. */
    data class AlreadyClaimed(val log: DailySummaryLogEntity) : ClaimResult()

    /** First successful claim for the day, with the spoils that were just paid out. */
    data class Claimed(
        val log: DailySummaryLogEntity,
        val xpEarned: Int,
        val coinsEarned: Int,
        val treatsEarned: Int,
        val manaSparksEarned: Int,
        val supportiveMessage: String?,
    ) : ClaimResult()
}

/**
 * The single source of truth for game state. Wraps the Room DAOs and applies [GameRules] so
 * callers (view models) never touch reward math or persistence directly.
 */
class GameRepository(
    private val critterDao: CritterDao,
    private val inventoryDao: FarmInventoryDao,
    private val dailySummaryLogDao: DailySummaryLogDao,
) {
    val critter: Flow<CritterEntity?> = critterDao.observeCritter()
    val inventory: Flow<FarmInventoryEntity?> =
        inventoryDao.observeInventory(FarmInventoryEntity.SINGLETON_ID)
    val dailyLogs: Flow<List<DailySummaryLogEntity>> = dailySummaryLogDao.observeAll()

    fun observeDailyLog(date: LocalDate): Flow<DailySummaryLogEntity?> =
        dailySummaryLogDao.observeByDate(date.toString())

    /** Watermark for the 48h catch-up: the latest sync timestamp recorded across all days. */
    suspend fun getLastSyncedAt(): Instant? =
        dailySummaryLogDao.getMaxSyncedAt()?.let { Instant.ofEpochMilli(it) }

    /** Seeds "Sprout the Blob" and an empty inventory on first launch. Safe to call repeatedly. */
    suspend fun ensureInitialGameState() {
        if (critterDao.getCritter() == null) {
            val now = System.currentTimeMillis()
            critterDao.insert(
                CritterEntity(
                    name = STARTER_CRITTER_NAME,
                    species = "Blob",
                    stage = 0,
                    xp = 0,
                    level = 1,
                    happiness = 80,
                    hunger = 30,
                    mood = CritterMood.BOUNCING_HAPPY,
                    lastFedAt = now,
                    createdAt = now,
                ),
            )
        }
        if (inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID) == null) {
            inventoryDao.upsert(
                FarmInventoryEntity(
                    id = FarmInventoryEntity.SINGLETON_ID,
                    coins = 0,
                    treats = 0,
                    manaSparks = 0,
                    seeds = 0,
                    ownedHatIds = "",
                    equippedHatId = null,
                ),
            )
        }
    }

    /**
     * Merges the latest Health Connect totals into today's log. This only updates the raw
     * stats — no rewards are paid until [claimDailyTurn] is called, so an automatic sync can
     * run as often as needed without granting free loot.
     */
    suspend fun upsertDailyStats(
        date: LocalDate,
        steps: Long,
        caloriesBurnedKcal: Double,
        caloriesConsumedKcal: Double,
        hydrationMl: Double,
        sleepMinutes: Long,
        workouts: Int,
        weightKg: Double?,
        syncedAt: Instant,
    ) {
        val key = date.toString()
        val existing = dailySummaryLogDao.getByDate(key)
        val base = existing ?: DailySummaryLogEntity(
            date = key,
            steps = 0,
            caloriesBurned = 0.0,
            caloriesConsumed = 0.0,
            deficit = 0.0,
            hydrationMl = 0.0,
            sleepMinutes = 0,
            workouts = 0,
            weightKg = null,
            xpEarned = 0,
            coinsEarned = 0,
            treatsEarned = 0,
            chestClaimed = false,
            syncedAt = 0,
        )
        dailySummaryLogDao.upsert(
            base.copy(
                steps = steps,
                caloriesBurned = caloriesBurnedKcal,
                caloriesConsumed = caloriesConsumedKcal,
                deficit = caloriesBurnedKcal - caloriesConsumedKcal,
                hydrationMl = hydrationMl,
                sleepMinutes = sleepMinutes,
                workouts = workouts,
                weightKg = weightKg ?: existing?.weightKg,
                syncedAt = syncedAt.toEpochMilli(),
            ),
        )
        refreshMood(date)
    }

    /**
     * Tallies today's spoils and deposits them into the critter and inventory. Idempotent:
     * once [DailySummaryLogEntity.chestClaimed] is true for a day, calling this again returns
     * [ClaimResult.AlreadyClaimed] without paying out a second time.
     */
    suspend fun claimDailyTurn(date: LocalDate): ClaimResult {
        val log = dailySummaryLogDao.getByDate(date.toString()) ?: return ClaimResult.NothingToClaim
        if (log.chestClaimed) return ClaimResult.AlreadyClaimed(log)

        val coinsEarned = GameRules.coinsFromSteps(log.steps)
        val treatsEarned = GameRules.treatsFromWorkouts(log.workouts)
        val deficitReward = GameRules.manaSparksFromDeficit(log.deficit, log.caloriesConsumed)
        val hydrationBoost = GameRules.hydrationMoodBoost(log.hydrationMl)
        val xpEarned = coinsEarned + treatsEarned * 4 + deficitReward.manaSparks * 3 + hydrationBoost

        critterDao.getCritter()?.let { critter ->
            val leveledUp = GameRules.applyXp(critter, xpEarned)
            critterDao.update(
                leveledUp.copy(
                    happiness = (leveledUp.happiness + hydrationBoost).coerceIn(0, 100),
                    hunger = (leveledUp.hunger - treatsEarned * 2).coerceIn(0, 100),
                    mood = GameRules.deriveMood(leveledUp, log.steps, log.workouts, chestClaimedToday = true),
                    lastFedAt = if (treatsEarned > 0) System.currentTimeMillis() else leveledUp.lastFedAt,
                ),
            )
        }

        inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)?.let { inventory ->
            inventoryDao.upsert(
                inventory.copy(
                    coins = inventory.coins + coinsEarned,
                    treats = inventory.treats + treatsEarned,
                    manaSparks = inventory.manaSparks + deficitReward.manaSparks,
                ),
            )
        }

        val claimedLog = log.copy(
            xpEarned = xpEarned,
            coinsEarned = coinsEarned,
            treatsEarned = treatsEarned,
            chestClaimed = true,
        )
        dailySummaryLogDao.upsert(claimedLog)

        return ClaimResult.Claimed(
            log = claimedLog,
            xpEarned = xpEarned,
            coinsEarned = coinsEarned,
            treatsEarned = treatsEarned,
            manaSparksEarned = deficitReward.manaSparks,
            supportiveMessage = deficitReward.supportiveMessage,
        )
    }

    private suspend fun refreshMood(date: LocalDate) {
        val log = dailySummaryLogDao.getByDate(date.toString()) ?: return
        val critter = critterDao.getCritter() ?: return
        critterDao.update(
            critter.copy(
                mood = GameRules.deriveMood(critter, log.steps, log.workouts, log.chestClaimed),
            ),
        )
    }

    companion object {
        const val STARTER_CRITTER_NAME = "Sprout the Blob"
    }
}
