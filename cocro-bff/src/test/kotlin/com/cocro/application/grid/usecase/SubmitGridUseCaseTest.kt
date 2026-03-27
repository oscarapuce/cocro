package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.service.GridIdGenerator
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.grid.model.valueobject.GridShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SubmitGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()
    private val gridIdGenerator: GridIdGenerator = mock()

    private val useCase = SubmitGridUseCase(
        currentUserProvider,
        gridRepository,
        gridIdGenerator,
    )

    private val authenticatedUser = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))

    private fun validDto(): SubmitGridDto {
        val w = 5
        val h = 5
        val cells = (0 until h).flatMap { y ->
            (0 until w).map { x ->
                if (x == w - 1 && y == h - 1) {
                    CellDto(x = x, y = y, type = CellType.BLACK, letter = null, separator = null, number = null, clues = null)
                } else {
                    CellDto(x = x, y = y, type = CellType.LETTER, letter = "A", separator = null, number = null, clues = null)
                }
            }
        }
        return SubmitGridDto(
            title = "Test Grid Title",
            difficulty = "NONE",
            reference = null,
            description = null,
            width = w,
            height = h,
            cells = cells,
        )
    }

    @Test
    fun `should submit grid successfully`() {
        // given
        val dto = validDto()
        val shareCode = GridShareCode("GRD001")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(gridIdGenerator.generateId()).thenReturn(shareCode)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] }

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success).isEqualTo(shareCode)
        verify(gridRepository).save(any())
    }

    @Test
    fun `should return UnauthorizedGridCreation when user is not authenticated`() {
        // given
        val dto = validDto()
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(GridError.UnauthorizedGridCreation)
    }

    @Test
    fun `should return DuplicateLetterHash when grid with same hash exists`() {
        // given
        val dto = validDto()
        val shareCode = GridShareCode("GRD001")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(gridIdGenerator.generateId()).thenReturn(shareCode)
        whenever(gridRepository.findByHashLetters(any())).thenAnswer {
            val grid = mock<com.cocro.domain.grid.model.Grid>()
            whenever(grid.hashLetters).thenReturn(it.arguments[0] as Long)
            whenever(grid.shortId).thenReturn(GridShareCode("OTHER1"))
            grid
        }

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.DuplicateLetterHash }
    }

    @Test
    fun `should return validation error when title is empty`() {
        // given
        val dto = validDto().copy(title = "")

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.TitleInvalid }
        verifyNoInteractions(gridRepository)
    }

    private fun validDtoWithNumberedCells(
        wordLengths: List<Int>,
        label: String = "Qui suis-je ?",
    ): SubmitGridDto {
        val base = validDto()
        // Number consecutive cells starting at 1
        var n = 1
        val cells = base.cells.map { cell ->
            if (cell.type == CellType.LETTER && n <= wordLengths.sum()) {
                cell.copy(number = n++)
            } else {
                cell
            }
        }
        return base.copy(
            cells = cells,
            globalClueLabel = label,
            globalClueWordLengths = wordLengths,
        )
    }

    @Test
    fun `submit with valid global clue succeeds`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(gridIdGenerator.generateId()).thenReturn(GridShareCode("ABCDEF"))
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] as com.cocro.domain.grid.model.Grid }

        val dto = validDtoWithNumberedCells(listOf(2, 1))
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    }

    @Test
    fun `submit with global clue but missing label returns GlobalClueLabelMissing`() {
        val dto = validDtoWithNumberedCells(listOf(2, 1), label = "")
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GlobalClueLabelMissing }
    }

    @Test
    fun `submit with empty word lengths list returns GlobalClueNoWords`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        val dto = validDto().copy(
            globalClueLabel = "Qui suis-je ?",
            globalClueWordLengths = emptyList(),
        )
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GlobalClueNoWords }
    }

    @Test
    fun `submit with word length of 0 returns GlobalClueWordLengthInvalid`() {
        val dto = validDtoWithNumberedCells(listOf(0, 2))
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GlobalClueWordLengthInvalid && it.index == 0 }
    }

    @Test
    fun `submit with letter count mismatch returns GlobalClueLetterCountMismatch`() {
        // Request 5 letters but only number 3 cells
        val base = validDto()
        val cells = base.cells.mapIndexed { i, cell ->
            if (i < 3 && cell.type == CellType.LETTER) cell.copy(number = i + 1) else cell
        }
        val dto = base.copy(
            cells = cells,
            globalClueLabel = "Qui suis-je ?",
            globalClueWordLengths = listOf(3, 2), // sum = 5
        )
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GlobalClueLetterCountMismatch }
    }

    @Test
    fun `submit with non-consecutive numbering returns GlobalClueNumberingInvalid`() {
        val base = validDto()
        // Number 3 cells but with a gap: 1, 2, 4 (missing 3)
        val numbers = listOf(1, 2, 4)
        var idx = 0
        val cells = base.cells.map { cell ->
            if (cell.type == CellType.LETTER && idx < numbers.size) {
                cell.copy(number = numbers[idx++])
            } else {
                cell
            }
        }
        val dto = base.copy(
            cells = cells,
            globalClueLabel = "Qui suis-je ?",
            globalClueWordLengths = listOf(2, 1), // sum = 3
        )
        val result = useCase.execute(dto)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GlobalClueNumberingInvalid }
    }
}
