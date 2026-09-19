# v1.5 — Personalise, reflect and feel good

Read this whole file before writing code. Work in this repo. **Do not break any existing test.**
Keep the app's voice: warm, playful, never scolding. No new Health Connect permissions, no
network calls, no new dangerous permissions.

Three jobs: let the player **make the farm theirs** (decorations), give them a **reason to review
the week** (recap), and make every reward **feel** like one (juice: haptics + particles).

## 1. Farm decorations (buy + place)

`data/DecorCatalog.kt` — purchasable decorations, coins only (this is the cosmetic sink):

```
enum class DecorSlot { GROUND, WATER, STRUCTURE }
data class DecorItem(
    val id: String,            // "decor_pond"
    val name: String,
    val emoji: String,
    val blurb: String,
    val price: Int,            // coins
    val slot: DecorSlot,
)
object DecorCatalog { val ALL: List<DecorItem>; fun item(id: String): DecorItem? }
```

At least **10** items across the slots (pond, flower patch, apple tree, hay bale, fence, lantern,
scarecrow, bee hive, sunflowers, windmill), priced 100–1,500 coins so it is a long-term sink.

**Placement:** a fixed grid scene, e.g. **6 columns × 4 rows = 24 cells**, drawn behind and around
the critter. Persisted in a new table:

`data/local/DecorPlacementEntity.kt` — `(cellIndex: Int PRIMARY KEY, decorId: String, placedAt: Long)`.
Add `MIGRATION_4_5` creating it (additive; never destructive) and chain it with the earlier ones.

`GameRepository` additions (suspend):
- `placeDecor(itemId: String, cellIndex: Int): PlaceResult` — `Placed` / `NotOwned(item)` /
  `CannotAfford(item)` / `CellOutOfRange` / `CellOccupied(existing)`. **Owning is implied by
  placement**: buying = placing, so the purchase IS the placement (simplest honest model — say so
  in a comment). Placing a second copy of the same item in another cell must be allowed only if
  the player can still afford it, so `price` is charged per placement.
- `removeDecor(cellIndex: Int): Boolean` — clears the cell. Refunding is **not** required; if you
  do not refund, say so in the UI copy.

`ui/decor/FarmScene.kt` — a Composable drawing the 6×4 grid with the active critter
(`CritterCanvas` with its species/stage/hat) centred, placed decorations drawn in their cells, and
empty cells subtly marked. Tapping an empty cell while an item is selected places it; tapping a
filled cell offers remove. Keep it readable: decorations are emoji + a soft coloured cell, not
hand-drawn art, unless you can do art well.

Add a **Decor** section to the existing Barn Shop screen (same screen, new section: item cards with
price, owned count, and a "Place" action that switches to the scene), OR a dedicated
`ui/decor/DecorScreen.kt` with the catalog + scene if the Shop would get too long. Either is fine —
state which you chose.

## 2. Weekly recap

`data/WeeklyRecap.kt` (pure):
```
data class WeekStats(
    val startDate: LocalDate, val endDate: LocalDate,
    val daysLogged: Int, val totalSteps: Long, val totalWorkouts: Int,
    val totalWaterFlOz: Double, val totalSleepMinutes: Long,
    val metTargetDays: Int, val bestDayScore: Int,
    val coinsEarned: Int, val treatsEarned: Int, val sparksEarned: Int,
)
data class RecapDelta(val label: String, val thisWeek: String, val vsLastWeek: String, val up: Boolean)
object WeeklyRecap {
    fun statsFor(logs: List<DailySummaryLogEntity>, weekStart: LocalDate): WeekStats
    fun deltas(current: WeekStats, previous: WeekStats): List<RecapDelta>
    fun highlights(week: WeekStats): List<String>   // 2-4 friendly one-liners
}
```
- `highlights` must be **encouraging even for a poor week** (e.g. "4 of 7 days logged — that is 4
  more than zero"): never scold, never mention weight loss speed, never suggest eating less.
- `ui/recap/RecapScreen.kt` + ViewModel: a big "Your week on the farm" card with the totals,
  the deltas (▲/▼ vs last week, phrased as information not judgement) and the highlights, plus a
  **Share** button that fires a plain `ACTION_SEND` text intent (`Intent.createChooser`) with a
  short summary the player can paste anywhere. No image generation, no extra permissions.
- Reachable from the History screen (a "This week" button) and/or the farm. State which you chose.

## 3. Juice (haptics + particles)

- **Haptics**: use Compose's `LocalHapticFeedback` (no permission needed) on: claiming the Daily
  Turn chest, claiming a quest, feeding, buying/equipping, evolving, and hatching. A light tick is
  enough — do not machine-gun it.
- **Particles**: a small Canvas confetti/starburst burst for the three big moments (chest claim,
  evolution, hatch), driven by an `animateFloatAsState`/`Animatable` progress value, drawn with
  the existing palette. Keep it cheap: ~20 particles, ~900 ms, no allocation in the draw loop.
  It must not block or delay the modal.
- Respect that this is a health app: no fake urgency, no countdown pressure, no guilt copy.

## 4. Tests (`app/src/test/java/com/critterfarm/data/`)

- `DecorCatalogTest` — unique ids, every id resolves, all prices positive, all slots represented.
- `PlacementRulesTest` — cell bounds (24 cells: -1, 24 and 25 rejected), an occupied cell is
  refused, affordability in coins, and that a placement is charged per copy.
- `WeeklyRecapTest` — a week's totals sum only the days inside the week (boundaries: the day
  before and after must be excluded), deltas compute direction correctly including zero-vs-zero,
  and `highlights` returns at least one line for an **empty** week without any negative wording
  (assert the absence of words like "fail", "bad", "lazy", "should").

## 5. Verify before reporting success (do not skip)

```
HOME=/home/hermes ./gradlew :app:assembleDebug
HOME=/home/hermes ./gradlew :app:testDebugUnitTest
```
Both must pass and every pre-existing test must stay green. Report files created/changed, both
command results, the decor prices and grid size you chose, which screens you attached things to,
and anything you compromised on. An honest gap beats a silent stub.
