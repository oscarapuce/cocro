package com.cocro.application.session.usecase

import com.cocro.application.auth.port.CurrentUserProvider
import com.cocro.application.grid.port.GridRepository
import com.cocro.application.session.dto.CreateSessionDto
import com.cocro.application.session.dto.SessionCreationSuccess
import com.cocro.application.session.port.SessionGridStateCache
import com.cocro.application.session.port.SessionRepository
import com.cocro.application.session.service.SessionCodeGenerator
import com.cocro.domain.auth.enum.Role
import com.cocro.domain.auth.model.AuthenticatedUser
import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.Grid
import com.cocro.domain.grid.model.GridMetadata
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridHeight
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.grid.model.valueobject.GridTitle
import com.cocro.domain.grid.model.valueobject.GridWidth
import java.util.UUID
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.Session
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class CreateSessionUseCaseTest {

    private val currentUserProvider: CurrentUserProvider = mock()
    private val sessionRepository: SessionRepository = mock()
    private val sessionGridStateCache: SessionGridStateCache = mock()
    private val shareCodeGenerator: SessionCodeGenerator = mock()
    private val gridRepository: GridRepository = mock()
    private val useCase = CreateSessionUseCase(
        currentUserProvider,
        sessionRepository,
        sessionGridStateCache,
        shareCodeGenerator,
        gridRepository,
    )

    private val authenticatedUser = AuthenticatedUser(UserId.new(), setOf(Role.PLAYER))

    @Test
    fun `should return SessionCreationSuccess on valid input`() {
        // given
        val dto = CreateSessionDto(gridId = "GRID01")
        val shareCode = SessionShareCode("AB12")
        val gridId = GridShareCode("GRID01")
        val grid = Grid(
            id = UUID.randomUUID(),
            shortId = gridId,
            title = GridTitle("Test Grid"),
            metadata = GridMetadata(author = authenticatedUser.userId, reference = null, description = null, difficulty = "NONE"),
            width = GridWidth(5),
            height = GridHeight(5),
            cells = emptyList(),
        )
        val snapshot = GridTemplateSnapshot(
            shortId = gridId, title = "T", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = emptyList(),
        )
        val session = Session.create(
            creatorId = authenticatedUser.userId,
            shareCode = shareCode,
            gridId = gridId,
            gridTemplate = snapshot,
        )
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)
        whenever(shareCodeGenerator.generateId()).thenReturn(shareCode)
        whenever(gridRepository.findByShortId(gridId)).thenReturn(grid)
        whenever(sessionRepository.save(any())).thenReturn(session)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        val success = (result as CocroResult.Success<SessionCreationSuccess>).value
        assertThat(success.shareCode).isEqualTo("AB12")
        verify(sessionGridStateCache).initialize(session.id, session.sessionGridState)
    }

    @Test
    fun `should return error when user is not authenticated`() {
        // given
        val dto = CreateSessionDto(gridId = "GRID01")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(null)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).contains(SessionError.Unauthorized)
        verifyNoInteractions(sessionRepository)
    }

    @Test
    fun `should return error when gridId format is invalid`() {
        // given
        val dto = CreateSessionDto(gridId = "bad!")
        whenever(currentUserProvider.currentUserOrNull()).thenReturn(authenticatedUser)

        // when
        val result = useCase.execute(dto)

        // then
        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        val errors = (result as CocroResult.Error).errors
        assertThat(errors).anyMatch { it is SessionError.InvalidGridId }
        verifyNoInteractions(sessionRepository)
    }
}
