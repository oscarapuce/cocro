package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.session.dto.SessionFullDto
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.Session
import com.cocro.domain.common.model.Author
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SynchroniseSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()

    private val useCase = SynchroniseSessionUseCase(currentUserProvider, sessionRepository, sessionGridStateCache)

    private val userId = UserId.new()
    private val user = AuthenticatedUser(userId, "TestUser", setOf(Role.PLAYER))
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val template = GridTemplateSnapshot(
        shortId = gridId, title = "T", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null, globalClueWordLengths = null, cells = emptyList(),
    )
    private val session = Session.create(author = Author(id = UserId.new(), username = "TestUser"), shareCode = shareCode, gridId = gridId, gridTemplate = template)
        .join(userId)

    @Test
    fun `should flush cache and return SessionFullDto`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(user)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)
        whenever(sessionGridStateCache.get(session.id)).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val dto = (result as CocroResult.Success<SessionFullDto>).value
        assertThat(dto.shareCode).isEqualTo("AB12")
        verify(sessionRepository).updateGridState(session.id, session.sessionGridState)
        verify(sessionGridStateCache).markFlushed(session.id, session.sessionGridState.revision.value)
    }

    @Test
    fun `should return Unauthorized when user not authenticated`() {
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).contains(SessionError.Unauthorized)
    }

    @Test
    fun `should return UserNotParticipant when caller is not in session`() {
        val outsider = AuthenticatedUser(UserId.new(), "TestUser", setOf(Role.PLAYER))
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(outsider)
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        val result = useCase.execute("AB12")

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.UserNotParticipant }
    }
}
