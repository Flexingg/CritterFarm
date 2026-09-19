package com.critterfarm.ui.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.GameRepository
import com.critterfarm.data.GameRules
import com.critterfarm.data.PurchaseResult
import com.critterfarm.data.ShopCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ShopIntent {
    data class Buy(val itemId: String) : ShopIntent()

    /** Equipping null takes the hat off. */
    data class Equip(val itemId: String?) : ShopIntent()

    data object DismissMessage : ShopIntent()
}

data class ShopUiState(
    val coins: Int = 0,
    val manaSparks: Int = 0,
    val treats: Int = 0,
    val ownedHatIds: Set<String> = emptySet(),
    val equippedHatId: String? = null,
    val streakFreezes: Int = 0,
    val message: String? = null,
) {
    fun canAfford(item: com.critterfarm.data.ShopItem): Boolean =
        GameRules.canAfford(item, coins, manaSparks)

    /** How much more the player needs, for the "3 days of steps to go" nudge. */
    fun shortfallFor(item: com.critterfarm.data.ShopItem): Int = when (item.currency) {
        com.critterfarm.data.ShopCurrency.COINS -> (item.price - coins).coerceAtLeast(0)
        com.critterfarm.data.ShopCurrency.MANA_SPARKS -> (item.price - manaSparks).coerceAtLeast(0)
    }
}

/**
 * Owns the shop's state. Purchases are the only way currency leaves the farm, so this is where
 * the v1.2 sink economy actually happens.
 */
class ShopViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gameRepository.inventory.collect { inventory ->
                _uiState.update {
                    it.copy(
                        coins = inventory?.coins ?: 0,
                        manaSparks = inventory?.manaSparks ?: 0,
                        treats = inventory?.treats ?: 0,
                        ownedHatIds = GameRules.parseOwnedHatIds(inventory?.ownedHatIds ?: ""),
                        equippedHatId = inventory?.equippedHatId,
                        streakFreezes = inventory?.streakFreezes ?: 0,
                    )
                }
            }
        }
    }

    fun onIntent(intent: ShopIntent) {
        when (intent) {
            is ShopIntent.Buy -> buy(intent.itemId)
            is ShopIntent.Equip -> equip(intent.itemId)
            ShopIntent.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun buy(itemId: String) {
        viewModelScope.launch {
            val message = when (val result = gameRepository.purchase(itemId)) {
                is PurchaseResult.Purchased -> when (result.item.kind) {
                    com.critterfarm.data.ShopItemKind.HAT ->
                        "${result.item.emoji} ${result.item.name} is yours — tap Wear to put it on Sprout."
                    com.critterfarm.data.ShopItemKind.STREAK_FREEZE ->
                        "🧊 Streak Freeze stashed. It spends itself the next time you miss a day."
                }
                PurchaseResult.AlreadyOwned -> "You already own that one."
                is PurchaseResult.CannotAfford -> {
                    val short = _uiState.value.shortfallFor(result.item)
                    val purse = if (result.item.currency == com.critterfarm.data.ShopCurrency.COINS) {
                        "$short more coins"
                    } else {
                        "$short more Mana Sparks"
                    }
                    "Not yet — $purse to go."
                }
                PurchaseResult.UnknownItem -> "That item is no longer in the shop."
                PurchaseResult.NoInventory -> "The farm hasn't woken up yet — try again in a moment."
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    private fun equip(itemId: String?) {
        viewModelScope.launch {
            gameRepository.equipHat(itemId)
            val message = if (itemId == null) {
                "Hat tucked away in the barn."
            } else {
                val name = ShopCatalog.item(itemId)?.name ?: "Hat"
                "$name on! Sprout is wearing it now."
            }
            _uiState.update { it.copy(message = message) }
        }
    }
}

class ShopViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ShopViewModel(gameRepository) as T
}
