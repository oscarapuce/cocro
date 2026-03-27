package com.cocro.infrastructure.persistence.mongo.grid.mapper

import com.cocro.infrastructure.persistence.mongo.grid.document.CellDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridDocument
import com.cocro.infrastructure.persistence.mongo.grid.document.GridMetadataDocument
import com.cocro.domain.auth.model.valueobject.UserId
import java.util.UUID
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.enums.ClueDirection
import com.cocro.domain.grid.enums.SeparatorType
import com.cocro.domain.grid.model.Cell
import com.cocro.domain.grid.model.CellPos
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

fun Grid.toDocument(): GridDocument =
    GridDocument(
        id = id.toString(),
        shortId = shortId.value,
        title = title.value,
        metadata = GridMetadataDocument(
            author = metadata.author.toString(),
            reference = metadata.reference,
            description = metadata.description,
            difficulty = metadata.difficulty,
            globalClueLabel = metadata.globalClueLabel,
            globalClueWordLengths = metadata.globalClueWordLengths,
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
            CellDocument(x = pos.x, y = pos.y, type = "BLACK")
    }

fun GridDocument.toDomain(): Grid =
    Grid(
        id = UUID.fromString(id),
        shortId = GridShareCode(shortId),
        title = GridTitle(title),
        metadata = GridMetadata(
            author = UserId.from(metadata.author),
            reference = metadata.reference,
            description = metadata.description,
            difficulty = metadata.difficulty,
            globalClueLabel = metadata.globalClueLabel,
            globalClueWordLengths = metadata.globalClueWordLengths,
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
                pos,
                Letter(
                    value = LetterValue(letter!!),
                    separator = SeparatorType.valueOf(separator!!),
                    number = number,
                ),
            )
        CellType.CLUE_SINGLE.name ->
            Cell.ClueCell.SingleClueCell(
                pos,
                Clue(direction = ClueDirection.valueOf(clueDirection!!), text = ClueText(clueText!!)),
            )
        CellType.CLUE_DOUBLE.name ->
            Cell.ClueCell.DoubleClueCell(
                pos,
                Clue(direction = ClueDirection.valueOf(clueDirection!!), text = ClueText(clueText!!)),
                Clue(direction = ClueDirection.valueOf(secondClueDirection!!), text = ClueText(secondClueText!!)),
            )
        CellType.BLACK.name -> Cell.BlackCell(pos)
        else -> error("Unknown cell type: $type")
    }
}
