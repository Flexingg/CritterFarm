# v1.7 — Mythic evolutions, Barn Harmony, and a critter that learns you

Read this whole file before writing code. Work in this repo. **Do not break any of the 189 existing
tests.** Keep the app's voice: warm, playful, never scolding. No new Health Connect permissions, no
network calls, no new dangerous permissions. Everything stays local.

The guiding rule of this release, which is an extension of one the app already states:

> **Consistency gates capability; currency only buys decoration.**

A stronger critter must give the player **more game to play, never more health reward**. Nothing in
this release may increase coins/treats/sparks earned for the same real-world behaviour, and nothing
may reward eating less.

## 1. Third evolution — the Mythic stage

Evolution currently stops at stage 2. Add **stage 3**, and make it feel like a months-long summit:

| Stage | Name | Level | Consistency | Sparks |
|---|---|---|---|---|
| 0 | Hatchling | — | — | — |
| 1 | Awakened | ≥ 5 | 7 days meeting 4+ of 6 targets | 20 |
| 2 | Ascendant | ≥ 12 | 21 days meeting 4+ of 6 targets | 50 |
| **3** | **Mythic** | **≥ 25** | **60 days meeting 4+ of 6 targets** | **120** |

- Put those numbers in the existing evolution rules as data where they already live, and keep the
  existing `EvolutionRequirement` shape working — the requirement must still expose
  `(minLevel, minGoalDays, sparkCost, met, progressText)` so the barn's progress line
  (`Stage 3 · Level 12/25 · 40/60 goal days · 120 sparks`) keeps working unchanged.
- **Stage names** belong in the species/evolution catalogue as a single lookup
  (`StageNames.forStage(stage)` or similar), used by the barn, the farm header and the evolution
  dialog. The barn should read `Lv 26 · Mythic · 🎉` where it currently reads `Lv 26 · Stage 3 · 🎉`.
- **Existing tests must keep passing**: a stage-2 critter at level 12 with 21 goal days must still
  report stage 2, and a stage-3 critter must report `met = false` for anything higher (there is no
  stage 4 — the rules must clamp rather than throw).

### Stage 3 art (this is the payoff — it must be visible)
`CritterCanvas` already renders species + stage + hat + mood. Extend it so stage 3 is unmistakable:
- **Larger silhouette** than stage 2 (a clear step up, not a nudge).
- **A visible aura** — concentric rings or floating runes drawn *behind* the body, gently animated
  (the existing `cycle`/infinite-transition driver is fine). It must be cheap: precompute any
  particle/ring geometry, allocate nothing in the draw loop, and draw nothing extra for stages 0-2.
- **A per-species flourish** — e.g. drake wings spread, axolotl gills glowing, sloth draped over a
  branch, phoenix tail feathers. Distinct per species, same flat vector style as everything else.
- **Hats must still render unclipped** for the largest species at stage 3. The v1.2 clipping bug and
  the v1.3 geometry re-proportioning are both covered by tests/geometry rules — do not regress them.
  Verify the tallest hat (party hat / crown / halo) on the tallest species at stage 3 in the
  emulator, not just in your head.

## 2. Barn Harmony — the purpose of getting stronger

Every owned critter contributes to one derived number:

```
harmony(critters) = critters.sumOf { it.stage + 1 }   // a lone stage-0 starter = 1
```

So **breadth (more species) and depth (deeper evolutions) both count**, and every critter you own
has a purpose rather than only the active one.

`data/HarmonyRules.kt` — **pure**, no Android imports:

```
data class HarmonyBonuses(
    val extraDailyQuests: Int = 0,        // 3 quests/day becomes 3 + this
    val decorRows: Int = 0,               // extra rows on the 6x4 farm scene
    val unlockedSpeciesIds: Set<String> = emptySet(),
    val unlockedHatIds: Set<String> = emptySet(),
)

data class HarmonyTier(
    val id: String, val name: String, val emoji: String, val blurb: String,
    val minHarmony: Int, val bonuses: HarmonyBonuses,
)

object HarmonyRules {
    val TIERS: List<HarmonyTier>   // ordered, ascending minHarmony
    fun harmony(critters: List<CritterEntity>): Int
    fun tierFor(harmony: Int): HarmonyTier       // the highest tier whose minHarmony is met
    fun nextTier(harmony: Int): HarmonyTier?     // null once the top tier is reached
    fun progressToNext(harmony: Int): String     // e.g. "5 / 6 harmony" for the next tier
}
```

Tiers:

| Harmony | id | Name | Unlocks |
|---|---|---|---|
| 1 | `quiet` | Quiet Barn 🏡 | base game (no bonuses) |
| 3 | `working` | Working Farm 🌾 | **+1 daily quest** |
| 6 | `thriving` | Thriving Farm 🌻 | **+1 decor row** (6x4 becomes 6x5) |
| 10 | `mythic` | Mythic Farm ✨ | unlocks the **Aurora Phoenix** species and the **Aurora Crown** hat |

- `tierFor` must clamp sensibly: harmony 0 (no critters — impossible in practice but testable) returns
  the first tier.
- Harmony is **derived, never stored** — no schema change for it.

### Wiring the bonuses honestly
- **Daily quests**: `QuestsForDay.forDate(date, quests)` currently returns 3. Give it an optional
  count parameter defaulting to 3 so every existing test keeps passing, and have the farm pass
  `3 + tier.bonuses.extraDailyQuests`. A test must prove that 4 quests are still distinct, stable
  for a given date, and different on consecutive days.
