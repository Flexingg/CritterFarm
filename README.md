# CritterFarm: Calorie Critters

A native Android game that gamifies weight loss using **only** on-device Android Health
Connect data, to nurture a Tamagotchi-style virtual pet farm. No backend, no accounts, no
network calls — everything lives on the device.

## The game loop

Sprout the Blob (and later, other critters) grows through six health-driven zones:

| Zone | Health Connect source | What it does |
|---|---|---|
| **Growth Spark** | `TotalCaloriesBurnedRecord` / `ActiveCaloriesBurnedRecord` vs. `NutritionRecord` | A *safe* calorie deficit mints Mana Sparks that level up your critter. Deficits are clamped to a medically sane band (~1,000 kcal/day) — crash-dieting never earns extra loot, and a very low logged intake gets a supportive message instead of a reward. |
| **Pasture Roam** | `StepsRecord` / `DistanceRecord` | Steps convert into coins (100 steps = 1 coin) and drive the step-ring stat pod. |
| **Fresh Pond** | `HydrationRecord` | Hydration fills the pond and boosts the critter's mood. |
| **Gym Barn** | `ExerciseSessionRecord` | Logged workouts award rare Treat Tokens (5 per workout) and reduce hunger. |
| **Cozy Barn** | `SleepSessionRecord` | Last night's sleep duration is tracked toward the farm's stamina. |
| **Evolution Scale** | `WeightRecord` | The latest weigh-in is surfaced as an evolution checkpoint. |

**Daily Turn loop:** on every app open, the farm automatically syncs today's totals from
Health Connect (`HealthConnectManager.computeDelta` exercises the 48h catch-up window since
the last sync; the authoritative per-day numbers come from `getTodaySnapshot()`, since a
whole-day total is replace-safe to write repeatedly while a partial delta is not). Rewards
aren't paid out on sync, though — tapping the **Claim Daily Turn** chest is what actually
tallies the day's stats and deposits coins/XP/treats/Mana Sparks, with an arcade-style
count-up animation. Claiming is idempotent: opening the chest twice in one day only pays out
once.

