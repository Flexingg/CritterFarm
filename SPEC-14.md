# v1.4 — Quests, badges and per-zone streaks

Read this whole file before writing code. Work in this repo. **Do not break the existing tests**
(65 at v1.2, plus whatever v1.3 added). Keep the app's voice: warm, playful, never scolding.

The job of this release is *reasons to come back today and every day*: short-term goals (quests),
medium-term proof of progress (streaks) and long-term trophies (badges). All three must be
computed from data the app already stores — no new Health Connect permissions.

## 1. Daily quests

`data/QuestCatalog.kt` — a pool of quests, each defined as data:

```
data class Quest(
    val id: String,            // stable, e.g. "steps_8k"
    val text: String,          // "Walk 8,000 steps"
    val emoji: String,
    val rewardCoins: Int,
    val rewardTreats: Int,     // one of the two is usually 0; quests pay the currency they fit
    val metric: Metric,        // reuse the existing Metric enum
    val target: Double,
    val compare: QuestCompare, // AT_LEAST (most) or AT_MOST (e.g. "keep intake reasonable")
)
```

Provide **at least 12** quests spanning the six metrics, with rewards roughly proportional to
difficulty (a 10k-step day is worth more than a weigh-in). Suggested set: 8k steps, 10k steps,
12k steps, log 64 fl oz, log 100 fl oz, one workout, two workouts, sleep 7h, sleep 8h, a safe
deficit day, log a weigh-in, hit four of six targets.

**Rotation must be pure and deterministic:** `QuestsForDay.forDate(date: LocalDate, quests: List<Quest>): List<Quest>`
returns **3** quests chosen from the pool using the date as the seed (e.g. a stable hash of the
epoch day), so the same day always yields the same three quests, consecutive days differ, and
there is a test proving both properties. Avoid `Random()` without a seed.

Progress comes from today's `DailySummaryLogEntity` (`Metric.valueIn(log)`), so no progress is
stored — only claims are.

`data/local/QuestClaimEntity.kt` — `(date: String, questId: String, claimedAt: Long)` with a
composite primary key of `(date, questId)`. Add the DAO, and a **MIGRATION_3_4** that creates the
new table (additive; never destructive). `GameRepository.claimQuest(date, questId): QuestClaimResult`
must be idempotent per (day, quest) exactly like the Daily Turn chest, and must refuse to pay a
quest whose target is not actually met (`NotComplete`).

## 2. Per-zone streaks

`data/StreakRules.kt` (pure):
- `currentStreak(logs, metric): Int` — consecutive days up to *today or yesterday* where that
  metric's target was met. Today counts only if it is met; a streak that ended yesterday is still
  alive today (it just has not been extended yet) — this distinction must be tested.
- `bestStreak(logs, metric): Int` — the longest run in history.
- `streakFor(logs, metric): MetricStreak(current, best, daysToNextBadge?)`.

Show per-zone streaks in the existing "Today on the farm" card as a small "🔥 4-day" chip on each
row when `current >= 2`. Do not restructure the card otherwise — it was just redesigned for
clarity and must stay numbers-first.

## 3. Badges (achievements)

`data/BadgeCatalog.kt` + `data/BadgeRules.kt`, pure:

```
data class Badge(val id: String, val name: String, val emoji: String, val blurb: String, val tier: BadgeTier)
data class BadgeProgress(val badge: Badge, val unlocked: Boolean, val current: Int, val target: Int) {
    val progressText: String get() = "$current / $target"
}
object BadgeRules { fun progressFor(logs: List<DailySummaryLogEntity>, badge: Badge): BadgeProgress }
```

Provide **at least 16** badges across tiers (BRONZE / SILVER / GOLD), each with a concrete,
computable condition over the stored logs. Examples: first 10k-step day; 5 perfect days; 100k
lifetime steps; 250k lifetime steps; 10 workouts; 25 workouts; a 7-day hydration streak; a 7-day
steps streak; 10 weigh-ins; 5 days at 8h+ sleep; a 30-day claim streak; 50 days logged; a week
with every day logged; 20 safe-deficit days; 3 badge tiers of a "consistent month" family.

Unlocked badges are permanent — derive unlocked state from history, and expose
`unlockedCount(logs)` for a header ("7 / 18 badges"). Cards for locked badges must show progress
so the next one always feels close; that is the entire point.

## 4. UI

- **Quests card on the farm**: the three quests for today with progress bars, reward icons and a
  Claim button per completed-unclaimed quest (disabled/`Met` states otherwise). A completed quest
  shows ✓. This card sits directly under the streak card.
- **Badges screen** (`ui/badges/BadgesScreen.kt` + `BadgesViewModel.kt`, MVI like the others):
  header with unlocked count, a grid of badge cards (emoji, name, blurb, progress bar, tier
  colour), locked vs unlocked visually distinct, newest unlock celebrated at the top.
- Navigation: add a `badges` route, reachable from the farm (a "Badges" button next to
  Shop / History / Barn) and from the History screen.
- All loading / empty / zero-permission states need friendly copy. With zero permissions every
  quest shows 0 progress and every badge 0/N — the copy must stay encouraging.

## 5. Tests (`app/src/test/java/com/critterfarm/data/`)

- `QuestRotationTest` — same date → same 3 quests; three consecutive dates are not all identical;
  exactly 3 returned; every quest id appears in the pool without duplicates; rewards are positive.
- `QuestProgressTest` — completion and progress come from the log per metric; `AT_MOST` quests are
  inverted correctly; a quest is not complete when the metric has no data.
- `StreakRulesTest` — current streak counts consecutive met days; a streak ending yesterday is
  still current; a gap of 2+ days resets it; best streak finds the longest historical run;
  per-metric independence (a water streak does not care about steps).
- `BadgeRulesTest` — each badge's threshold is met exactly at the target (not one above or below),
  progress never exceeds the target, a badge unlocked by an old day stays unlocked, and
  `unlockedCount` agrees with the individual results.

## 6. Verify before reporting success (do not skip)

```
HOME=/home/hermes ./gradlew :app:assembleDebug
HOME=/home/hermes ./gradlew :app:testDebugUnitTest
```
Both must pass and every pre-existing test must stay green. Report files created/changed, both
command results, the quest/badge counts and thresholds you chose, and anything you compromised on.
An honest gap beats a silent stub.
