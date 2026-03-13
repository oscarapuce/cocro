package com.cocro.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocro.session.Participant
import com.cocro.session.SessionUiState
import com.cocro.ui.components.CocroButton
import com.cocro.ui.components.CocroButtonVariant
import com.cocro.ui.components.CocroHr
import com.cocro.ui.components.CocroKeyboard
import com.cocro.ui.components.WordDirection
import com.cocro.ui.theme.CocroColors
import com.cocro.ui.theme.FontGrid
import com.cocro.ui.theme.FontLabel
import com.cocro.ui.theme.FontTitle
import com.cocro.ui.theme.FontUi

private const val GRID_W = 10
private const val GRID_H = 10

/** One distinct color per participant slot (index 0 = local player). */
private val PLAYER_COLORS = listOf(
    Color(0xFF2D5A3D), // forest green  (slot 0 / moi)
    Color(0xFF2C4A8A), // encre bleue   (slot 1)
    Color(0xFFB83225), // rouge         (slot 2)
    Color(0xFFC4952A), // or            (slot 3)
)

@Composable
fun GameBoardScreen(
    shareCode: String,
    myUserId: String,
    sessionState: SessionUiState,
    onPlaceLetter: (posX: Int, posY: Int, letter: Char) -> Unit,
    onClearCell: (posX: Int, posY: Int) -> Unit,
    onLeave: () -> Unit,
) {
    val active = sessionState as? SessionUiState.Active
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var direction by remember { mutableStateOf(WordDirection.HORIZONTAL) }

    // (x,y) → letter
    val cellMap: Map<Pair<Int, Int>, Char> =
        active?.cells?.associate { (it.x to it.y) to it.letter } ?: emptyMap()

    // Map userId → color based on participant list index
    val participantColors: Map<String, Color> = buildParticipantColors(
        participants = active?.participants ?: emptyList(),
        myUserId = myUserId,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CocroColors.paper),
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = shareCode,
                    style = TextStyle(
                        fontFamily = FontTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp,
                        color = CocroColors.ink,
                    ),
                )
                if (active != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Rév.${active.gridRevision}",
                            style = TextStyle(
                                fontFamily = FontLabel,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp,
                                color = CocroColors.inkMuted,
                            ),
                        )
                        // Participant dots
                        active.participants.forEach { participant ->
                            val color = participantColors[participant.userId] ?: PLAYER_COLORS[0]
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (participant.isOnline) color else color.copy(alpha = 0.3f),
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
            CocroButton(
                text = "Quitter",
                onClick = onLeave,
                variant = CocroButtonVariant.Danger,
            )
        }

        CocroHr()

        // ── Loading banner ────────────────────────────────────────────────────
        if (sessionState is SessionUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CocroColors.forestLight.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = sessionState.message,
                    style = MaterialTheme.typography.bodySmall.copy(color = CocroColors.forest),
                )
            }
        }

        // ── Grid area (scrollable, takes remaining height) ────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                repeat(GRID_H) { y ->
                    Row {
                        repeat(GRID_W) { x ->
                            val coord = x to y
                            val isSelected = selectedCell == coord
                            val otherParticipant = active?.participants?.firstOrNull { p ->
                                p.cursorX == x && p.cursorY == y && p.userId != myUserId
                            }
                            val otherColor = otherParticipant?.let { participantColors[it.userId] }
                            GridCell(
                                letter = cellMap[coord],
                                isSelected = isSelected,
                                myColor = participantColors[myUserId] ?: PLAYER_COLORS[0],
                                otherCursorColor = otherColor,
                                direction = if (isSelected) direction else null,
                                onClick = {
                                    if (selectedCell == coord) {
                                        // Double-tap same cell → toggle direction
                                        direction = if (direction == WordDirection.HORIZONTAL)
                                            WordDirection.VERTICAL else WordDirection.HORIZONTAL
                                    } else {
                                        selectedCell = coord
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        CocroHr()

        // ── Current cell indicator ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CocroColors.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectedCell != null) {
                val (cx, cy) = selectedCell!!
                Text(
                    text = if (direction == WordDirection.HORIZONTAL) "→" else "↓",
                    style = TextStyle(
                        fontFamily = FontUi,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = participantColors[myUserId] ?: PLAYER_COLORS[0],
                    ),
                )
                Text(
                    text = "Colonne ${cx + 1}, Ligne ${cy + 1}",
                    style = TextStyle(
                        fontFamily = FontUi,
                        fontSize = 13.sp,
                        color = CocroColors.inkMuted,
                    ),
                    modifier = Modifier.weight(1f),
                )
                CocroButton(
                    text = "Effacer",
                    onClick = {
                        val sel = selectedCell ?: return@CocroButton
                        onClearCell(sel.first, sel.second)
                    },
                    variant = CocroButtonVariant.Ghost,
                )
            } else {
                Text(
                    text = "Appuyez sur une case pour la sélectionner",
                    style = TextStyle(
                        fontFamily = FontUi,
                        fontSize = 13.sp,
                        color = CocroColors.inkMuted,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Custom keyboard (always visible, no phone keyboard) ───────────────
        CocroKeyboard(
            direction = direction,
            onLetterInput = { char ->
                val sel = selectedCell ?: return@CocroKeyboard
                onPlaceLetter(sel.first, sel.second, char)
                selectedCell = advanceCursor(sel, direction)
            },
            onBackspace = {
                val sel = selectedCell ?: return@CocroKeyboard
                onClearCell(sel.first, sel.second)
                selectedCell = retreatCursor(sel, direction)
            },
            onDirectionToggle = {
                direction = if (direction == WordDirection.HORIZONTAL)
                    WordDirection.VERTICAL else WordDirection.HORIZONTAL
            },
        )
    }
}

// ── Grid cell ────────────────────────────────────────────────────────────────

@Composable
private fun GridCell(
    letter: Char?,
    isSelected: Boolean,
    myColor: Color,
    otherCursorColor: Color?,
    direction: WordDirection?,
    onClick: () -> Unit,
) {
    val borderColor = when {
        isSelected           -> myColor
        otherCursorColor != null -> otherCursorColor.copy(alpha = 0.6f)
        else                 -> CocroColors.border
    }
    val borderWidth = when {
        isSelected || otherCursorColor != null -> 2.dp
        else -> 1.dp
    }
    val bgColor = when {
        isSelected           -> myColor.copy(alpha = 0.10f)
        otherCursorColor != null -> otherCursorColor.copy(alpha = 0.06f)
        else                 -> CocroColors.surface
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .border(borderWidth, borderColor, RectangleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Letter
        if (letter != null) {
            Text(
                text = letter.toString(),
                style = TextStyle(
                    fontFamily = FontGrid,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = CocroColors.ink,
                ),
            )
        }
        // Direction arrow for selected cell (top-right corner)
        if (isSelected && direction != null) {
            Text(
                text = if (direction == WordDirection.HORIZONTAL) "›" else "v",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 1.dp),
                style = TextStyle(
                    fontFamily = FontUi,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.sp,
                    color = myColor.copy(alpha = 0.65f),
                ),
            )
        }
        // Other participant cursor indicator (colored square, bottom-right)
        if (!isSelected && otherCursorColor != null) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(otherCursorColor)
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

// ── Color assignment ──────────────────────────────────────────────────────────

private fun buildParticipantColors(
    participants: List<Participant>,
    myUserId: String,
): Map<String, Color> {
    if (participants.isEmpty()) return emptyMap()

    // Local player always gets PLAYER_COLORS[0], others get remaining slots
    val myIndex = participants.indexOfFirst { it.userId == myUserId }
    return participants.mapIndexed { listIdx, participant ->
        val colorIdx = when {
            participant.userId == myUserId -> 0
            myIndex < 0 -> (listIdx % PLAYER_COLORS.size)
            listIdx < myIndex -> ((listIdx + 1) % PLAYER_COLORS.size)
            else -> (listIdx % PLAYER_COLORS.size)
        }.coerceIn(0, PLAYER_COLORS.lastIndex)
        participant.userId to PLAYER_COLORS[colorIdx]
    }.toMap()
}

// ── Cursor navigation helpers ─────────────────────────────────────────────────

private fun advanceCursor(current: Pair<Int, Int>, direction: WordDirection): Pair<Int, Int> =
    when (direction) {
        WordDirection.HORIZONTAL -> (current.first + 1).coerceAtMost(GRID_W - 1) to current.second
        WordDirection.VERTICAL   -> current.first to (current.second + 1).coerceAtMost(GRID_H - 1)
    }

private fun retreatCursor(current: Pair<Int, Int>, direction: WordDirection): Pair<Int, Int> =
    when (direction) {
        WordDirection.HORIZONTAL -> (current.first - 1).coerceAtLeast(0) to current.second
        WordDirection.VERTICAL   -> current.first to (current.second - 1).coerceAtLeast(0)
    }
