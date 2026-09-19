# CritterFarm: Calorie Critters — Build Spec

You are an expert Senior Android Engineer. Build the app described here, step by step,
in THIS repository. Deliver **compiling, production-quality Kotlin** — no placeholders,
no `// TODO: implement`, no stubbed functions that return fake data.

## The product (the point of the whole thing)

A native Android **game** that gamifies weight loss using **only** Android Health Connect
data, to nurture a Tamagotchi-style virtual pet farm. Everything serves one goal:
**engaging the user, being fun, and making losing weight feel rewarding.**

### Game loop metaphor
1. **Energy balance / calorie deficit — "Growth Spark".** Compare
   `TotalCaloriesBurnedRecord` (or `ActiveCaloriesBurnedRecord` + BMR) against dietary
   energy from `NutritionRecord`. An intentional, *safe* deficit produces **Mana Sparks**
   that level up critters. Never reward crash-dieting (see Guardrails).
2. **Steps & distance — "Pasture Roam".** `StepsRecord` + `DistanceRecord`. Every 1,000
   steps lets critters explore, discovering coins, seeds and cosmetic hats.
3. **Hydration — "Fresh Pond".** `HydrationRecord` fills the pond, boosts mood.
4. **Workouts — "Gym Barn".** `ExerciseSessionRecord` triggers a training animation and
   awards rare **Treat Tokens**.
5. **Sleep — "Cozy Barn".** `SleepSessionRecord`. 7+ quality hours regenerate the farm's
   **Stamina Meter** for the next day.
6. **Weight — "Evolution Scale".** `WeightRecord`. A weigh-in triggers a critter
   evolution checkpoint / celebratory dance.

### Daily Turn (engagement engine)
- **Automatic delta sync on launch:** on app open, query Health Connect across the last
  **48 hours** and compute the delta since the last recorded sync timestamp.
- **Manual "Claim Daily Turn" chest:** a home-screen interactive chest. Tapping it tallies
  today's stats in an arcade-style slot/counter animation that deposits coins, XP and
  treats into the barn. Manual claiming (not passive) is deliberate — it is the dopamine
  moment of the app.

## Tech stack & hard constraints
- **Kotlin**, `compileSdk = 35`, `targetSdk = 35`, `minSdk = 28`.
- **100% Jetpack Compose + Material 3.** Vibrant, game-like: rounded cards, bouncy spring
  animations (`spring(dampingRatio = Spring.DampingRatioMediumBouncy)`), cheerful palette.
- **Architecture:** Clean Architecture + MVVM/MVI, state-driven UI. `StateFlow` as the
  single source of truth; unidirectional data flow (intents in, state out). No logic in
  composables.
- **Persistence:** Room (local game state only). No backend, no auth, no cloud.
- **Health engine:** `androidx.health.connect:connect-client`.

### Exact dependency versions — USE THESE (they are pre-cached on this machine)
```
AGP 8.13.1 · Gradle 8.14 · Kotlin 2.1.0 · KSP 2.1.0-1.0.29   (already pinned in settings.gradle.kts — do not change)
androidx.compose:compose-bom:2025.09.00
androidx.compose.material3:material3                (from the BOM)
androidx.compose.ui:ui-tooling-preview              (from the BOM)
androidx.activity:activity-compose:1.10.0
androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7
androidx.navigation:navigation-compose:2.8.8
androidx.room:room-runtime:2.6.1 · room-ktx:2.6.1 · room-compiler:2.6.1 (via KSP)
androidx.health.connect:connect-client:1.2.0-alpha02
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1
```
Root `settings.gradle.kts`, `build.gradle.kts` and `gradle.properties` already exist and are
correct — **do not rewrite them**. You own `app/build.gradle.kts`.

## Environment (this machine)
- Android SDK: `ANDROID_HOME=/home/hermes/android-sdk` (also in `local.properties`).
  Platforms 34/35/36 installed; build-tools 34/35/36.
- No `gradle` CLI on PATH — **always use the wrapper**: `./gradlew ...`
- Always prefix builds with `HOME=/home/hermes` if the environment looks odd.
- Build/verify command: `cd /home/hermes/repos/CritterFarm && ./gradlew :app:assembleDebug`
- First build downloads the rest of the deps; it takes a few minutes. Run it and fix
  every compile error until it succeeds. A green `assembleDebug` is the definition of done.

## Deliverables (implement in this order)

### Step 1 — Project setup & manifest
- `app/build.gradle.kts`: android {} block (compileSdk 35, minSdk 28, targetSdk 35,
  `buildFeatures { compose = true }`, Java 17 / Kotlin jvmTarget 17), and the dependency
  list above. KSP for Room.
