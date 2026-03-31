package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.dto.CellDto
import com.cocro.application.grid.dto.PatchGridDto
import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.model.Author
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.enums.CellType
import com.cocro.domain.grid.error.GridError
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.valueobject.GridDimension
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class PatchGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()

    private val useCase = PatchGridUseCase(currentUserProvider, gridRepository)

    private val authorId = UserId.new()
    private val otherUserId = UserId.new()

    private fun existingGrid() = Grid(
        id = java.util.UUID.randomUUID(),
        shortId = GridShareCode("GRPAT1"),
        metadata = GridMetadata(title = GridTitle("Test Grid"), author = Author(id = authorId, username = "TestAuthor"), reference = null, description = null, difficulty = "NONE"),
        dimension = GridDimension(width = GridWidth(5), height = GridHeight(5)),
        cells = emptyList(),
    )

    private val w = 5
    private val h = 5
    private val validCells = (0 until h).flatMap { y ->
        (0 until w).map { x ->
            CellDto(x = x, y = y, type = CellType.LETTER, letter = "A", separator = null, number = null, clues = null)
        }
    }

    private fun patchDto() = PatchGridDto(
        gridId = "GRPAT1",
        title = "New Title",
        difficulty = null,
        reference = null,
        description = null,
        width = w,
        height = h,
        cells = validCells,
    )

    @Test
    fun `should patch grid title successfully`() {
        // given
        val grid = existingGrid()
        val author = AuthenticatedUser(authorId, "Author", setOf(Role.PLAYER))
        whenever(gridRepository.findByShortId(GridShareCode("GRPAT1"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(author)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] as Grid }

        // when
        val result = useCase.execute(patchDto())

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success).isEqualTo(GridShareCode("GRPAT1"))
        verify(gridRepository).save(argThat { title == GridTitle("New Title") })
    }

    @Test
    fun `should return GridNotFound when grid does not exist`() {
        // given
        val author = AuthenticatedUser(authorId, "Author", setOf(Role.PLAYER))
        whenever(gridRepository.findByShortId(GridShareCode("GRPAT1"))).thenReturn(null)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(author)

        // when
        val result = useCase.execute(patchDto())

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GridNotFound }
        verify(gridRepository, never()).save(any())
    }

    @Test
    fun `should return UnauthorizedGridModification when user is not the author`() {
        // given
        val grid = existingGrid()
        val otherUser = AuthenticatedUser(otherUserId, "Other", setOf(Role.PLAYER))
        whenever(gridRepository.findByShortId(GridShareCode("GRPAT1"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(otherUser)

        // when
        val result = useCase.execute(patchDto())

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.UnauthorizedGridModification }
        verify(gridRepository, never()).save(any())
    }

    @Test
    fun `should allow admin to patch any grid`() {
        // given
        val grid = existingGrid()
        val adminUser = AuthenticatedUser(otherUserId, "Admin", setOf(Role.PLAYER, Role.ADMIN))
        whenever(gridRepository.findByShortId(GridShareCode("GRPAT1"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(adminUser)
        whenever(gridRepository.findByHashLetters(any())).thenReturn(null)
        whenever(gridRepository.save(any())).thenAnswer { it.arguments[0] as Grid }

        // when
        val result = useCase.execute(patchDto())

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success).value
        assertThat(success).isEqualTo(GridShareCode("GRPAT1"))
        verify(gridRepository).save(any())
    }

    @Test
    fun `should return UnauthorizedGridModification when not authenticated`() {
        // given
        val grid = existingGrid()
        whenever(gridRepository.findByShortId(GridShareCode("GRPAT1"))).thenReturn(grid)
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute(patchDto())

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.UnauthorizedGridModification }
        verify(gridRepository, never()).save(any())
    }
}
