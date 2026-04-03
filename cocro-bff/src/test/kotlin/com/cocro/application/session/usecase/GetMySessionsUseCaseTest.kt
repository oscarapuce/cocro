package com.cocro.application.session.usecase

import com.cocro.application.session.port.SessionRepository
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetMySessionsUseCaseTest {

    private val sessionRepository: SessionRepository = mock()
    private val useCase = GetMySessionsUseCase(sessionRepository)

    private val userId = UserId.new()
    private val otherUserId = UserId.new()
    private val gridId = GridShareCode("GRID01")

    private fun createTestSession(author: Author, shareCode: String): Session =
        Session.create(
            author = author,
            shareCode = SessionShareCode(shareCode),
            gridId = gridId,
            gridTemplate = GridTemplateSnapshot(
                shortId = gridId, title = "T", width = 5, height = 5,
                difficulty = null, author = null, reference = null,
                description = null, globalClueLabel = null, globalClueWordLengths = null, cells = emptyList(),
            ),
        )

    @Test
    fun `should return created sessions with role CREATOR`() {
        val session = createTestSession(Author(id = userId, username = "Me"), "AB12")
        whenever(sessionRepository.findByCreator(userId)).thenReturn(listOf(session))
        whenever(sessionRepository.findByParticipantUserId(userId)).thenReturn(emptyList())
        val result = useCase.execute(userId)
        assertThat(result).hasSize(1)
        assertThat(result[0].role).isEqualTo("CREATOR")
        assertThat(result[0].shareCode).isEqualTo("AB12")
    }

    @Test
    fun `should return joined sessions with role PARTICIPANT`() {
        val otherAuthor = Author(id = otherUserId, username = "Other")
        val session = createTestSession(otherAuthor, "CD34").join(userId, "Me")
        whenever(sessionRepository.findByCreator(userId)).thenReturn(emptyList())
        whenever(sessionRepository.findByParticipantUserId(userId)).thenReturn(listOf(session))
        val result = useCase.execute(userId)
        assertThat(result).hasSize(1)
        assertThat(result[0].role).isEqualTo("PARTICIPANT")
    }

    @Test
    fun `should not duplicate sessions where user is both creator and participant`() {
        val session = createTestSession(Author(id = userId, username = "Me"), "AB12").join(userId, "Me")
        whenever(sessionRepository.findByCreator(userId)).thenReturn(listOf(session))
        whenever(sessionRepository.findByParticipantUserId(userId)).thenReturn(listOf(session))
        val result = useCase.execute(userId)
        assertThat(result).hasSize(1)
        assertThat(result[0].role).isEqualTo("CREATOR")
    }

    @Test
    fun `should return empty list when user has no sessions`() {
        whenever(sessionRepository.findByCreator(userId)).thenReturn(emptyList())
        whenever(sessionRepository.findByParticipantUserId(userId)).thenReturn(emptyList())
        val result = useCase.execute(userId)
        assertThat(result).isEmpty()
    }

    @Test
    fun `should return sessions sorted by updatedAt descending`() {
        val session1 = createTestSession(Author(id = userId, username = "Me"), "AB12")
        val session2 = createTestSession(Author(id = userId, username = "Me"), "CD34")
        whenever(sessionRepository.findByCreator(userId)).thenReturn(listOf(session1, session2))
        whenever(sessionRepository.findByParticipantUserId(userId)).thenReturn(emptyList())
        val result = useCase.execute(userId)
        assertThat(result).hasSize(2)
        assertThat(result[0].updatedAt >= result[1].updatedAt).isTrue()
    }
}
