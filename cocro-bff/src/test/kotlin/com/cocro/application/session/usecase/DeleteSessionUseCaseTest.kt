package com.cocro.application.session.usecase

import com.cocro.application.session.port.HeartbeatTracker
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DeleteSessionUseCaseTest {

    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val heartbeatTracker: HeartbeatTracker = mock()

    private val useCase = DeleteSessionUseCase(sessionRepository, sessionGridStateCache, heartbeatTracker)

    private val creatorId = UserId.new()
    private val shareCode = SessionShareCode("AB12")
    private val gridId = GridShareCode("GRID01")
    private val session = Session.create(
        author = Author(id = creatorId, username = "Creator"),
        shareCode = shareCode,
        gridId = gridId,
        gridTemplate = GridTemplateSnapshot(
            shortId = gridId, title = "T", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null, globalClueWordLengths = null, cells = emptyList(),
        ),
    )

    @Test
    fun `should delete session when actor is the creator`() {
        // given
        val sessionWithParticipants = session.join(UserId.new(), "P1").join(UserId.new(), "P2")
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(sessionWithParticipants)

        // when
        val result = useCase.execute("AB12", creatorId)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        verify(sessionGridStateCache).deactivate(sessionWithParticipants.id)
        verify(sessionRepository).deleteById(sessionWithParticipants.id)
        sessionWithParticipants.participants.forEach { p ->
            verify(heartbeatTracker).remove(sessionWithParticipants.id, p.userId)
        }
    }

    @Test
    fun `should return NotCreator error when actor is not the creator`() {
        // given
        val otherUserId = UserId.new()
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(session)

        // when
        val result = useCase.execute("AB12", otherUserId)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.NotCreator }
        verifyNoInteractions(sessionGridStateCache)
        verifyNoInteractions(heartbeatTracker)
    }

    @Test
    fun `should return SessionNotFound error when session does not exist`() {
        // given
        whenever(sessionRepository.findByShareCode(shareCode)).thenReturn(null)

        // when
        val result = useCase.execute("AB12", creatorId)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.SessionNotFound }
        verifyNoInteractions(sessionGridStateCache)
        verifyNoInteractions(heartbeatTracker)
    }
}