- `app/src/main/AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.INTERNET" />` not needed; but DO add
    Health Connect package visibility `<queries>` for `com.google.android.apps.healthdata`
    (and the SDK's `ACTION_SHOW_PERMISSIONS_RATIONALE` handling below).
  - Health Connect permission declarations (alpha11+/1.2 style: `<uses-permission
    android:name="android.permission.health.READ_STEPS" />` etc. for each record type).
  - `MainActivity` with an `<intent-filter>` + `<activity-alias>` for
    `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`
    (`android.intent.action.VIEW_PERMISSION_USAGE`, category `HEALTH_PERMISSIONS`).
  - An `Application` class registered via `android:name`.

### Step 2 — Health Connect layer
`app/src/main/java/com/critterfarm/health/HealthConnectManager.kt`
- `HealthConnectClient.getSdkStatus(context)` availability check returning a sealed status
  (`Available`, `NotInstalled`, `UpdateRequired`, `Unavailable`) so the UI can show a warm
  fallback.
- `REQUIRED_PERMISSIONS`: READ permissions for StepsRecord, TotalCaloriesBurnedRecord,
  ActiveCaloriesBurnedRecord, NutritionRecord, HydrationRecord, ExerciseSessionRecord,
  SleepSessionRecord, WeightRecord, DistanceRecord.
- Aggregate queries for **today**: total steps, total energy burned, dietary energy
  consumed, hydration volume, sleep duration (last night), recent exercise sessions, and
  latest weight.
- `computeDelta(since: Instant): HealthDelta` — the 48h catch-up. Must be resilient: any
  single record type failing (permission revoked, no data, provider hiccup) must degrade
  gracefully and never crash the app; missing data must be distinguishable from zero.
- Use `suspend` functions on `Dispatchers.IO`; wrap SDK calls in try/catch and return
  typed results.

### Step 3 — Game state & database
`app/src/main/java/com/critterfarm/data/local/` → Room
- `CritterEntity` (id, name, species/archetype, stage/evolution, xp, level, happiness,
  hunger, mood/emotion enum, lastFedAt, createdAt).
- `FarmInventoryEntity` (coins, treats, manaSparks, seeds, cosmetic hats owned/equipped).
- `DailySummaryLogEntity` (date, steps, caloriesBurned, caloriesConsumed, deficit,
  hydrationMl, sleepMinutes, workouts, weightKg, xpEarned, coinsEarned, treatsEarned,
  chestClaimed: Boolean, syncedAt).
- DAOs with `Flow` queries, a `CritterFarmDatabase`, and a `GameRepository` implementing:
  - XP scaling + level-ups (document the curve; make it feel generous early, slower later).
  - Currency rewards derived from health metrics (steps → coins, workouts → treats,
    deficit → mana sparks, hydration → pond/mood).
  - Critter mood derivation (happy/bouncing, sluggish/tired, celebrating).
  - Idempotent "claim daily turn": claiming twice the same day must not double-pay.
- Seed a starter critter ("Sprout the Blob") on first launch.

### Step 4 — UI & onboarding
- `ui/theme/` — vibrant game palette, Material 3 theme, playful typography, shapes.
- `ui/onboarding/OnboardingScreen.kt` — cheerful welcome, introduces Sprout, a big
  "Connect My Health" button, uses
  `PermissionController.createRequestPermissionResultContract()`, and handles: granted /
  partially granted / denied. Partially granted is NOT a dead end — unlinked features show
  as **"Dormant Zones"** with warm copy (e.g. "The Pond is sleeping until water tracking is
  linked").
- `ui/farm/FarmScreen.kt` + `FarmViewModel.kt`:
  - Animated critter drawn with Compose (`Canvas` or vector) with distinct emotional
    states — bouncing/happy, sluggish/tired, celebration — driven by `animateFloatAsState`
    / `Animatable` with spring physics.
  - Stat pods: step progress ring, calorie deficit/expenditure flame, pond water bar.
  - A "Claim Daily Turn" floating banner that opens a celebration modal (dialog or bottom
    sheet) tallying today's spoils with an arcade-style counting animation.
  - Loading, empty, permission-denied and error states all handled with friendly copy.
- `MainActivity.kt` — sets content, hosts navigation (onboarding → farm), edge-to-edge.

### Guardrails (do not violate)
- **Weight loss must be safe and kind.** Never reward extreme restriction. Clamp deficit
  rewards to a medically sane band (e.g. ignore/flat-line beyond ~1,000 kcal/day deficit)
  and add a supportive message for very low intake rather than bonus loot. No shame-based
  copy, no "you failed" language. Health data is sensitive: never log raw health values at
  info level, no analytics, no network calls.
- Handle `SecurityException` on every Health Connect read (the user can revoke at any time).
- Keep the app fully functional with **zero** permissions granted (Dormant Zones).

## Definition of done
1. `./gradlew :app:assembleDebug` succeeds.
2. Every file listed above exists with a real, complete implementation.
3. `grep -rn "TODO\|FIXME\|placeholder\|not implemented" app/src/main/java` returns nothing
   meaningful.
4. A short `README.md` explaining the game loop, the architecture, and how to build.
