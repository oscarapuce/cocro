package com.cocro.presentation.websocket

import com.cocro.application.session.dto.UpdateSessionGridDto
import com.cocro.application.session.usecase.UpdateSessionGridUseCases
import com.cocro.kernel.common.CocroResult
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller

@Controller
class SessionWebSocketController(
    private val updateSessionGridUseCases: UpdateSessionGridUseCases,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Client sends to: /app/session/{shareCode}/grid
     * Server broadcasts result to: /topic/session/{shareCode}
     */
    @MessageMapping("/session/{shareCode}/grid")
    fun handleGridUpdate(
        @DestinationVariable shareCode: String,
        @Payload payload: GridUpdatePayload,
    ) {
        val dto = UpdateSessionGridDto(
            shareCode = shareCode,
            posX = payload.posX,
            posY = payload.posY,
            commandType = payload.commandType,
            letter = payload.letter,
        )


        when (val result = updateSessionGridUseCases.execute(dto)) {
            is CocroResult.Success -> logger.debug(
                "Grid update applied on session={}, pos=({},{}), command={}",
                shareCode, payload.posX, payload.posY, payload.commandType,
            )
            is CocroResult.Error -> logger.warn(
                "Grid update rejected on session={}: {}",
                shareCode, result.errors.map { it.errorCode },
            )
        }
    }
}

data class GridUpdatePayload(
    val posX: Int,
    val posY: Int,
    val commandType: String,
    val letter: Char?,
)

