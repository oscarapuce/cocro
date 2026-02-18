package com.cocro.infrastructure.persistence.mongo.grid.mapper

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.Clue
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.Letter
import com.cocro.domain.grid.model.valueobject.ClueText
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import com.cocro.domain.grid.model.valueobject.LetterValue
import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridMetadataDocument
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.enums.ClueDirection
import com.cocro.kernel.grid.enums.GridDifficulty
import com.cocro.kernel.grid.enums.SeparatorType
import com.cocro.kernel.grid.model.CellPos

fun Grid.toDocument(): GridDocument =
    GridDocument(
        id = id,
        shortId = shortId.value,
        title = title.value,
        metadata =
            GridMetadataDocument(
                author = metadata.author.toString(),
                reference = metadata.reference,
                description = metadata.description,
                difficulty = metadata.difficulty.name,
            ),
        hashLetters = hashLetters,
        width = width.value,
        height = height.value,
        cells = cells.map { it.toDocument() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun Cell.toDocument(): CellDocument =
    when (this) {
        is Cell.LetterCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "LETTER",
                letter = letter.value.value,
                separator = letter.separator.name,
                number = letter.number,
            )

        is Cell.ClueCell.SingleClueCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "CLUE_SINGLE",
                clueDirection = clue.direction.name,
                clueText = clue.text.value,
            )

        is Cell.ClueCell.DoubleClueCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "CLUE_DOUBLE",
                clueDirection = first.direction.name,
                clueText = first.text.value,
                secondClueDirection = second.direction.name,
                secondClueText = second.text.value,
            )

        is Cell.BlackCell ->
            CellDocument(
                x = pos.x,
                y = pos.y,
                type = "BLACK",
            )
    }

fun GridDocument.toDomain(): Grid =
    Grid(
        id = id,
        shortId = GridShareCode(shortId),
        title = GridTitle(title),
        metadata =
            GridMetadata(
                author = UserId.from(metadata.author),
                reference = metadata.reference,
                description = metadata.description,
                difficulty = GridDifficulty.valueOf(metadata.difficulty),
            ),
        hashLetters = hashLetters,
        width = GridWidth(width),
        height = GridHeight(height),
        cells = cells.map { it.toDomain() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun CellDocument.toDomain(): Cell {
    val pos = CellPos(x, y)

    return when (type) {
        CellType.LETTER.name ->
            Cell.LetterCell(
                pos = pos,
                letter =
                    Letter(
                        value = LetterValue(letter!!),
                        separator = SeparatorType.valueOf(separator!!),
                        number = number,
                    ),
            )

        CellType.CLUE_SINGLE.name ->
            Cell.ClueCell.SingleClueCell(
                pos = pos,
                clue =
                    Clue(
                        direction = ClueDirection.valueOf(clueDirection!!),
                        text = ClueText(clueText!!),
                    ),
            )

        CellType.CLUE_DOUBLE.name ->
            Cell.ClueCell.DoubleClueCell(
                pos = pos,
                first =
                    Clue(
                        direction = ClueDirection.valueOf(clueDirection!!),
                        text = ClueText(clueText!!),
                    ),
                second =
                    Clue(
                        direction = ClueDirection.valueOf(secondClueDirection!!),
                        text = ClueText(secondClueText!!),
                    ),
            )

        CellType.BLACK.name ->
            Cell.BlackCell(pos)

        else -> error("Unknown cell type: $type")
    }
}