**Dormant Zones:** every zone works independently of the others. If only some Health Connect
permissions are granted (or Health Connect isn't installed at all), the ungranted zones show
up as sleepy "Dormant Zones" with warm, specific copy — never an error screen. The app is
fully playable with zero permissions granted.

## Stats at a glance

Every zone's number sits in one **"Today on the farm"** card, in a fixed order, and every row
answers the same three questions:

```
🐾 Pasture Roam                        3,588 steps to go
6,412  / 10,000 steps
▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░
```

- **Big current value**, then `/ target`, then a status string (`3,588 steps to go`,
  `Goal met!`, `Link health to track`).
- A **dormant zone still shows its target** — an unlinked farm tells you where you need to be
  instead of hiding the number.
- Units are the ones you actually read: **steps**, **kcal**, **fl oz**, **sessions**, **h m**
  (sleep), **lb** (weight). Health Connect's ml/kg values are converted in one place
  (`StatsFormat.kt`) so they can be unit-tested.
- Bar colour carries the state as well as the text: gold/green at goal, the zone's accent while
  in progress, grey when unlinked. No colour-only signals.

## Screenshots

Captured from the `critterfarm_api35` emulator (Android 15) running **v1.2**. The local database
was seeded with demo days so the heatmap and shop have something to show — the emulator has no
Health Connect data, so these are not real user stats:

| Sprout wearing a bought hat | The Barn Shop | Claim + streak payout | History heatmap |
|---|---|---|---|
| ![hat](docs/screenshots/04-v12-farm-hat.png) | ![shop](docs/screenshots/05-v12-shop.png) | ![claim](docs/screenshots/07-v12-celebration.png) | ![history](docs/screenshots/06-v12-history.png) |

Earlier layout shots (v1.0 onboarding/navigation) are `01`–`03` in the same folder.

## What the currencies are for

v1.1 earned coins, treats and Mana Sparks with nothing to spend them on. v1.2 closes that loop —
each currency now has exactly one job:

| Currency | Earned from | Spent on |
|---|---|---|
| 🪙 **Coins** (100 steps = 1) | steps | hats (150–2,000) and **Streak Freezes** (250) |
| 🍬 **Treats** (5 per workout) | workouts | **feeding Sprout** — hunger ↓, happiness ↑ |
| 🔮 **Mana Sparks** (scarce) | a safe calorie deficit | the prestige **Deficit Halo** |

Every hat is drawn with the same Canvas as the critter, so Sprout actually wears what you buy.

**Three rules keep it honest**, and there is a unit test for the third:
1. Consistency gates capability; currency only buys decoration. You cannot purchase a level or
   an evolution.
2. Nothing buys a health shortcut — no "skip today's workout", no buying a deficit.
3. `GameEconomyTest` fails the build if anyone adds a purchasable item that is not a cosmetic or
   streak insurance.

**The streak is what compounds:** day 3 pays ×1.5, day 7 ×2, day 30 ×3 on every reward, shown
in the celebration modal. Miss a day and a Streak Freeze (if you own one) keeps the chain alive.

## History

Tap any stat row for that metric's history — a 14-day bar chart, best-ever with its date, the
average and how many days are recorded. The **Farm history** screen adds a GitHub-style
consistency heatmap (five shades for how many of the six daily targets you hit) and records:
most steps in a day, most water, longest sleep, most workouts, longest goal streak, best 6/6
day, and lowest weigh-in — framed as a milestone (*"slow and steady wins"*), never a race.

All of it is computed from the daily logs the app already stored, so it needs **no new Health
Connect permissions** and works offline.


## Architecture

Clean Architecture-ish, MVI on the UI layer:

```
health/    HealthConnectManager — the only code that talks to Health Connect.
           Every SDK call is wrapped in try/catch; a permission revoke or provider hiccup
           degrades a single field to null (never crashes), and null is kept distinguishable
           from a real zero.

data/      GameRepository — the single source of truth for game state, backed by Room.
data/local/  Room entities (CritterEntity, FarmInventoryEntity, DailySummaryLogEntity),
             DAOs (Flow-based), and CritterFarmDatabase.
data/GameRules.kt   Pure, Android-free reward math (XP curve, currency conversion, the
                     deficit guardrail, mood derivation) — unit tested in isolation.

ui/theme/    Material 3 theme: vibrant palette, rounded shapes, bold playful type.
ui/model/    GameZone — maps each zone to its required Health Connect permission(s) and
             its Dormant Zone copy, shared by onboarding and the farm screen.
ui/onboarding/  OnboardingScreen — introduces Sprout, requests permissions via
                PermissionController.createRequestPermissionResultContract(), and always
                has a path forward (granted / partial / denied / Health Connect missing).
ui/farm/     FarmViewModel (StateFlow<FarmUiState> out, FarmIntent in — no game logic in
             composables) + FarmScreen + the Canvas-drawn critter, stat pods, claim chest,
             and celebration modal.
```

Data flows one way: Health Connect → `HealthConnectManager` → `FarmViewModel` →
`GameRepository` (persistence + reward math) → Room → `Flow` → `FarmUiState` → Compose.
User actions flow back up as `FarmIntent`s; the view model is the only thing that mutates
state.

## Guardrails

- Calorie-deficit rewards are clamped to a safe band; very low intake gets a kind message,
  never bonus loot, and there is no shame-based copy anywhere in the app.
- Every Health Connect read handles `SecurityException` (permissions can be revoked at any
  time) and never crashes the app.
- The farm is fully functional with zero permissions granted.
- No raw health values are logged, no analytics, no network calls.

## Download & install

A signed release APK is published — no toolchain, no Android Studio:

| Where | What |
|---|---|
| **[GitHub Releases](https://github.com/Flexingg/CritterFarm/releases/latest)** | `CritterFarm-1.2-release.apk` (recommended) |
| **In this repo** | [`dist/CritterFarm-1.2-release.apk`](dist/CritterFarm-1.2-release.apk) |

Install: allow "install unknown apps" for your browser/file manager → open the APK → launch
**CritterFarm**. Health Connect ships with Android 14+; on older devices grab it from the Play
Store first. The farm is playable before granting anything — ungranted zones simply appear as
sleepy "Dormant Zones".

```bash
# confirm you got the same bytes we built
sha256sum CritterFarm-1.2-release.apk     # compare to dist/CritterFarm-1.2-release.apk.sha256
```

The release APK is signed with a **4096-bit RSA** key (`CN=CritterFarm`, APK Signature Scheme
v2): sideload-ready, not Play-Store-submitted. The keystore lives *outside* this repo
(`~/.keystores/critterfarm-release.jks`, pointed at by a gitignored `keystore.properties`), so
a build made without it falls back to debug signing and will **not** install over this one.

## Emulator

An AVD (`critterfarm_api35`, Android 15 / API 35, KVM-accelerated) is configured on this machine
for running and verifying the app without a phone — including headless screenshots from the
command line. Full commands and gotchas: [`docs/emulator.md`](docs/emulator.md).

## Building

```
cd /home/hermes/repos/CritterFarm
HOME=/home/hermes ./gradlew :app:assembleDebug      # build the debug APK
HOME=/home/hermes ./gradlew :app:testDebugUnitTest   # run the GameRules unit tests
```

The `HOME=/home/hermes` prefix keeps Gradle pointed at the cached dependencies and Android
SDK on this machine (`ANDROID_HOME` / `local.properties` already point at
`/home/hermes/android-sdk`). There's no `gradle` CLI on `PATH` — always use the wrapper.
