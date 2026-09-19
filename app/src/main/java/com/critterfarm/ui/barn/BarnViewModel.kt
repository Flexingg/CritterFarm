package com.critterfarm.ui.barn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.critterfarm.data.EvolutionRequirement
import com.critterfarm.data.EvolutionRules
import com.critterfarm.data.EvolveResult
import com.critterfarm.data.GameRepository
import com.critterfarm.data.HatchResult
import com.critterfarm.data.HatchRules
import com.critterfarm.data.Species
import com.critterfarm.data.SpeciesCatalog
import com.critterfarm.data.local.CritterEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Intents in, state out — the Barn screen never mutates state directly. */
sealed class BarnIntent {
    data class Hatch(val speciesKey: String) : BarnIntent()

    data class SetActive(val critterId: Long) : BarnIntent()

    data object EvolveActive : BarnIntent()

    data object DismissMessage : BarnIntent()

    data object DismissCelebration : BarnIntent()
}

/** One card in the Hatch section: cost, unlock state, and progress toward the unlock. */
data class HatchCardUi(
    val species: Species,
    val unlocked: Boolean,
    val progressText: String,
    val affordable: Boolean,
    val shortfall: Int,
)

data class BarnUiState(
    val isLoading: Boolean = true,
    val critters: List<CritterEntity> = emptyList(),
    val activeCritterId: Long? = null,
    val manaSparks: Int = 0,
    val hatchCards: List<HatchCardUi> = emptyList(),
    /** Null once the active critter has reached the final stage — there's nothing left to show. */
    val evolutionRequirement: EvolutionRequirement? = null,
    val evolutionCelebration: Pair<CritterEntity, Int>? = null,
    val message: String? = null,
)

/**
 * Owns the Barn screen's state: the owned critters, the hatch catalogue (unlocked or not, from
 * the stored daily logs), and the active critter's evolution progress. All the maths lives in
 * [HatchRules] and [EvolutionRules]; this class only shapes their output for the screen.
 */
class BarnViewModel(private val gameRepository: GameRepository) : ViewModel() {

    private data class Combined(
        val critters: List<CritterEntity>,
        val activeId: Long?,
        val manaSparks: Int,
        val hatchCards: List<HatchCardUi>,
        val evolutionRequirement: EvolutionRequirement?,
    )

    private val _uiState = MutableStateFlow(BarnUiState())
    val uiState: StateFlow<BarnUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                gameRepository.observeAllCritters(),
                gameRepository.critter,
                gameRepository.inventory,
                gameRepository.dailyLogs,
            ) { critters, active, inventory, logs ->
                val manaSparks = inventory?.manaSparks ?: 0
                val hatchCards = SpeciesCatalog.ALL.map { species ->
                    val progress = HatchRules.unlockProgress(species, logs)
                    HatchCardUi(
                        species = species,
                        unlocked = progress.unlocked,
                        progressText = progress.progressText,
                        affordable = HatchRules.canAfford(species, manaSparks),
                        shortfall = HatchRules.shortfall(species, manaSparks),
                    )
                }
                val requirement = active?.takeIf { it.stage < 2 }?.let {
                    EvolutionRules.requirementFor(it.stage + 1, logs, it, manaSparks)
                }
                Combined(critters, active?.id, manaSparks, hatchCards, requirement)
            }.collect { combined ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        critters = combined.critters,
                        activeCritterId = combined.activeId,
                        manaSparks = combined.manaSparks,
                        hatchCards = combined.hatchCards,
                        evolutionRequirement = combined.evolutionRequirement,
                    )
                }
            }
        }
    }

    fun onIntent(intent: BarnIntent) {
        when (intent) {
            is BarnIntent.Hatch -> hatch(intent.speciesKey)
            is BarnIntent.SetActive -> setActive(intent.critterId)
            BarnIntent.EvolveActive -> evolve()
            BarnIntent.DismissMessage -> _uiState.update { it.copy(message = null) }
            BarnIntent.DismissCelebration -> _uiState.update { it.copy(evolutionCelebration = null) }
        }
    }

    private fun hatch(speciesKey: String) {
        viewModelScope.launch {
            val message = when (val result = gameRepository.hatchCritter(speciesKey, null)) {
                is HatchResult.Hatched ->
                    "${result.critter.name} hatched! Welcome to the barn."
                is HatchResult.Locked ->
                    "Not yet — ${result.species.unlockNote}"
                is HatchResult.CannotAfford ->
                    "Not yet — ${result.shortfall} more Mana Sparks to go."
                HatchResult.UnknownSpecies ->
                    "That species isn't in the catalogue."
                HatchResult.NoInventory ->
                    "The farm hasn't woken up yet — try again in a moment."
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    private fun setActive(critterId: Long) {
        viewModelScope.launch { gameRepository.setActiveCritter(critterId) }
    }

    private fun evolve() {
        viewModelScope.launch {
            when (val result = gameRepository.evolveActiveCritter()) {
                is EvolveResult.Evolved -> _uiState.update {
                    it.copy(evolutionCelebration = result.critter to result.newStage)
                }
                is EvolveResult.NotReady -> _uiState.update {
                    it.copy(message = "Not ready yet — ${result.requirement.progressText}")
                }
                is EvolveResult.CannotAfford -> _uiState.update {
                    it.copy(message = "So close — ${result.shortfall} more Mana Sparks to go.")
                }
            }
        }
    }
}

class BarnViewModelFactory(private val gameRepository: GameRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BarnViewModel(gameRepository) as T
}
