# v1.6 — Challenges, Events & Second Chances

Read this whole file before writing code. Work in this repo. **Do not break any of the 150 existing
tests.** Keep the app's voice: warm, playful, never scolding. No new Health Connect permissions, no
network calls, no new dangerous permissions. Everything stays local.

The app already covers **today** (quests, the chest, streaks) and **all time** (badges, history).
The missing middle is a **medium-term goal with a deadline you can watch run down** — that is what
this release adds, plus one targeted mercy rule.

Design principle for the whole release: **challenges are the engine, and a window is just a
parameter.** Weekly, monthly and seasonal-event goals are the same machinery with a different date
range, so build it once and test it once.

## 1. Challenge engine

`data/ChallengeSpec.kt`:

```
enum class ChallengeWindow { WEEKLY, MONTHLY, EVENT }

enum class ChallengeGoal {
    TOTAL,   // sum the metric over the window, e.g. "4 workouts this week"
    DAYS,    // count days where the metric met its goal, e.g. "5 days at your water goal"
    ACTIVE_DAYS, // count days with any activity logged at all
}

data class ChallengeSpec(
    val id: String,              // stable, e.g. "weekly_workouts_4"
    val title: String,           // "Four trips to the gym barn"
    val emoji: String,
    val blurb: String,
    val window: ChallengeWindow,
    val metric: Metric,          // reuse the existing enum
    val goal: ChallengeGoal,
    val target: Double,
    val rewardCoins: Int,
    val rewardTreats: Int,
    val rewardSparks: Int,
    val eventId: String? = null, // set only for ChallengeWindow.EVENT
)
```

`data/ChallengeRules.kt` — **pure**:

```
data class ChallengeProgress(
    val spec: ChallengeSpec,
    val current: Double,
    val target: Double,
    val completed: Boolean,
    val daysLeft: Int,          // 0 on the final day
    val periodKey: String,      // "2026-W38" weekly, "2026-09" monthly, "harvest" for an event
) { val fraction: Float; val progressText: String }
```

- `periodKeyFor(spec, today: LocalDate): String` — weekly uses ISO week (`2026-W38`), monthly uses
  `yyyy-MM`, events use the event id. A claim is keyed by `(periodKey, challengeId)`, so **the same
  challenge is claimable again next week and cannot be claimed twice in one period.**
- `windowFor(spec, today): Pair<LocalDate, LocalDate>` — weekly is Monday-start (the ISO week, so
  the period key and the range agree), monthly is the calendar month, an event uses its own range.
- `progressFor(spec, logs, today): ChallengeProgress` — `TOTAL` sums the metric across logged days
  in the window, `DAYS` counts days where that metric met its `GameGoals` target, `ACTIVE_DAYS`
  counts logged days with any non-zero activity. Never counts a day twice, never counts a day
  outside the window, and `daysLeft` is inclusive of today.
- `TOTAL` must ignore days with no data rather than treating them as zero in a way that inflates a
  "days" count — keep the two goals clearly separate.

`data/ChallengeCatalog.kt` — at least **10** specs: several weekly (a workout count, an active-days
count, a steps total, a water-days count, a sleep-days count), several monthly (a bigger steps
total, workouts, goal days, weigh-in days). Rewards scale with difficulty. **No challenge may ask
the player to eat less or chase a bigger deficit** — a monthly "safe deficit days" style goal is
acceptable only through the `DAYS` goal against the existing deficit target, never a larger one.

`data/local/ChallengeClaimEntity.kt` + DAO — `(periodKey, challengeId, claimedAt)`, composite primary
key. `MIGRATION_5_6` creates it (additive; never destructive), chained with the earlier migrations.

`GameRepository`:
- `observeChallengeClaims(): Flow<List<ChallengeClaimEntity>>`
- `claimChallenge(spec, today): ChallengeClaimResult` — `Claimed(progress, coins, treats, sparks)` /
  `AlreadyClaimed` / `NotComplete(progress)` / `UnknownChallenge`. Idempotent per (period, id),
  paying the rewards into the same purse the rest of the game uses.

## 2. Seasonal events

`data/EventCatalog.kt`:

```
data class FarmEvent(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val start: LocalDate,
    val end: LocalDate,       // inclusive
)
```

- **At least 4** events spread across the calendar, each roughly 3-6 weeks long. Make sure **one is
  active on 2026-09-19** (the date the emulator is seeded for) so it can be seen.
- `object EventCatalog { val ALL: List<FarmEvent>; fun activeOn(date): FarmEvent?; fun isActive(id, date): Boolean; fun byId(id): FarmEvent? }`

**Event-exclusive decorations:** extend `DecorItem` with `eventId: String? = null`, and mark **at
least 3** existing decorations as event-exclusive (do not change their prices). Add
`DecorCatalog.availableOn(date: LocalDate): List<DecorItem>` that returns normal items plus the
exclusive ones **only while their event is active**. Outside the window an event decoration is not in
the catalogue at all, which is what makes it scarce — no new ownership table, no new placement
semantics, and `PlacementRules.validate` must reject an out-of-season event item even if its id is
passed directly (check the window inside the rules, not only in the UI).

