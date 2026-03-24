package com.cocro.presentation.rest.session

import com.cocro.application.session.dto.GridTemplateCellDto
import com.cocro.application.session.dto.GridTemplateDto
import com.cocro.application.session.usecase.CheckGridUseCase
import com.cocro.application.session.usecase.CreateSessionUseCase
import com.cocro.application.session.usecase.GetSessionGridTemplateUseCase
import com.cocro.application.session.usecase.GetSessionStateUseCase
import com.cocro.application.session.usecase.JoinSessionUseCase
import com.cocro.application.session.usecase.LeaveSessionUseCase
import com.cocro.application.session.usecase.StartSessionUseCase
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.session.error.SessionError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class SessionGridTemplateControllerTest {

    private val createSessionUseCase: CreateSessionUseCase = mock()
    private val joinSessionUseCase: JoinSessionUseCase = mock()
    private val leaveSessionUseCase: LeaveSessionUseCase = mock()
    private val startSessionUseCase: StartSessionUseCase = mock()
    private val getSessionStateUseCase: GetSessionStateUseCase = mock()
    private val getSessionGridTemplateUseCase: GetSessionGridTemplateUseCase = mock()
    private val checkGridUseCase: CheckGridUseCase = mock()

    private lateinit var controller: SessionController

    @BeforeEach
    fun setUp() {
        controller = SessionController(
            createSessionUseCase,
            joinSessionUseCase,
            leaveSessionUseCase,
            startSessionUseCase,
            getSessionStateUseCase,
            getSessionGridTemplateUseCase,
            checkGridUseCase,
        )
    }

    @Test
    fun `getSessionGridTemplate should return 200 with dto on success`() {
        val dto = GridTemplateDto(
            title = "Ma Grille",
            width = 5,
            height = 5,
            difficulty = "2",
            author = "Oscar",
            reference = null,
            description = null,
            globalClueLabel = null,
            globalClueWordLengths = null,
            cells = listOf(
                GridTemplateCellDto(x = 1, y = 0, type = "LETTER", separator = "NONE", number = 1, clues = null),
                GridTemplateCellDto(x = 0, y = 0, type = "CLUE_SINGLE", separator = null, number = null,
                    clues = listOf(com.cocro.application.session.dto.GridTemplateClueDto("RIGHT", "Définition"))),
            ),
        )
        whenever(getSessionGridTemplateUseCase.execute("AB12")).thenReturn(CocroResult.Success(dto))

        val response = controller.getSessionGridTemplate("AB12")

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isEqualTo(dto)
    }

    @Test
    fun `getSessionGridTemplate should return 401 when Unauthorized`() {
        whenever(getSessionGridTemplateUseCase.execute("AB12"))
            .thenReturn(CocroResult.Error(listOf(SessionError.Unauthorized)))

        val response = controller.getSessionGridTemplate("AB12")

        assertThat(response.statusCode.value()).isEqualTo(401)
    }

    @Test
    fun `getSessionGridTemplate should return 403 when session not found`() {
        whenever(getSessionGridTemplateUseCase.execute("ZZZZ"))
            .thenReturn(CocroResult.Error(listOf(SessionError.SessionNotFound("ZZZZ"))))

        val response = controller.getSessionGridTemplate("ZZZZ")

        assertThat(response.statusCode.value()).isEqualTo(403)
    }

    @Test
    fun `getSessionGridTemplate should return 403 when user is not a participant`() {
        whenever(getSessionGridTemplateUseCase.execute("AB12"))
            .thenReturn(CocroResult.Error(listOf(SessionError.UserNotParticipant("user-id", "AB12"))))

        val response = controller.getSessionGridTemplate("AB12")

        assertThat(response.statusCode.value()).isEqualTo(403)
    }

    @Test
    fun `getSessionGridTemplate should return 404 when reference grid not found`() {
        whenever(getSessionGridTemplateUseCase.execute("AB12"))
            .thenReturn(CocroResult.Error(listOf(SessionError.ReferenceGridNotFound("GRID01"))))

        val response = controller.getSessionGridTemplate("AB12")

        assertThat(response.statusCode.value()).isEqualTo(404)
    }
}
