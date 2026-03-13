package com.cocro.kernel.session.model

import com.cocro.kernel.auth.model.valueobject.UserId
import com.cocro.kernel.common.CocroResult
import com.cocro.kernel.grid.model.valueobject.GridShareCode
import com.cocro.kernel.session.Participant
import com.cocro.kernel.session.enum.InviteStatus
import com.cocro.kernel.session.enum.SessionStatus
import com.cocro.kernel.session.error.SessionError
import com.cocro.kernel.session.model.state.SessionGridState
import com.cocro.kernel.session.model.valueobject.SessionId
import com.cocro.kernel.session.model.valueobject.SessionShareCode
import com.cocro.kernel.session.rule.ParticipantsRule
import java.time.Instant
import com.cocro.kernel.session.model.SessionLifecycleCommand

data class Session private constructor(
    val id: SessionId,
    val shareCode: SessionShareCode,
    val creatorId: UserId,
    val gridId: GridShareCode,
    val status: SessionStatus,
    val participants: List<Participant>,
    val sessionGridState: SessionGridState,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun create(
            creatorId: UserId,
            shareCode: SessionShareCode,
            gridId: GridShareCode,
            now: Instant = Instant.now(),
        ): Session {
            val sessionId = SessionId.new()

            return Session(
                id = sessionId,
                shareCode = shareCode,
                creatorId = creatorId,
                gridId = gridId,
                status = SessionStatus.CREATING,
                participants = listOf(Participant.creator(creatorId)),
                sessionGridState = SessionGridState.initial(sessionId, gridId),
                createdAt = now,
                updatedAt = now,
            )
        }

        fun rehydrate(
            id: SessionId,
            shareCode: SessionShareCode,
            creatorId: UserId,
            gridId: GridShareCode,
            status: SessionStatus,
            participants: List<Participant>,
            sessionGridState: SessionGridState,
            createdAt: Instant,
            updatedAt: Instant,
        ): Session {
            require(participants.any { it.userId == creatorId }) {
                "Creator must be participant"
            }
            require(participants.size <= ParticipantsRule.MAX_ACTIVE_PARTICIPANTS) {
                "Too many participants"
            }

            return Session(
                id,
                shareCode,
                creatorId,
                gridId,
                status,
                participants,
                sessionGridState,
                createdAt,
                updatedAt,
            )
        }
    }

    // ---------- Lifecycle Command ----------

    fun apply(command: SessionLifecycleCommand): CocroResult<Session, SessionError> =
        when (command) {
            is SessionLifecycleCommand.Join -> applyJoin(command.actorId)
            is SessionLifecycleCommand.Leave -> applyLeave(command.actorId)
            is SessionLifecycleCommand.Start -> applyStart()
        }

    private fun applyJoin(actorId: UserId): CocroResult<Session, SessionError> {
        if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING)) {
            return err(SessionError.InvalidStatusForAction(status, "join"))
        }
        if (participants.any { it.userId == actorId }) {
            return err(SessionError.AlreadyParticipant(actorId.toString(), shareCode.value))
        }
        if (!ParticipantsRule.canJoin(participants)) {
            return err(SessionError.SessionFull)
        }
        return ok(join(actorId))
    }

    private fun applyLeave(actorId: UserId): CocroResult<Session, SessionError> {
        if (!participants.any { it.userId == actorId && it.status == InviteStatus.JOINED }) {
            return err(SessionError.UserNotParticipant(actorId.toString(), shareCode.value))
        }
        return ok(leave(actorId))
    }

    private fun applyStart(): CocroResult<Session, SessionError> {
        if (status != SessionStatus.CREATING) {
            return err(SessionError.InvalidStatusForAction(status, "start"))
        }
        if (ParticipantsRule.countActiveParticipants(participants) < 1) {
            return err(SessionError.NotEnoughParticipants)
        }
        return ok(startPlaying())
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

    fun startPlaying(now: Instant = Instant.now()): Session = copy(status = SessionStatus.PLAYING, updatedAt = now)

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
        now: Instant = Instant.now(),
    ): Session =
        copy(
            participants = participants + Participant.joined(actorId),
            updatedAt = now,
        )

    fun leave(
        actorId: UserId,
        now: Instant = Instant.now(),
    ): Session {
        val updated =
            participants.map { p ->
                if (p.userId == actorId) p.copy(status = InviteStatus.LEFT) else p
            }

        return copy(participants = updated, updatedAt = now)
    }

    fun end(
        actorId: UserId,
        now: Instant = Instant.now(),
    ): CocroResult<Session, SessionError> {
        if (!isCreator(actorId)) {
            return err(SessionError.NotCreator(creatorId.toString(), actorId.toString()))
        }

        if (status !in setOf(SessionStatus.CREATING, SessionStatus.PLAYING, SessionStatus.SCORING)) {
            return err(SessionError.InvalidStatusForAction(status, "end"))
        }

        return ok(copy(status = SessionStatus.ENDED, updatedAt = now))
    }

    fun interrupt(now: Instant = Instant.now()): Session = copy(status = SessionStatus.INTERRUPTED, updatedAt = now)

    // ---------- helpers ----------

    private fun isCreator(actorId: UserId): Boolean = actorId == creatorId

    private fun ok(session: Session): CocroResult<Session, SessionError> = CocroResult.Success(session)

    private fun err(error: SessionError): CocroResult<Session, SessionError> = CocroResult.Error(listOf(error))
}
