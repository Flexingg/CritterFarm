package com.critterfarm.data

import com.critterfarm.data.local.ChallengeClaimDao
import com.critterfarm.data.local.ChallengeClaimEntity
import com.critterfarm.data.local.CritterDao
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.CritterMood
import com.critterfarm.data.local.DailySummaryLogDao
import com.critterfarm.data.local.DailySummaryLogEntity
import com.critterfarm.data.local.DecorPlacementDao
import com.critterfarm.data.local.DecorPlacementEntity
import com.critterfarm.data.local.FarmInventoryDao
import com.critterfarm.data.local.FarmInventoryEntity
import com.critterfarm.data.local.QuestClaimDao
import com.critterfarm.data.local.QuestClaimEntity
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

    /** A Barn-Harmony-gated item (see [ShopItem.minHarmony]) — no amount of currency skips this. */
    data class Locked(val item: ShopItem, val requiredTier: HarmonyTier) : PurchaseResult()

    data object UnknownItem : PurchaseResult()

    data object NoInventory : PurchaseResult()
}

/** Outcome of feeding the critter a treat. */
sealed class FeedResult {
    data class Fed(val happiness: Int, val hunger: Int, val treatsLeft: Int) : FeedResult()

    data object NoTreats : FeedResult()
}

/** Outcome of trying to hatch a new critter. */
sealed class HatchResult {
    data class Hatched(val critter: CritterEntity) : HatchResult()

    /** The species' unlock requirement is not yet met — no amount of sparks changes that. */
    data class Locked(val species: Species) : HatchResult()

    data class CannotAfford(val species: Species, val shortfall: Int) : HatchResult()

    data object UnknownSpecies : HatchResult()

    data object NoInventory : HatchResult()
}

/** Outcome of trying to evolve the active critter. */
sealed class EvolveResult {
    data class Evolved(val critter: CritterEntity, val newStage: Int) : EvolveResult()

    /** The consistency gate (level + goal days) is not yet met — sparks would not have helped. */
    data class NotReady(val requirement: EvolutionRequirement) : EvolveResult()

    data class CannotAfford(val shortfall: Int) : EvolveResult()
}

/** Outcome of tapping Claim on a daily quest. */
sealed class QuestClaimResult {
    data class Claimed(val quest: Quest, val coinsEarned: Int, val treatsEarned: Int) : QuestClaimResult()

    /** Idempotent, exactly like the Daily Turn chest — a second tap pays nothing extra. */
    data object AlreadyClaimed : QuestClaimResult()

    /** The quest's target isn't met yet — nothing to pay out. */
    data object NotComplete : QuestClaimResult()

    data object UnknownQuest : QuestClaimResult()

    data object NoInventory : QuestClaimResult()
}

/** Outcome of tapping Claim on a challenge. */
sealed class ChallengeClaimResult {
    data class Claimed(
        val progress: ChallengeProgress,
        val coins: Int,
        val treats: Int,
        val sparks: Int,
    ) : ChallengeClaimResult()

    /** Idempotent per (period, challenge) — a second tap pays nothing extra. */
    data object AlreadyClaimed : ChallengeClaimResult()

    /** The target isn't met yet — nothing to pay out. */
    data class NotComplete(val progress: ChallengeProgress) : ChallengeClaimResult()

    data object UnknownChallenge : ChallengeClaimResult()
}

/** Outcome of trying to repair a broken streak. */
sealed class RepairResult {
    data class Repaired(val option: RepairOption) : RepairResult()

    data class NotAvailable(val reason: String) : RepairResult()

    data class CannotAfford(val shortfall: Int) : RepairResult()
}

/**
 * The single source of truth for game state. Wraps the Room DAOs and applies [GameRules] so
 * callers (view models) never touch reward math or persistence directly.
 */
