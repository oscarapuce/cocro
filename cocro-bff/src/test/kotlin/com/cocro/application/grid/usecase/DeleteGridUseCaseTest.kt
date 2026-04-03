package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.common.model.Author
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class DeleteGridUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()
    private val sessionRepository: SessionRepository = mock()

    private val useCase = DeleteGridUseCase(currentUserProvider, gridRepository, sessionRepository)

    private val userId = UserId.new()
    private val user = AuthenticatedUser(userId, "TestUser", setOf(Role.PLAYER))
    private val gridId = GridShareCode("GRID01")
    private val grid = Grid(
        id = UUID.randomUUID(),
        shortId = gridId,
        metadata = GridMetadata(
            title = GridTitle("Test Grid"),
            author = Author(id = userId, username = "TestUser"),
            reference = null,
            description = null,
            difficulty = "NONE",
        ),
        dimension = GridDimension(width = GridWidth(5), height = GridHeight(5)),
        cells = emptyList(),
    )

    @Test
    fun `should delete grid when user is the author`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(grid)
        whenever(sessionRepository.existsByGridIdAndActiveStatus(gridId)).thenReturn(false)

        val result = useCase.execute("GRID01")

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        verify(gridRepository).deleteByShortId(gridId)
    }

    @Test
    fun `should return error when grid not found`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(null)

        val result = useCase.execute("GRID01")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GridNotFound }
    }

    @Test
    fun `should return error when user is not the author`() {
        val otherUser = AuthenticatedUser(UserId.new(), "Other", setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(otherUser)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(grid)

        val result = useCase.execute("GRID01")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.UnauthorizedGridModification }
    }

    @Test
    fun `should return error when grid has active sessions`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(grid)
        whenever(sessionRepository.existsByGridIdAndActiveStatus(gridId)).thenReturn(true)

        val result = useCase.execute("GRID01")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is GridError.GridHasActiveSessions }
    }

    @Test
    fun `should return error when user is not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute("GRID01")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        verifyNoInteractions(gridRepository)
    }
}
