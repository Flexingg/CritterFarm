package com.critterfarm.data

import com.critterfarm.data.local.DecorPlacementEntity
import java.time.LocalDate

/**
 * Outcome of trying to put a decoration on the farm.
 *
 * There is no separate "owned" list: **placing a decoration IS buying it**, and each placement is
 * charged again. That keeps the model to one table with no reconciling between an inventory and a
 * layout, and it means the player can never own an item they cannot see.
 */
sealed class PlaceResult {
    data class Placed(
        val item: DecorItem,
        val cellIndex: Int,
        val coinsLeft: Int,
    ) : PlaceResult()

    data class CannotAfford(
        val item: DecorItem,
        val shortfall: Int,
        val coins: Int,
    ) : PlaceResult()

    data class CellOutOfRange(val cellIndex: Int) : PlaceResult()

    data class CellOccupied(val cellIndex: Int, val existingId: String) : PlaceResult()

    data object UnknownItem : PlaceResult()

    /** The item exists but its event is not running on this date — checked here, not only in the UI. */
    data class OutOfSeason(val item: DecorItem) : PlaceResult()
}

/**
 * The rules for placing a decoration — deliberately pure, so the awkward cases (out of bounds,
 * occupied, cannot afford) are unit-tested rather than discovered by a player tapping a corner of
 * the screen.
 */
object PlacementRules {
    /**
     * [cellCount] defaults to the base grid so every existing caller and test is unaffected; a
     * Thriving-Farm-or-above harmony tier passes the grown count instead, so out-of-range
     * behaviour on the base grid never changes while a bigger grid accepts its new cells.
     */
    fun isInRange(cellIndex: Int, cellCount: Int = DecorCatalog.CELL_COUNT): Boolean = cellIndex in 0 until cellCount

    fun canAfford(item: DecorItem, coins: Int): Boolean = coins >= item.price

    fun shortfall(item: DecorItem, coins: Int): Int = (item.price - coins).coerceAtLeast(0)

    /** How many of each decoration the player currently has standing. */
    fun countsByItem(placements: List<DecorPlacementEntity>): Map<String, Int> =
        placements.groupingBy { it.decorId }.eachCount()

    /**
     * Checks a placement in the order a player would hit the problems: does the item exist, is it
     * in season, is that square on the board, is something already there, can they pay.
     *
     * The event-window check happens here, not only in the shop UI, so an out-of-season event item
     * cannot be placed by id even if something upstream forgot to filter the catalogue first.
     */
    fun validate(
        itemId: String,
        cellIndex: Int,
        coins: Int,
        occupiedBy: Map<Int, String>,
        today: LocalDate = LocalDate.now(),
        cellCount: Int = DecorCatalog.CELL_COUNT,
    ): PlaceResult {
        val item = DecorCatalog.item(itemId) ?: return PlaceResult.UnknownItem
        if (item.eventId != null && !EventCatalog.isActive(item.eventId, today)) {
            return PlaceResult.OutOfSeason(item)
        }
        if (!isInRange(cellIndex, cellCount)) return PlaceResult.CellOutOfRange(cellIndex)
        occupiedBy[cellIndex]?.let { return PlaceResult.CellOccupied(cellIndex, it) }
        if (!canAfford(item, coins)) {
            return PlaceResult.CannotAfford(item, shortfall(item, coins), coins)
        }
        return PlaceResult.Placed(item, cellIndex, coins - item.price)
    }
}
