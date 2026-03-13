package com.cocro.network.stomp

private const val NULL_BYTE = "\u0000"
private const val LF = "\n"

enum class StompCommand {
    // client → server
    CONNECT, SEND, SUBSCRIBE, UNSUBSCRIBE, DISCONNECT,
    // server → client
    CONNECTED, MESSAGE, RECEIPT, ERROR,
}

data class StompFrame(
    val command: StompCommand,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    fun serialize(): String = buildString {
        append(command.name)
        append(LF)
        headers.forEach { (k, v) -> append("$k:$v$LF") }
        append(LF)
        if (body != null) append(body)
        append(NULL_BYTE)
    }

    companion object {
        fun parse(raw: String): StompFrame {
            // Strip trailing null byte(s)
            val text = raw.trimEnd('\u0000', '\r')
            val headerBodySplit = text.indexOf("\n\n")

            val headerPart = if (headerBodySplit >= 0) text.substring(0, headerBodySplit) else text
            val body = if (headerBodySplit >= 0) text.substring(headerBodySplit + 2).ifEmpty { null } else null

            val lines = headerPart.split(LF)
            val command = StompCommand.entries.firstOrNull { it.name == lines[0].trim() }
                ?: StompCommand.ERROR

            val headers = lines.drop(1)
                .filter { it.contains(':') }
                .associate { line ->
                    val colon = line.indexOf(':')
                    line.substring(0, colon).trim() to line.substring(colon + 1).trim()
                }

            return StompFrame(command, headers, body)
        }
    }
}
