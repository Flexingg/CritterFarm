package com.critterfarm.data

import java.time.LocalDate

/**
 * A seasonal event: a fixed window on the calendar with its own emoji, blurb and (via
 * [ChallengeCatalog]) a challenge, plus (via [DecorCatalog]) a handful of decorations that only
 * ever appear while it runs. Nothing here is randomised or generated — the calendar is static
 * data, so "is the harvest festival on" always means the same two dates.
 */
data class FarmEvent(
    val id: String,
    val name: String,
    val emoji: String,
    val blurb: String,
    val start: LocalDate,
    /** Inclusive — the event is still running on this day. */
    val end: LocalDate,
)

/**
 * The full seasonal calendar. Four events spread across the year, each a few weeks long, so
 * there is usually nothing running (the common case, and it must look intentional) and
 * occasionally something worth logging in for.
 */
object EventCatalog {
    val ALL: List<FarmEvent> = listOf(
        FarmEvent(
            id = "sprout_spring_bloom",
            name = "Spring Bloom Festival",
            emoji = "🌸",
            blurb = "The farm wakes up for spring — extra flowers, extra reasons to wander outside.",
            start = LocalDate.of(2026, 3, 1),
            end = LocalDate.of(2026, 4, 11),
        ),
        FarmEvent(
            id = "sunny_summer_games",
            name = "Sunny Summer Games",
            emoji = "☀️",
            blurb = "A season of friendly competition — the whole farm is showing off its steps.",
            start = LocalDate.of(2026, 6, 1),
            end = LocalDate.of(2026, 7, 13),
        ),
        FarmEvent(
            id = "harvest_moon",
            name = "Harvest Moon Festival",
            emoji = "🌾",
            blurb = "The fields are golden and the barn is full — time to bring in the harvest.",
            start = LocalDate.of(2026, 9, 1),
            end = LocalDate.of(2026, 10, 12),
        ),
        FarmEvent(
            id = "frost_lantern",
            name = "Frost Lantern Festival",
            emoji = "❄️",
            blurb = "Lanterns in the snow and one last push before the year turns over.",
            start = LocalDate.of(2026, 12, 1),
            end = LocalDate.of(2027, 1, 9),
        ),
    )

    fun byId(id: String): FarmEvent? = ALL.firstOrNull { it.id == id }

    /** The one event running on [date], if any — events never overlap, so there is at most one. */
    fun activeOn(date: LocalDate): FarmEvent? =
        ALL.firstOrNull { date.isBefore(it.start).not() && date.isAfter(it.end).not() }

    fun isActive(id: String, date: LocalDate): Boolean = activeOn(date)?.id == id
}
