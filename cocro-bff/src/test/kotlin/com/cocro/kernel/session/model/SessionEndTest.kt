package com.cocro.kernel.session.model

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionEndTest {

    private val creatorId = UserId.new()
    private val session = Session.create(
        creatorId = creatorId,
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
    fun `creator can end a PLAYING session`() {
        val playing = session.withStatus(SessionStatus.PLAYING)

        val result = playing.end(creatorId)

        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
        assertThat((result as CocroResult.Success).value.status).isEqualTo(SessionStatus.ENDED)
    }

    @Test
    fun `non-creator cannot end a session`() {
        val outsider = UserId.new()

        val result = session.end(outsider)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.NotCreator }
    }

    @Test
    fun `cannot end an already ENDED session`() {
        val ended = session.withStatus(SessionStatus.ENDED)

        val result = ended.end(creatorId)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }

    @Test
    fun `cannot end an INTERRUPTED session`() {
        val interrupted = session.withStatus(SessionStatus.INTERRUPTED)

        val result = interrupted.end(creatorId)

        assertThat(result).isInstanceOf(CocroResult.Error::class.java)
        assertThat((result as CocroResult.Error).errors).anyMatch { it is SessionError.InvalidStatusForAction }
    }
}
