# v1.3 — The Collection Loop: critters, evolution and hatching

Read this whole file before writing code. Work in this repo. Everything compiles today; do not
break the existing 65 tests.

The goal is the retention loop the app is still missing: owning a *barn* of critters, and
spending the scarce currency (Mana Sparks) to hatch new ones and evolve the ones you have.
Collecting is the strongest draw a game like this can have — but it must never let money buy
health progress. Consistency gates evolution; currency is only the extra cost.

## 1. Data model

`CritterEntity` already exists (id, name, species, stage, xp, level, happiness, hunger, mood,
lastFedAt, createdAt). Add what the barn needs, keeping the migration additive:

- `isActive: Boolean` — the critter shown on the farm. Exactly one row may be active.
- `hatchedAt: Long` (millis) — when it joined the barn.

Extend the entity and bump the Room version to **3** with a `MIGRATION_2_3` that `ALTER TABLE`s
the new columns with defaults (`isActive INTEGER NOT NULL DEFAULT 0`, `hatchedAt INTEGER NOT
NULL DEFAULT 0`), and set `isActive = 1` on the existing critter so an upgrading player keeps
their pet. `addMigrations` must chain 1→2 and 2→3. Destructive migration is never acceptable.

## 2. Species catalogue (`data/SpeciesCatalog.kt`)

A species is: `key`, `displayName`, `emoji`, `blurb` (one friendly line), `hatchCostSparks`,
`primaryColor`, `accentColor`, `unlockNote` (what earns the right to hatch it).

Define six, with a real cost/difficulty ladder:

| key | name | hatch cost (Mana Sparks) | unlock requirement |
|---|---|---|---|
| `blob` | Blob | 0 | starter — always available |
| `bunny` | Pasture Bunny | 15 | any 1 workout logged in history |
| `chick` | Sunrise Chick | 25 | 3 days with the steps target met |
| `axolotl` | Pond Axolotl | 40 | 5 days with the hydration target met |
| `dragon` | Ember Drake | 60 | 10 days with the deficit target met |
| `sloth` | Cozy Sloth | 80 | 7 days with the sleep target met |

`unlockRequirement` must be a **pure function of the stored daily logs** (reuse
`HistoryRules.metTargets(log)` and `Metric`/`GameGoals`), not a hard-coded flag. A species you
have not unlocked shows exactly what unlocks it (`unlockNote`) and stays unbuyable.

## 3. Evolution

Three stages per critter (0 → 1 → 2). Evolution needs **both** a behaviour gate and Mana Sparks:

- **Stage 1** — requires level ≥ 5 AND 7 days meeting at least 4 of the 6 targets; costs 20 sparks.
- **Stage 2** — requires level ≥ 12 AND 21 days meeting at least 4 targets; costs 50 sparks.

Put these thresholds in the catalogue/rules as data, expose a pure
`EvolutionRules.requirementFor(stage, logs, critter): EvolutionRequirement` returning
`(minLevel, minGoalDays, sparkCost, met: Boolean, progressText: String)` so the UI can show
"Level 7/12 · 14/21 goal days · 50 sparks" — the *progress toward* an evolution is the hook, so
this must be visible at all times, not just when affordable.

## 4. Repository API (all suspend unless noted)

- `observeAllCritters(): Flow<List<CritterEntity>>`
- `critter: Flow<CritterEntity?>` must now be the **active** critter (keep the name).
- `hatchCritter(speciesKey: String, name: String?): HatchResult` — `Hatched` /
  `Locked(species)` / `CannotAfford(species, shortfall)` / `UnknownSpecies` / `NoInventory`.
  Spends sparks, resets that critter's xp/level/happiness/hunger to sensible starting values,
  and does **not** make it active unless it is the first critter.
- `evolveActiveCritter(): EvolveResult` — `Evolved(critter, newStage)` / `NotReady(requirement)`
  / `CannotAfford(shortfall)`. Spends sparks.
