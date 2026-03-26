package com.cocro.kernel.session.model

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.GridTemplateSnapshot
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.model.valueobject.SessionShareCode
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
    fun `Session creator is auto-joined on create`() {
        val creatorId = UserId.new()
        val session = Session.create(
            creatorId = creatorId,
            shareCode = SessionShareCode("AB12"),
            gridId = GridShareCode("GRID01"),
            gridTemplate = minimalSnapshot(),
        )
        assertThat(session.participants).anyMatch {
            it.userId == creatorId && it.status == InviteStatus.JOINED
        }
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
