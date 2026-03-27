package com.cocro.domain.session.model

import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.cocro.domain.auth.model.valueobject.UserId

class SessionEndTest {

    private val session = Session.create(
        creatorId = UserId.new(),
        shareCode = SessionShareCode("AB12"),
        gridId = GridShareCode("GRID01"),
        gridTemplate = GridTemplateSnapshot(
            shortId = GridShareCode("GRID01"),
            title = "Test", width = 5, height = 5,
            difficulty = null, author = null, reference = null,
            description = null, globalClueLabel = null,
            globalClueWordLengths = null, cells = emptyList()
        ),
    )

    private fun Session.withStatus(status: SessionStatus): Session =
        Session.rehydrate(id, shareCode, creatorId, gridId, status, participants, sessionGridState, createdAt, updatedAt, gridTemplate = gridTemplate)

    @Test
    fun `can end a PLAYING session`() {
        val result = session.end()

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value.status).isEqualTo(SessionStatus.ENDED)
    }

    @Test
    fun `cannot end an already ENDED session`() {
        val ended = session.withStatus(SessionStatus.ENDED)

        val result = ended.end()

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }

    @Test
    fun `cannot end an INTERRUPTED session`() {
        val interrupted = session.withStatus(SessionStatus.INTERRUPTED)

        val result = interrupted.end()

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }
}
