# Task: make today's stats instantly readable

The farm screen currently fails at its most important job. `StepProgressRing` renders a bare
`"$steps"` with no target, `CalorieFlame` and `PondWaterBar` render **no numbers at all**, and the
six zone cards describe flavour text ("The Gym Barn is quiet until workout tracking is linked")
instead of showing a number against a goal.

Rewrite the stats UI so a glance answers three questions for every metric:

1. **What is it?** — plain label + unit.
2. **Where am I?** — the current value, as the largest text on the row.
3. **Where do I need to be?** — the target, and how far is left to go.

## The one rule that matters

Every single stat must render, in plain text, `current / target` **plus** a short status string
such as `3,588 to go`, `Goal met!`, or `Link health to track`. No metric may be represented by a
picture alone. If a decorative widget stays, the same numbers must be printed next to it.

## The stats (six rows, always all six rendered)

| Zone (row title) | Metric | Target | Unit formatting |
|---|---|---|---|
| Pasture Roam | steps | 10,000 | `6,412` / `10,000 steps`, thousands separators |
| Growth Spark | calorie deficit | 500 kcal | `312` / `500 kcal` (deficit, never raw intake) |
| Fresh Pond | hydration | 100 fl oz | **fl oz** (`64` / `100 fl oz`), ml only as small secondary text |
| Gym Barn | workouts | 1 session | `1` / `1 session`, use `session`/`sessions` correctly |
| Cozy Barn | sleep last night | 8 h | `6h 42m` / `8h 0m` |
| Evolution Scale | latest weigh-in | 1 per week | latest value in **lb** + `Logged today` / `N days ago` |

- Water is tracked in **fl oz** by the owner; weight is **lb**. Convert from the SI values
  Health Connect returns (ml → fl oz, kg → lb) and keep the conversion in one place.
- Growth Spark shows the **deficit** (burned − consumed, clamped to the safe band), because that
  is what the reward is based on. Never display a "you ate too much" framing.

## Layout

- A single **"Today on the farm"** card, directly under the critter, holding the six rows in a
  fixed order (the order above). No horizontal scrolling, no nested cards.
- Each row: `[icon] [Zone name]            [BIG current] / [target] [unit]`
  then a thin rounded progress bar underneath, and a small right-aligned status line.
- Bar fill = `current / target`, clamped 0..1. Colour by state:
  `goal met` → gold/green, `in progress` → the zone's accent colour, `no data` → muted grey.
- **Make the numbers the loudest element on the row** — current value in a headline/`headlineSmall`
  weight, label smaller. This is the whole point of the change.
- Keep the existing critter canvas, the Claim Daily Turn banner, and the celebration modal exactly
  as they are. Keep the animation polish (animated bar fills are welcome).
- Replace the old zone-card list with these rows — do not keep both, and do not delete the
  zone flavour: keep one short flavour line per row only if it does not push the numbers out.

## No-data state (the state the emulator will actually show)

This must never hide the target:

- Row still shows the full `current / target` scaffold with the target visible, e.g.
  `— / 10,000 steps` and status `Link health to track`.
- Keep the existing friendly framing — no error styling, no shame, no red.
- The existing "Zzz... the farm is quiet" empty-state card with the *Connect My Health* button
  stays for the all-dormant case, but it must sit ABOVE rows that still show their targets.

## Accessibility

- Every row must expose a single content description for screen readers, e.g.
  "Steps: 6,412 of 10,000, 3,588 to go".
- Keep text >= 12sp, do not rely on colour alone to convey state (the status string carries it).

## Verify before you report success

- `HOME=/home/hermes ./gradlew :app:assembleDebug` must pass.
- Existing unit tests (`:app:testDebugUnitTest`) must stay green; add unit tests for the
  formatting/conversion helpers (ml→fl oz, kg→lb, sleep minutes→`6h 42m`, thousands separators,
  "to go" maths, goal-met edge at exactly the target).
- Report the files you changed and anything you had to compromise on.
