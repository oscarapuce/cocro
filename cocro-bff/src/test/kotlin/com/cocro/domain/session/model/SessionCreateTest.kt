package com.cocro.domain.session.model

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.model.valueobject.SessionShareCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionCreateTest {

    private fun minimalSnapshot() = GridTemplateSnapshot(
        shortId = GridShareCode("GRID01"),
        title = "Test", width = 5, height = 5,
        difficulty = null, author = null, reference = null,
        description = null, globalClueLabel = null,
        globalClueWordLengths = null, cells = emptyList()
    )

    @Test
    fun `Session create produces PLAYING status immediately`() {
        val session = Session.create(
            creatorId = UserId.new(),
            shareCode = SessionShareCode("AB12"),
            gridId = GridShareCode("GRID01"),
            gridTemplate = minimalSnapshot(),
        )
        assertThat(session.status).isEqualTo(SessionStatus.PLAYING)
    }

    @Test
    fun `Session create embeds grid template`() {
        val snapshot = minimalSnapshot()
        val session = Session.create(
            creatorId = UserId.new(),
            shareCode = SessionShareCode("AB12"),
            gridId = GridShareCode("GRID01"),
            gridTemplate = snapshot,
        )
        assertThat(session.gridTemplate).isEqualTo(snapshot)
    }

    @Test
    fun `Session create produces empty participants list`() {
        val session = Session.create(
            creatorId = UserId.new(),
            shareCode = SessionShareCode("AB12"),
            gridId = GridShareCode("GRID01"),
            gridTemplate = minimalSnapshot(),
        )
        assertThat(session.participants).isEmpty()
    }

    @Test
    fun `Session apply Join succeeds on PLAYING session`() {
        val session = Session.create(
            creatorId = UserId.new(),
            shareCode = SessionShareCode("AB12"),
            gridId = GridShareCode("GRID01"),
            gridTemplate = minimalSnapshot(),
        )
        val result = session.apply(SessionLifecycleCommand.Join(UserId.new()))
        assertThat(result).isInstanceOf(CocroResult.Success::class.java)
    }
}
