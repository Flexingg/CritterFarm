package com.critterfarm.ui.decor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.critterfarm.data.DecorCatalog
import com.critterfarm.data.local.CritterEntity
import com.critterfarm.data.local.DecorPlacementEntity
import com.critterfarm.ui.farm.CritterCanvas
import com.critterfarm.ui.theme.SkyBlue
import com.critterfarm.ui.theme.SproutGreen

/**
 * The farm itself: a 6x4 grid of ground squares with the critter standing in the middle.
 *
 * Tapping a square either places the currently selected decoration (when there is one selected) or
 * asks to remove what is standing there — the caller decides, so this stays a dumb view.
 *
 * The critter is an overlay rather than a grid cell: it has no pointer handling of its own, so taps
 * over it still reach the square underneath, which is what a player expects when the animal is
 * standing on the bit of farm they want to plant.
 */
@Composable
fun FarmScene(
    critter: CritterEntity?,
    hatId: String?,
    placements: List<DecorPlacementEntity>,
    selectedItemId: String?,
    onCellTap: (Int) -> Unit,
    rows: Int = DecorCatalog.GRID_ROWS,
    modifier: Modifier = Modifier,
) {
    val byCell = remember(placements) { placements.associateBy { it.cellIndex } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(DecorCatalog.GRID_COLUMNS.toFloat() / rows.toFloat()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    repeat(DecorCatalog.GRID_COLUMNS) { column ->
                        val index = row * DecorCatalog.GRID_COLUMNS + column
                        SceneCell(
                            index = index,
                            decorEmoji = byCell[index]?.let { DecorCatalog.item(it.decorId)?.emoji },
                            hasSelection = selectedItemId != null,
                            onClick = { onCellTap(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // The critter, painted over the middle of the farm. No click handling: taps pass through.
        if (critter != null) {
            CritterCanvas(
                mood = critter.mood,
                speciesKey = critter.species,
                stage = critter.stage,
                hatId = hatId,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(132.dp),
            )
        }
    }
}

@Composable
private fun SceneCell(
    index: Int,
    decorEmoji: String?,
    hasSelection: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val oddRow = (index / DecorCatalog.GRID_COLUMNS) % 2 == 1
    val placed = decorEmoji != null
    val ground: Color = when {
        placed -> SproutGreen.copy(alpha = 0.28f)
        oddRow -> SproutGreen.copy(alpha = 0.10f)
        else -> SkyBlue.copy(alpha = 0.10f)
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            placed -> Text(text = decorEmoji!!, fontSize = 24.sp)
            hasSelection -> Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
    }
}
