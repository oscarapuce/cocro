package com.cocro.domain.session.error

import com.cocro.domain.common.error.CocroError
import com.cocro.domain.common.error.ErrorCode
import com.cocro.domain.session.enum.SessionStatus

sealed interface SessionError : CocroError {
    data class InvalidLetter(
        val letter: String,
    ) : SessionError {
        override val errorCode = ErrorCode.GRID_STATE_INVALID_LETTER

        override fun context(): Map<String, String> = mapOf("letter" to letter)
    }

    object InvalidPosition : SessionError {
        override val errorCode = ErrorCode.GRID_STATE_INVALID_POSITION
    }

    data class InvalidCommand(
        val command: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_INVALID_COMMAND

        override fun context(): Map<String, String> = mapOf("command" to command)
    }

    object GridStateNotInitialized : SessionError {
        override val errorCode = ErrorCode.GRID_STATE_NOT_INITIALIZED
    }

    object SessionFull : SessionError {
        override val errorCode = ErrorCode.SESSION_FULL
    }

    object Unauthorized : SessionError {
        override val errorCode = ErrorCode.SESSION_CANNOT_CREATE_WHEN_UNAUTHORIZED
    }

    data class SessionNotFound(
        val sessionId: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_NOT_FOUND

        override fun context(): Map<String, String> = mapOf("sessionId" to sessionId)
    }

    data class InvalidShareCode(
        val shareCode: String?,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_INVALID_SHARE_CODE

        override fun context(): Map<String, String> =
            if (shareCode == null) {
                mapOf(
                    "shareCode" to "null",
                )
            } else {
                mapOf(
                    "shareCode" to shareCode,
                )
            }
    }

    data class InvalidGridId(
        val gridId: String?,
    ) : SessionError {
        override val errorCode = ErrorCode.GRID_INVALID_GRID_ID

        override fun context(): Map<String, String> =
            if (gridId == null) {
                mapOf(
                    "gridId" to "null",
                )
            } else {
                mapOf(
                    "gridId" to gridId,
                )
            }
    }

    data class InvalidStatusForAction(
        val status: SessionStatus,
        val action: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_INVALID_STATUS_FOR_ACTION

        override fun context(): Map<String, String> =
            mapOf(
                "status" to status.name,
                "action" to action,
            )
    }

    data class UserNotParticipant(
        val userId: String,
        val sessionId: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_USER_NOT_IN_SESSION

        override fun context(): Map<String, String> = mapOf("userId" to userId, "sessionId" to sessionId)
    }

    data class AlreadyParticipant(
        val userId: String,
        val sessionId: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_ALREADY_PARTICIPANT

        override fun context(): Map<String, String> = mapOf("userId" to userId, "sessionId" to sessionId)
    }

    data class NotInSession(
        val userId: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_USER_NOT_IN_SESSION

        override fun context(): Map<String, String> = mapOf("userId" to userId)
    }

    data class NotInvited(
        val userId: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_NOT_INVITED

        override fun context(): Map<String, String> = mapOf("userId" to userId)
    }

    object GridNotSelected : SessionError {
        override val errorCode = ErrorCode.SESSION_GRID_NOT_SELECTED
    }

    object NotEnoughParticipants : SessionError {
        override val errorCode = ErrorCode.SESSION_NOT_ENOUGH_PARTICIPANTS
    }

    data class ReferenceGridNotFound(
        val gridCode: String,
    ) : SessionError {
        override val errorCode = ErrorCode.SESSION_REFERENCE_GRID_NOT_FOUND

        override fun context(): Map<String, String> = mapOf("gridCode" to gridCode)
    }

    object ConcurrentModification : SessionError {
        override val errorCode = ErrorCode.SESSION_GRID_CONCURRENT_MODIFICATION
    }
}
