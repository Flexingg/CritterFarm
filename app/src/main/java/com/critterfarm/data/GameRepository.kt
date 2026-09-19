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

    /**
     * First successful claim for the day. [streakDays] and [multiplier] are reported so the
     * celebration can show what the chain just earned — the rewards are what the streak pays.
     */
    data class Claimed(
        val log: DailySummaryLogEntity,
        val xpEarned: Int,
        val coinsEarned: Int,
        val treatsEarned: Int,
        val manaSparksEarned: Int,
        val supportiveMessage: String?,
        val streakDays: Int,
        val multiplier: Double,
        val freezeUsed: Boolean,
    ) : ClaimResult()
}

/** Outcome of trying to buy something. */
sealed class PurchaseResult {
    data class Purchased(val item: ShopItem, val inventory: FarmInventoryEntity) : PurchaseResult()

    /** Cosmetics are one-time purchases; freezes are not. */
    data object AlreadyOwned : PurchaseResult()

    data class CannotAfford(val item: ShopItem) : PurchaseResult()

    data object UnknownItem : PurchaseResult()

    data object NoInventory : PurchaseResult()
}

/** Outcome of feeding the critter a treat. */
sealed class FeedResult {
    data class Fed(val happiness: Int, val hunger: Int, val treatsLeft: Int) : FeedResult()

    data object NoTreats : FeedResult()
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
            inventoryDao.upsert(FarmInventoryEntity.empty())
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
     *
     * The streak multiplier is applied here — the chain is what compounds, not the day.
     */
    suspend fun claimDailyTurn(date: LocalDate): ClaimResult {
        val log = dailySummaryLogDao.getByDate(date.toString()) ?: return ClaimResult.NothingToClaim
        if (log.chestClaimed) return ClaimResult.AlreadyClaimed(log)

        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: FarmInventoryEntity.empty()

        val streak = GameRules.nextStreak(
            previousStreak = inventory.claimStreak,
            lastClaimedDate = inventory.lastClaimedDate,
            today = date,
            freezesAvailable = inventory.streakFreezes,
        )
        val multiplier = GameRules.claimMultiplier(streak.streak)

        val coinsEarned = GameRules.applyMultiplier(GameRules.coinsFromSteps(log.steps), multiplier)
        val treatsEarned = GameRules.applyMultiplier(GameRules.treatsFromWorkouts(log.workouts), multiplier)
        val deficitReward = GameRules.manaSparksFromDeficit(log.deficit, log.caloriesConsumed)
        val manaSparksEarned = GameRules.applyMultiplier(deficitReward.manaSparks, multiplier)
        val hydrationBoost = GameRules.hydrationMoodBoost(log.hydrationMl)
        val xpEarned = coinsEarned + treatsEarned * 4 + manaSparksEarned * 3 + hydrationBoost

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

        inventoryDao.upsert(
            inventory.copy(
                coins = inventory.coins + coinsEarned,
                treats = inventory.treats + treatsEarned,
                manaSparks = inventory.manaSparks + manaSparksEarned,
                claimStreak = streak.streak,
                bestStreak = maxOf(inventory.bestStreak, streak.streak),
                streakFreezes = streak.freezesLeft,
                lastClaimedDate = date.toString(),
            ),
        )

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
            manaSparksEarned = manaSparksEarned,
            supportiveMessage = deficitReward.supportiveMessage,
            streakDays = streak.streak,
            multiplier = multiplier,
            freezeUsed = streak.freezeUsed,
        )
    }

    // --------------------------------------------------------------------------- shopping ----

    /**
     * Buys a catalogue item. Only cosmetics and streak freezes are purchasable — nothing here
     * can buy health progress, which is the whole point of the catalogue being closed.
     */
    suspend fun purchase(itemId: String): PurchaseResult {
        val item = ShopCatalog.item(itemId) ?: return PurchaseResult.UnknownItem
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return PurchaseResult.NoInventory
        val owned = GameRules.parseOwnedHatIds(inventory.ownedHatIds)

        if (!item.repeatable && item.id in owned) return PurchaseResult.AlreadyOwned
        if (!GameRules.canAfford(item, inventory.coins, inventory.manaSparks)) {
            return PurchaseResult.CannotAfford(item)
        }

        val purchased = when (item.kind) {
            ShopItemKind.HAT -> inventory.copy(
                coins = if (item.currency == ShopCurrency.COINS) inventory.coins - item.price else inventory.coins,
                manaSparks = if (item.currency == ShopCurrency.MANA_SPARKS) {
                    inventory.manaSparks - item.price
                } else {
                    inventory.manaSparks
                },
                ownedHatIds = GameRules.serializeOwnedHatIds(owned + item.id),
            )

            ShopItemKind.STREAK_FREEZE -> inventory.copy(
                coins = inventory.coins - item.price,
                streakFreezes = inventory.streakFreezes + 1,
            )
        }
        inventoryDao.upsert(purchased)
        return PurchaseResult.Purchased(item, purchased)
    }

    /** Equips an owned hat, or clears it when [itemId] is null. Returns the resulting hat id. */
    suspend fun equipHat(itemId: String?): String? {
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID) ?: return null
        val owned = GameRules.parseOwnedHatIds(inventory.ownedHatIds)
        val next = when {
            itemId == null -> null
            itemId in owned -> itemId
            else -> return inventory.equippedHatId
        }
        inventoryDao.upsert(inventory.copy(equippedHatId = next))
        return next
    }

    // ---------------------------------------------------------------------------- feeding ----

    /**
     * Spends one treat on the critter. This is the daily ritual that gives treats a purpose —
     * you cannot buy affection, you collect it by training.
     */
    suspend fun feedCritter(): FeedResult {
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return FeedResult.NoTreats
        if (inventory.treats <= 0) return FeedResult.NoTreats

        val critter = critterDao.getCritter() ?: return FeedResult.NoTreats
        val outcome = GameRules.feed(critter.happiness, critter.hunger)

        critterDao.update(
            critter.copy(
                happiness = outcome.happiness,
                hunger = outcome.hunger,
                mood = CritterMood.CELEBRATING,
                lastFedAt = System.currentTimeMillis(),
            ),
        )
        val after = inventory.copy(treats = inventory.treats - 1)
        inventoryDao.upsert(after)

        return FeedResult.Fed(
            happiness = outcome.happiness,
            hunger = outcome.hunger,
            treatsLeft = after.treats,
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
