package com.cocro.application.grid.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class GetMyGridsUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val gridRepository: GridRepository = mock()
    private val useCase = GetMyGridsUseCase(currentUserProvider, gridRepository)

    private fun gridFor(userId: UserId, shortId: String, title: String) = Grid(
        id = UUID.randomUUID(),
        shortId = GridShareCode(shortId),
        title = GridTitle(title),
        metadata = GridMetadata(author = userId, reference = null, description = null, difficulty = "NONE"),
        width = GridWidth(5),
        height = GridHeight(5),
        cells = emptyList(),
    )

    @Test
    fun `should return empty list when user has no grids`() {
        val user = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(gridRepository.findByAuthor(user.userId)).thenReturn(emptyList())

        val result = useCase.execute()

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value).isEmpty()
    }

    @Test
    fun `should return grids belonging to the authenticated user`() {
        val user = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        val grids = listOf(
            gridFor(user.userId, "ABCDE1", "Grid One"),
            gridFor(user.userId, "ABCDE2", "Grid Two"),
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(gridRepository.findByAuthor(user.userId)).thenReturn(grids)

        val result = useCase.execute()

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val summaries = (result as CocroResult.Success).value
        assertThat(summaries).hasSize(2)
        assertThat(summaries.map { it.gridId }).containsExactly("ABCDE1", "ABCDE2")
        assertThat(summaries.map { it.title }).containsExactly("Grid One", "Grid Two")
    }

    @Test
    fun `should only return grids for the current user`() {
        val currentUser = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))
        val otherUserId = UserId.new()
        val currentUserGrid = gridFor(currentUser.userId, "MINE01", "My Grid")
        // The repository is queried with currentUser.userId, so otherUser grids are never returned.
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(currentUser)
        whenever(gridRepository.findByAuthor(currentUser.userId)).thenReturn(listOf(currentUserGrid))
        whenever(gridRepository.findByAuthor(otherUserId)).thenReturn(
            listOf(gridFor(otherUserId, "OTHER1", "Other Grid")),
        )

        val result = useCase.execute()

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val summaries = (result as CocroResult.Success).value
        assertThat(summaries).hasSize(1)
        assertThat(summaries[0].gridId).isEqualTo("MINE01")
    }
}
