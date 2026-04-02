package com.cocro.domain.session.model

import com.cocro.domain.auth.model.valueobject.UserId
import com.cocro.domain.common.CocroResult
import com.cocro.domain.common.model.Author
import com.cocro.domain.grid.model.GridTemplateSnapshot
import com.cocro.domain.grid.model.valueobject.GridShareCode
import com.cocro.domain.session.Participant
import com.cocro.domain.session.enum.ParticipantStatus
import com.cocro.domain.session.enum.SessionStatus
import com.cocro.domain.session.error.SessionError
import com.cocro.domain.session.model.state.SessionGridState
import com.cocro.domain.session.model.valueobject.SessionId
import com.cocro.domain.session.model.valueobject.SessionShareCode
import com.cocro.domain.session.rule.ParticipantsRule
import java.time.Instant
import com.cocro.domain.session.model.SessionLifecycleCommand

data class Session private constructor(
    val id: SessionId,
    val shareCode: SessionShareCode,
    val author: Author,
    val gridId: GridShareCode,
    val status: SessionStatus,
    val participants: List<Participant>,
    val sessionGridState: SessionGridState,
    val gridTemplate: GridTemplateSnapshot?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    // Rétro-compat helper
    val creatorId: UserId get() = author.id

    companion object {
        fun create(
            author: Author,
            shareCode: SessionShareCode,
            gridId: GridShareCode,
            gridTemplate: GridTemplateSnapshot,
            now: Instant = Instant.now(),
        ): Session {
            val sessionId = SessionId.new()

            return Session(
                id = sessionId,
                shareCode = shareCode,
                author = author,
                gridId = gridId,
                status = SessionStatus.PLAYING,
                participants = emptyList(),
                sessionGridState = SessionGridState.initial(sessionId, gridId),
                gridTemplate = gridTemplate,
                createdAt = now,
                updatedAt = now,
            )
        }

        fun rehydrate(
            id: SessionId,
            shareCode: SessionShareCode,
            author: Author,
            gridId: GridShareCode,
            status: SessionStatus,
            participants: List<Participant>,
            sessionGridState: SessionGridState,
            createdAt: Instant,
            updatedAt: Instant,
            gridTemplate: GridTemplateSnapshot? = null,
        ): Session {
            require(ParticipantsRule.countActiveParticipants(participants) <= ParticipantsRule.MAX_ACTIVE_PARTICIPANTS) {
                "Too many active participants"
            }

            return Session(
                id,
                shareCode,
                author,
                gridId,
                status,
                participants,
                sessionGridState,
                gridTemplate,
                createdAt,
                updatedAt,
            )
        }
    }

    // ---------- Lifecycle Command ----------

    fun apply(command: SessionLifecycleCommand): CocroResult<Session, SessionError> =
        when (command) {
            is SessionLifecycleCommand.Join -> applyJoin(command.actorId, command.username)
            is SessionLifecycleCommand.Leave -> applyLeave(command.actorId)
        }

    private fun applyJoin(actorId: UserId, username: String): CocroResult<Session, SessionError> {
        if (status !in setOf(SessionStatus.PLAYING, SessionStatus.INTERRUPTED)) {
            return err(SessionError.InvalidStatusForAction(status, "join"))
        }
        // Only JOINED participants block a re-join; LEFT participants may rejoin
        if (participants.any { it.userId == actorId && it.status == ParticipantStatus.JOINED }) {
            return err(SessionError.AlreadyParticipant(actorId.toString(), shareCode.value))
        }
        // LEFT participant does not count toward capacity (they're rejoining, not adding a new slot)
        val isRejoin = participants.any { it.userId == actorId && it.status == ParticipantStatus.LEFT }
        if (!isRejoin && !ParticipantsRule.canJoin(participants)) {
            return err(SessionError.SessionFull)
        }
        val updated = join(actorId, username)
        // Resume INTERRUPTED → PLAYING when first participant joins
        val resumed = if (status == SessionStatus.INTERRUPTED) updated.copy(status = SessionStatus.PLAYING) else updated
        return ok(resumed)
    }

    private fun applyLeave(actorId: UserId): CocroResult<Session, SessionError> {
        if (!participants.any { it.userId == actorId && it.status == ParticipantStatus.JOINED }) {
            return err(SessionError.UserNotParticipant(actorId.toString(), shareCode.value))
        }
        return ok(leave(actorId))
    }

    // ---------- Commands / Rules ----------

//    fun selectGrid(
//        actorId: UserId,
//        gridId: GridShareCode,
//        now: Instant = Instant.now(),
//    ): CocroResult<Session, SessionError> {
//        if (!isCreator(actorId)) {
//            return err(SessionError.NotCreator(creatorId.toString(), actorId.toString()))
//        }
//
//        if (status != SessionStatus.CREATING) {
//            return err(SessionError.InvalidStatusForAction(status, "selectGrid"))
//        }
//
//        return ok(copy(gridId = gridId, updatedAt = now))
//    }

//    fun invite(
//        actorId: UserId,
//        invitedUserId: UserId,
//        now: Instant = Instant.now(),
//    ): CocroResult<Session, SessionError> {
//        if (!isCreator(actorId)) {
//            return err(SessionError.NotCreator(creatorId.toString(), actorId.toString()))
//        }
//
//        if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING)) {
//            return err(SessionError.InvalidStatusForAction(status, "invite"))
//        }
//
//        if (participants.any { it.userId == invitedUserId }) {
//            return err(SessionError.AlreadyParticipant(invitedUserId.toString()))
//        }
//
//        if (participants.size >= MAX_PARTICIPANTS) {
//            return err(SessionError.SessionFull)
//        }
//
//        return ok(
//            copy(
//                participants = participants + Participant.invited(invitedUserId),
//                updatedAt = now,
//            ),
//        )
//    }

    fun join(
        actorId: UserId,
        username: String = "Inconnu",
        now: Instant = Instant.now(),
    ): Session {
        val leftIndex = participants.indexOfFirst { it.userId == actorId && it.status == ParticipantStatus.LEFT }
        val updatedParticipants = if (leftIndex >= 0) {
            participants.mapIndexed { i, p -> if (i == leftIndex) p.copy(status = ParticipantStatus.JOINED) else p }
        } else {
            participants + Participant.joined(actorId, username)
        }
        return copy(participants = updatedParticipants, updatedAt = now)
    }

    fun leave(
        actorId: UserId,
        now: Instant = Instant.now(),
    ): Session {
        val updated =
            participants.map { p ->
                if (p.userId == actorId) p.copy(status = ParticipantStatus.LEFT) else p
            }

        return copy(participants = updated, updatedAt = now)
    }

    fun end(now: Instant = Instant.now()): CocroResult<Session, SessionError> {
        if (status != SessionStatus.PLAYING) {
            return err(SessionError.InvalidStatusForAction(status, "end"))
        }

        return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
    }

    fun interrupt(now: Instant = Instant.now()): Session = copy(status = SessionStatus.INTERRUPTED, updatedAt = now)

    // ---------- helpers ----------

    private fun ok(session: Session): CocroResult<Session, SessionError> = CocroResult.Success(session)

    private fun err(error: SessionError): CocroResult<Session, SessionError> = CocroResult.Error(listOf(error))
}