- **Decor rows**: `FarmScene` currently draws `DecorCatalog.GRID_ROWS` (4). Add an optional rows
  parameter defaulting to the existing constant, so the base grid is unchanged for existing tests and
  the tier adds a row. `PlacementRules.isInRange` must take the allowed cell count (defaulting to the
  existing `DecorCatalog.CELL_COUNT`) so out-of-range behaviour for the base grid is **unchanged**
  and a grown grid accepts the new cells. Add `DecorCatalog.cellCount(extraRows: Int)`.
- **Aurora Phoenix**: a seventh species, `phoenix`, hatch cost **120 sparks**, with its own colours,
  emoji, blurb and stage art, plus an unlock requirement that is **not** satisfiable by logs alone —
  it must additionally require the Mythic harmony tier. Extend the species unlock model with an
  optional harmony requirement rather than special-casing the phoenix in the UI, and make sure a
  locked phoenix cannot be hatched even with unlimited sparks (test it).
- **Aurora Crown**: a new hat in the shop, coins, only purchasable once the Mythic tier is reached.
  Reuse the existing hat/shop plumbing; `GameEconomyTest` must stay green (this is still decoration).

## 3. The critter learns you — insight lines

`data/InsightRules.kt` — **pure**:

```
data class Insight(val text: String, val fromStage: Int)
object InsightRules {
    fun insightFor(stage: Int, logs: List<DailySummaryLogEntity>, today: LocalDate): Insight
}
```

- The depth of the observation scales with the **active critter's stage**:
  - stage 0 — cheerful, says nothing specific ("Sprout thinks today is a good day to wander.")
  - stage 1 — one fact about **today** ("6,412 steps so far — 3,588 to go.")
  - stage 2 — a **7-day pattern** ("You've hit your water goal 5 of the last 7 days.")
  - stage 3 — a **cross-metric observation** ("Your sleep is longest on days you walk 8,000+.")
- Deterministic for a given (stage, logs, today) — no randomness — so it is testable.
- Must degrade kindly with no data (empty logs, zero permissions): return an encouraging line, never
  an error, never a zero-flavoured scold.
- **Hard guardrail:** insights are observations, never instructions about food. The copy must never
  contain "eat", "less", "cut", "calorie deficit" as advice, "should", "must", "cheat", "guilt",
  "fail", or any weight-loss framing. Add a test that asserts this over **every** branch reachable at
  every stage, including the empty-data branches. Weight may only ever be mentioned as
  "lowest weigh-in on record" style fact, and it is fine to not mention it at all.
- UI: one line on the farm under the critter card, prefixed with the critter's name (e.g.
  `Sprout says: …`). Keep it small and quiet — it is a companion, not a coach.

## 4. UI

- **Farm**: the insight line under the critter; a harmony chip showing the current tier name and
  progress toward the next (`Thriving Farm · 7 / 10 harmony`); the stage NAME in the header instead of
  "Stage N".
- **Barn**: stage names, the stage-3 requirement line, and a **Harmony card** listing the tiers, which
  are unlocked, and what the next one gives — so the player can see exactly what another evolution or
  another species buys them.
- **Shop**: the Aurora Crown appears only once the Mythic tier is reached (with a lock note naming the
  requirement, like the other locked items). Event exclusivity from v1.6 must keep working.
- **Species grid (hatch section)**: the Aurora Phoenix shows a lock note naming the Mythic-tier
  requirement, and cannot be hatched while locked.
- Everything must work with **zero Health Connect permissions**: harmony and tiers still work (they
  depend on critters and stages, not health data), insights fall back to the friendly stage-0 line.

## 5. Tests (`app/src/test/java/com/critterfarm/data/`)

- `HarmonyRulesTest` — a single stage-0 critter is harmony 1; harmony sums `stage + 1` across critters
  (two stage-2 critters = 6); `tierFor` returns the right tier at exactly each threshold and one below
  it; harmony 0 clamps to the first tier; `nextTier` is null at the top; `progressToNext` reads
  sensibly; tiers are ordered and have unique ids; **no tier grants any reward-multiplying bonus**
  (assert the bonus shape cannot express one — i.e. construct the expected `HarmonyBonuses` and assert
  equality for each tier).
- `MythicEvolutionTest` — stage 2 critter stays stage 2; stage 3 requires level ≥ 25, 60 goal days and
  120 sparks, and is `met` only when all three hold; the progress text names the stage; stage 3 is the
  ceiling (clamped, no crash, no stage 4 requirement).
- `InsightRulesTest` — the stage-0 line is stage-appropriate and mentions no numbers; the stage-1 line
  contains today's actual step count; the stage-2 line counts met-goal days over the **last 7 days
  only** (a day outside the window must not be counted); the stage-3 line is deterministic and
  references two different metrics; empty logs return an encouraging line at every stage; and **every
  branch of every stage passes the forbidden-word guardrail** (the test should enumerate the branches
  it can reach rather than checking one example).
- `PhoenixUnlockTest` (or fold into the species tests) — the phoenix is locked below the Mythic tier
  even with unlimited sparks, unlocked at exactly harmony 10, and its hatch cost is 120.

## 6. Verify before reporting success (do not skip)

```
HOME=/home/hermes ./gradlew :app:assembleDebug
HOME=/home/hermes ./gradlew :app:testDebugUnitTest
```
Both must pass and **all 189 pre-existing tests must stay green**. Report: files created/changed, both
command results, the exact stage-3 numbers, the harmony tiers, how you kept hats unclipped at stage 3,
and anything you compromised on. An honest gap beats a silent stub.