**One event challenge:** the active event's goal is a `ChallengeSpec` with `window = EVENT` and
`eventId` set, so the same claim machinery pays it once per event.

## 3. Streak repair — a second chance, honestly priced

The streak multiplier is the strongest hook in the game, and losing it to a single bad day is the
fastest way to lose a player. Give them a way back that is **clearly labelled as protecting the
chain, not replacing the work.**

- Extend `FarmInventoryEntity` with `lastRepairedGapDate: String?` (nullable, additive) and add it to
  `MIGRATION_5_6` as a column on `farm_inventory` (an `ALTER TABLE ... ADD COLUMN` with no default
  needed for a nullable column).
- `data/StreakRepairRules.kt` — **pure**:
  - `gapDays(claimStreak, lastClaimedDate, today): Long` — how many days were missed.
  - `repairCost(streakDays: Int): Int` — e.g. `100 + 25 * streakDays`, capped at 1000 coins.
  - `canRepair(streakDays, lastClaimedDate, lastRepairedGapDate, today, coins): RepairOption?` —
    available **only when exactly one day was missed** (a missed week is not one bad day), the chain
    was actually alive before the gap (`streakDays > 0`), the gap has not already been repaired
    (`lastRepairedGapDate` must not equal the missed date), and the player can afford it.
  - Result: `RepairOption(cost, missedDate, coinsAfter, streakDaysRestored)`.
- `GameRepository.repairStreak(today): RepairResult` — `Repaired(option)` /
  `NotAvailable(reason)` / `CannotAfford(shortfall)`. On success: spend the coins, set
  `lastRepairedGapDate` to the missed date, and restore the claim streak to its pre-gap value **with
  the missed day marked as covered** so the next claim continues the chain.
- **The critical guardrail:** repairing pays **no retroactive rewards** — no coins, treats, sparks or
  XP for the missed day. It restores the *chain*, never the *work*. Say so in the UI copy, and write
  a test asserting the reward totals do not move.
- UI: when a repair is available, a card on the farm: what happened, the price, and two buttons —
  **Repair the streak** and **Let it go**. Never guilt the player for choosing the second one.

## 4. UI

- **Challenges screen** (`ui/challenges/ChallengesScreen.kt` + ViewModel, MVI like the others): the
  active event banner at the top if one is running (name, emoji, blurb, days left), then the weekly
  challenges with a countdown and progress bars, then the monthly ones. Completed-and-unclaimed shows
  a Claim button; completed-and-claimed shows ✓ claimed.
- **Farm additions:** a **Challenges** button in the action row (next to Badges / Decor / Week), the
  active-event banner (only when an event is running), and the streak-repair card when available.
- **Shop:** event-exclusive decorations appear in the Decor screen only during their window, clearly
  grouped and labelled with the event name and how long they are available.
- Navigation: add a `challenges` route.
- Every loading / empty / zero-permission state needs friendly copy. With zero permissions every
  challenge shows 0 progress — keep it encouraging.

## 5. Tests (`app/src/test/java/com/critterfarm/data/`)

- `ChallengeRulesTest` — window boundaries (a day before the start and a day after the end are
  excluded), ISO weekly period keys are stable and differ week to week, monthly keys differ month to
  month, `TOTAL` sums but `DAYS` counts days, a day can never be counted twice, `daysLeft` is
  inclusive of today and is 0 on the last day, `fraction` is clamped to 0..1, and an empty log set
  gives zero progress rather than a crash.
- `EventCatalogTest` — at least 4 events, unique ids, every event's `end` is after its `start`, an
  event is active on 2026-09-19, `activeOn` returns nothing outside every window, and event ids
  referenced by decor and by challenge specs all exist.
- `EventExclusivityTest` — `DecorCatalog.availableOn` includes an exclusive item inside its window
  and **excludes** it outside, normal items are always available, and `PlacementRules.validate`
  refuses an out-of-season event item even with unlimited coins.
- `StreakRepairTest` — a single missed day is repairable; two or more is not; a live chain (claimed
  yesterday or today) is not repairable; the same gap cannot be repaired twice; cost scales with
  streak length and is capped; an unaffordable repair reports the exact shortfall; and **repairing
  changes no reward fields** (assert the log rows' rewards are untouched).

## 6. Verify before reporting success (do not skip)

```
HOME=/home/hermes ./gradlew :app:assembleDebug
HOME=/home/hermes ./gradlew :app:testDebugUnitTest
```
Both must pass and **all 150 pre-existing tests must stay green**. Report: files created/changed,
both command results, the challenge specs and rewards you chose, the event calendar, the repair cost
formula, and anything you compromised on. An honest gap beats a silent stub.