- `setActiveCritter(id: Long)` — makes exactly one critter active (single SQL `UPDATE` that
  clears the others; do it in a transaction).
- `renameCritter(id: Long, name: String)` — trim, reject blank, cap length (e.g. 16 chars).

Add the DAO queries these need (`observeAll`, `getActive`, `getById`, `clearActive`, `setActive`,
`updateStage` if useful). Name the critter at hatch time from the species if the player does not
supply one (e.g. "Pasture Bunny" or a small friendly default list) — never leave it blank.

## 5. UI

### `ui/barn/BarnScreen.kt` + `BarnViewModel.kt` (MVI: intents in, StateFlow out)
- A **grid of the critters you own**: species art, name, level, stage badge, mood. The active
  one is clearly marked ("On the farm ✓"); tapping another offers "Put on the farm".
- A **Hatch** section: one card per species with cost, unlock state, and progress toward the
  unlock (e.g. "3/5 days with water met"). Locked cards explain how to unlock; affordable ones
  have a Hatch button; unaffordable ones show the shortfall in sparks.
- An **Evolve** card for the active critter using `EvolutionRules`: shows the requirement and a
  progress line, with the button enabled only when everything is met. When it evolves, celebrate
  (reuse the celebration style: a dialog naming the new stage).
- Empty/loading/error states with friendly copy, in the app's existing voice.
- Everything must work with **zero Health Connect permissions** (unlock progress then reads 0 and
  the copy must stay encouraging, never scolding).

### `CritterCanvas` — species and stage aware
Extend the signature to `CritterCanvas(mood, speciesKey: String, stage: Int, hatId: String?,
modifier)` and keep the existing mood/hat behaviour working:
- Distinct, readable silhouettes per species, all in the same flat vector style as the current
  blob: e.g. bunny = tall ears, chick = beak + small wings, axolotl = side gills, dragon = horns
  + small wings, sloth = round body + sleepy side eyes, blob = the current circle.
- Stage 1 = slightly larger + one extra detail; stage 2 = larger again + a visible aura or extra
  flourish. The player must be able to SEE that an evolution happened — that is the payoff.
- Colour comes from the species' primary/accent colours; keep the existing mood tints for the
  face so mood still reads.
- Hats must still render on top and stay unclipped (the headroom fix from v1.2 must survive any
  geometry change — verify the hat is still fully visible for the tallest species and stage).

### Screens to touch
- Farm: show the active critter's species name + stage, and a "Barn" button next to Shop/History.
- Navigation: add a `barn` route. Wire Shop → nothing new; Barn is reachable from the farm.
- Keep the existing Shop/History text and behaviour intact.

## 6. Tests (plain JUnit, no Android)

Add `app/src/test/java/com/critterfarm/data/`:
- `SpeciesCatalogTest` — unique keys, the blob is the free starter, every species resolves by key,
  costs are strictly increasing, and every species has non-blank user-facing copy.
- `EvolutionRulesTest` — locked at low level, locked with too few goal days, locked without the
  sparks, **unlocked only when all three are satisfied**, requirement text mentions the stage,
  and the day-count uses only days meeting ≥4 targets.
- `HatchRulesTest` — affordability in sparks, unlock requirements computed from sample logs
  (a player with 5 hydration days unlocks the axolotl and not the sloth), and that a locked
  species can never be hatched even with unlimited sparks.

## 7. Verify before reporting success (do not skip)

```
HOME=/home/hermes ./gradlew :app:assembleDebug
HOME=/home/hermes ./gradlew :app:testDebugUnitTest
```
Both must pass, and the pre-existing tests must stay green (they cover GameRules, the economy,
history and the stat rows). Report: files created/changed, both command results, the exact
evolution/hatch numbers you used, and anything you had to compromise on. If you cannot make
something work, say so plainly — an honest gap is worth more than a silent stub.
