package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.SubmitGridDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.grid.service.GridIdGenerator
import com.cocro.kernel.auth.enum.Role
import com.cocro.kernel.auth.model.AuthenticatedUser
import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.enums.CellType
import com.cocro.kernel.grid.error.GridError
import com.cocro.kernel.grid.model.valueobject.GridShareCode
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
            val grid = mock<com.cocro.kernel.grid.model.Grid>()
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
}
