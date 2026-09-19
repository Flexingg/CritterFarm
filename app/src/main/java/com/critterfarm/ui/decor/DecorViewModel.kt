package com.critterfarm.ui.decor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.DecorCatalog
import com.critterfarm.data.GameRepository
import com.critterfarm.data.PlaceResult
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DecorPlacementEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class DecorIntent {
    /** Arm a decoration so the next square tapped gets it. */
    data class SelectItem(val itemId: String?) : DecorIntent()

    /** A square was tapped: place the armed item, or clear what is standing there. */
    data class TapCell(val cellIndex: Int) : DecorIntent()

    data object DismissMessage : DecorIntent()
}

data class DecorUiState(
    val isLoading: Boolean = true,
    val coins: Int = 0,
    val placements: List<DecorPlacementEntity> = emptyList(),
    val critter: CritterEntity? = null,
    val equippedHatId: String? = null,
    val selectedItemId: String? = null,
    val message: String? = null,
) {
    /** How many of each decoration are standing — shown as a count on the shop cards. */
    val countsByItem: Map<String, Int> get() = placements.groupingBy { it.decorId }.eachCount()

    val placedCount: Int get() = placements.size

    val canAffordSelected: Boolean
        get() = selectedItemId?.let { id ->
            DecorCatalog.item(id)?.let { coins >= it.price }
        } ?: false
}

class DecorViewModel(private val gameRepository: GameRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DecorUiState())
    val uiState: StateFlow<DecorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                gameRepository.inventory,
                gameRepository.decorPlacements,
                gameRepository.critter,
            ) { inventory, placements, critter -> Triple(inventory, placements, critter) }
                .collect { (inventory, placements, critter) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            coins = inventory?.coins ?: 0,
                            placements = placements,
                            critter = critter,
                            equippedHatId = inventory?.equippedHatId,
                        )
                    }
                }
        }
    }

    fun onIntent(intent: DecorIntent) {
        when (intent) {
            is DecorIntent.SelectItem -> _uiState.update { it.copy(selectedItemId = intent.itemId) }
            is DecorIntent.TapCell -> tapCell(intent.cellIndex)
            DecorIntent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun tapCell(cellIndex: Int) {
        val state = _uiState.value
        val existing = state.placements.firstOrNull { it.cellIndex == cellIndex }
        if (existing != null) {
            clear(cellIndex)
            return
        }
        val itemId = state.selectedItemId
        if (itemId == null) {
            _uiState.update {
                it.copy(message = "Pick a decoration below first, then tap a square.")
            }
            return
        }
        place(itemId, cellIndex)
    }

    private fun place(itemId: String, cellIndex: Int) {
        viewModelScope.launch {
            when (val result = gameRepository.placeDecor(itemId, cellIndex)) {
                is PlaceResult.Placed -> _uiState.update {
                    it.copy(
                        message = "${result.item.emoji} ${result.item.name} is on the farm — " +
                            "${result.item.price} coins spent, ${result.coinsLeft} left.",
                    )
                }
                is PlaceResult.CannotAfford -> _uiState.update {
                    it.copy(
                        message = "${result.item.name} needs ${result.shortfall} more coins — " +
                            "100 steps earns one.",
                    )
                }
                is PlaceResult.CellOccupied -> _uiState.update {
                    it.copy(message = "That square is taken — tap it again to clear it.")
                }
                is PlaceResult.CellOutOfRange -> _uiState.update {
                    it.copy(message = "That square is off the farm.")
                }
                PlaceResult.UnknownItem -> _uiState.update {
                    it.copy(message = "That decoration is not in the catalogue.")
                }
            }
        }
    }

    private fun clear(cellIndex: Int) {
        viewModelScope.launch {
            val removed = _uiState.value.placements.firstOrNull { it.cellIndex == cellIndex }
            val name = removed?.let { DecorCatalog.item(it.decorId)?.name } ?: "Decoration"
            if (gameRepository.removeDecor(cellIndex)) {
                _uiState.update {
                    it.copy(message = "$name cleared. Coins are not refunded — tidying up is free.")
                }
            }
        }
    }
}

class DecorViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DecorViewModel(gameRepository) as T
}