class GameRepository(
    private val critterDao: CritterDao,
    private val inventoryDao: FarmInventoryDao,
    private val dailySummaryLogDao: DailySummaryLogDao,
    private val questClaimDao: QuestClaimDao,
    private val decorPlacementDao: DecorPlacementDao,
    private val challengeClaimDao: ChallengeClaimDao,
) {
    /** The active critter — the one shown on the farm. Exactly one row is ever active. */
    val critter: Flow<CritterEntity?> = critterDao.observeActive()
    val inventory: Flow<FarmInventoryEntity?> =
        inventoryDao.observeInventory(FarmInventoryEntity.SINGLETON_ID)
    val dailyLogs: Flow<List<DailySummaryLogEntity>> = dailySummaryLogDao.observeAll()

    /** Where every decoration currently stands, keyed by cell. */
    val decorPlacements: Flow<List<DecorPlacementEntity>> = decorPlacementDao.observeAll()

    fun observeQuestClaims(date: LocalDate): Flow<List<QuestClaimEntity>> =
        questClaimDao.observeForDate(date.toString())

    fun observeChallengeClaims(): Flow<List<ChallengeClaimEntity>> = challengeClaimDao.observeAll()

    /** Every critter in the barn, owned or not yet hatched has no row — this is "owned only". */
    fun observeAllCritters(): Flow<List<CritterEntity>> = critterDao.observeAll()

    fun observeDailyLog(date: LocalDate): Flow<DailySummaryLogEntity?> =
        dailySummaryLogDao.observeByDate(date.toString())

    /** Watermark for the 48h catch-up: the latest sync timestamp recorded across all days. */
    suspend fun getLastSyncedAt(): Instant? =
        dailySummaryLogDao.getMaxSyncedAt()?.let { Instant.ofEpochMilli(it) }

    /** Seeds "Sprout the Blob" and an empty inventory on first launch. Safe to call repeatedly. */
    suspend fun ensureInitialGameState() {
        if (critterDao.getAll().isEmpty()) {
            val now = System.currentTimeMillis()
            critterDao.insert(
                CritterEntity(
                    name = STARTER_CRITTER_NAME,
                    species = SpeciesCatalog.BLOB.key,
                    stage = 0,
                    xp = 0,
                    level = 1,
                    happiness = STARTER_HAPPINESS,
                    hunger = STARTER_HUNGER,
                    mood = CritterMood.BOUNCING_HAPPY,
                    lastFedAt = now,
                    createdAt = now,
                    isActive = true,
                    hatchedAt = now,
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

        critterDao.getActive()?.let { critter ->
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

    // --------------------------------------------------------------------------- quests ----

    /**
     * Pays out one of today's three quests. Idempotent per (day, quest) via the
     * [QuestClaimEntity] composite key — a repeat tap returns [QuestClaimResult.AlreadyClaimed]
     * rather than paying twice. A quest whose target isn't actually met yet is refused outright,
     * so there is no way to bank a reward the day's log doesn't back up.
     */
    suspend fun claimQuest(date: LocalDate, questId: String): QuestClaimResult {
        val quest = QuestCatalog.ALL.firstOrNull { it.id == questId } ?: return QuestClaimResult.UnknownQuest
        val dateKey = date.toString()
        if (questClaimDao.getClaim(dateKey, questId) != null) return QuestClaimResult.AlreadyClaimed

        val log = dailySummaryLogDao.getByDate(dateKey)
        if (!QuestRules.isComplete(quest, log)) return QuestClaimResult.NotComplete

        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return QuestClaimResult.NoInventory
        inventoryDao.upsert(
            inventory.copy(
                coins = inventory.coins + quest.rewardCoins,
                treats = inventory.treats + quest.rewardTreats,
            ),
        )
        questClaimDao.insert(QuestClaimEntity(date = dateKey, questId = questId, claimedAt = System.currentTimeMillis()))
        return QuestClaimResult.Claimed(quest, quest.rewardCoins, quest.rewardTreats)
    }

    // ----------------------------------------------------------------------- challenges ----

    /**
     * Pays out a weekly/monthly/event challenge. Idempotent per (period, challenge) via
     * [ChallengeClaimEntity]'s composite key — exactly the [claimQuest] pattern, just keyed to a
     * wider window. A challenge whose target isn't met yet is refused outright, so nothing can be
     * banked ahead of the logs that back it up.
     */
    suspend fun claimChallenge(spec: ChallengeSpec, today: LocalDate): ChallengeClaimResult {
        if (ChallengeCatalog.byId(spec.id) == null) return ChallengeClaimResult.UnknownChallenge
        val logs = dailySummaryLogDao.getAll()
        val progress = ChallengeRules.progressFor(spec, logs, today)

        if (challengeClaimDao.getClaim(progress.periodKey, spec.id) != null) {
            return ChallengeClaimResult.AlreadyClaimed
        }
        if (!progress.completed) return ChallengeClaimResult.NotComplete(progress)

        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: FarmInventoryEntity.empty()
        inventoryDao.upsert(
            inventory.copy(
                coins = inventory.coins + spec.rewardCoins,
                treats = inventory.treats + spec.rewardTreats,
                manaSparks = inventory.manaSparks + spec.rewardSparks,
            ),
        )
        challengeClaimDao.insert(
            ChallengeClaimEntity(
                periodKey = progress.periodKey,
                challengeId = spec.id,
                claimedAt = System.currentTimeMillis(),
            ),
        )
        return ChallengeClaimResult.Claimed(progress, spec.rewardCoins, spec.rewardTreats, spec.rewardSparks)
    }

    // ------------------------------------------------------------------- streak repair ----

    /**
     * Buys back the chain after exactly one missed day. Spends coins and marks the gap as
     * covered — nothing else changes. In particular this never touches [DailySummaryLogEntity] or
     * pays XP/coins/treats/sparks for the missed day: the guardrail is that a repair can only ever
     * cost coins, never manufacture a reward the day itself did not earn.
     */
    suspend fun repairStreak(today: LocalDate): RepairResult {
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return RepairResult.NotAvailable("No farm yet.")

        val missedDate = StreakRepairRules.eligibleGap(
            streakDays = inventory.claimStreak,
            lastClaimedDate = inventory.lastClaimedDate,
            lastRepairedGapDate = inventory.lastRepairedGapDate,
            today = today,
        ) ?: return RepairResult.NotAvailable("No single missed day to repair right now.")

        val cost = StreakRepairRules.repairCost(inventory.claimStreak)
        if (inventory.coins < cost) return RepairResult.CannotAfford(cost - inventory.coins)

        val option = StreakRepairRules.canRepair(
            streakDays = inventory.claimStreak,
            lastClaimedDate = inventory.lastClaimedDate,
            lastRepairedGapDate = inventory.lastRepairedGapDate,
            today = today,
            coins = inventory.coins,
        ) ?: return RepairResult.NotAvailable("No single missed day to repair right now.")

        inventoryDao.upsert(
            inventory.copy(
                coins = option.coinsAfter,
                lastClaimedDate = missedDate.toString(),
                lastRepairedGapDate = missedDate.toString(),
            ),
        )
        return RepairResult.Repaired(option)
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
        if (item.minHarmony > 0) {
            val harmony = HarmonyRules.harmony(critterDao.getAll())
            if (harmony < item.minHarmony) {
                return PurchaseResult.Locked(item, HarmonyRules.tierFor(item.minHarmony))
            }
        }
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

        val critter = critterDao.getActive() ?: return FeedResult.NoTreats
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

    // -------------------------------------------------------------------------- the barn ----

    /**
     * Hatches a new critter of [speciesKey]. Locked species can never be hatched, no matter the
     * sparks on hand — the unlock check runs before the affordability check on purpose. The new
     * critter starts at the same friendly baseline as the very first critter, and only becomes
     * active if the barn was empty (never bumps whatever's currently on the farm).
     */
    suspend fun hatchCritter(speciesKey: String, name: String?): HatchResult {
        val species = SpeciesCatalog.bySpecies(speciesKey) ?: return HatchResult.UnknownSpecies
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return HatchResult.NoInventory
        val logs = dailySummaryLogDao.getAll()

        val harmony = HarmonyRules.harmony(critterDao.getAll())
        if (!HatchRules.isUnlocked(species, logs, harmony)) return HatchResult.Locked(species)
        if (!HatchRules.canAfford(species, inventory.manaSparks)) {
            return HatchResult.CannotAfford(species, HatchRules.shortfall(species, inventory.manaSparks))
        }

        val isFirstCritter = critterDao.getAll().isEmpty()
        val now = System.currentTimeMillis()
        val resolvedName = name?.trim()?.take(MAX_NAME_LENGTH)?.takeIf { it.isNotBlank() }
            ?: species.displayName
        val newCritter = CritterEntity(
            name = resolvedName,
            species = species.key,
            stage = 0,
            xp = 0,
            level = 1,
            happiness = STARTER_HAPPINESS,
            hunger = STARTER_HUNGER,
            mood = CritterMood.BOUNCING_HAPPY,
            lastFedAt = now,
            createdAt = now,
            isActive = isFirstCritter,
            hatchedAt = now,
        )
        val id = critterDao.insert(newCritter)
        inventoryDao.upsert(inventory.copy(manaSparks = inventory.manaSparks - species.hatchCostSparks))
        return HatchResult.Hatched(newCritter.copy(id = id))
    }

    /**
     * Evolves the active critter to its next stage. The consistency gate
     * ([EvolutionRequirement.behaviorMet]) is checked before affordability, so a player who is
     * ready but short on sparks sees [EvolveResult.CannotAfford] rather than being told to keep
     * training — the training part is already done.
     */
    suspend fun evolveActiveCritter(): EvolveResult {
        val critter = critterDao.getActive()
            ?: return EvolveResult.NotReady(
                EvolutionRequirement(
                    stage = 1,
                    minLevel = 0,
                    minGoalDays = 0,
                    sparkCost = 0,
                    behaviorMet = false,
                    met = false,
                    progressText = "No critter on the farm yet.",
                ),
            )
        val nextStage = critter.stage + 1
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return EvolveResult.CannotAfford(0)
        val logs = dailySummaryLogDao.getAll()
        val requirement = EvolutionRules.requirementFor(nextStage, logs, critter, inventory.manaSparks)

        if (!requirement.behaviorMet) return EvolveResult.NotReady(requirement)
        if (inventory.manaSparks < requirement.sparkCost) {
            return EvolveResult.CannotAfford(requirement.sparkCost - inventory.manaSparks)
        }

        val evolved = critter.copy(stage = nextStage)
        critterDao.update(evolved)
        inventoryDao.upsert(inventory.copy(manaSparks = inventory.manaSparks - requirement.sparkCost))
        return EvolveResult.Evolved(evolved, nextStage)
    }

    /** Makes [id] the one critter shown on the farm; every other critter is cleared. */
    suspend fun setActiveCritter(id: Long) {
        critterDao.setActiveExclusive(id)
    }

    /** Renames a critter. Blank (after trim) names are rejected rather than saved empty. */
    suspend fun renameCritter(id: Long, name: String): Boolean {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isBlank()) return false
        val critter = critterDao.getById(id) ?: return false
        critterDao.update(critter.copy(name = trimmed))
        return true
    }

    /**
     * Places a decoration: **buying is placing.** The price is charged per copy, coins come out of
     * the same purse the hats use, and the cell is claimed in the same transaction so a failure
     * cannot leave the player charged for something that is not on their farm.
     */
    suspend fun placeDecor(itemId: String, cellIndex: Int, today: LocalDate = LocalDate.now()): PlaceResult {
        val inventory = inventoryDao.getInventory(FarmInventoryEntity.SINGLETON_ID)
            ?: return PlaceResult.UnknownItem
        val occupied = decorPlacementDao.getAll().associate { it.cellIndex to it.decorId }
        val extraRows = HarmonyRules.tierFor(HarmonyRules.harmony(critterDao.getAll())).bonuses.decorRows
        val checked = PlacementRules.validate(
            itemId,
            cellIndex,
            inventory.coins,
            occupied,
            today,
            DecorCatalog.cellCount(extraRows),
        )
        if (checked !is PlaceResult.Placed) return checked

        inventoryDao.upsert(inventory.copy(coins = checked.coinsLeft))
        decorPlacementDao.upsert(
            DecorPlacementEntity(
                cellIndex = cellIndex,
                decorId = itemId,
                placedAt = System.currentTimeMillis(),
            ),
        )
        return checked
    }

    /**
     * Clears a square. No refund — the shop copy says so — because a half-priced farm rearranging
     * itself would make the coin cost meaningless, and the scene is meant to be a permanent marker
     * of the walking you actually did.
     */
    suspend fun removeDecor(cellIndex: Int): Boolean {
        val extraRows = HarmonyRules.tierFor(HarmonyRules.harmony(critterDao.getAll())).bonuses.decorRows
        if (!PlacementRules.isInRange(cellIndex, DecorCatalog.cellCount(extraRows))) return false
        decorPlacementDao.removeAt(cellIndex)
        return true
    }

    private suspend fun refreshMood(date: LocalDate) {
        val log = dailySummaryLogDao.getByDate(date.toString()) ?: return
        val critter = critterDao.getActive() ?: return
        critterDao.update(
            critter.copy(
                mood = GameRules.deriveMood(critter, log.steps, log.workouts, log.chestClaimed),
            ),
        )
    }

    companion object {
        const val STARTER_CRITTER_NAME = "Sprout the Blob"
        const val STARTER_HAPPINESS = 80
        const val STARTER_HUNGER = 30
        const val MAX_NAME_LENGTH = 16
    }
}
