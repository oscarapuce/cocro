package com.cocro.network.stomp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StompFrameTest {

    @Nested
    inner class Serialize {

        @Test
        fun `CONNECT frame with headers and no body produces correct wire format`() {
            val frame = StompFrame(
                command = StompCommand.CONNECT,
                headers = mapOf("accept-version" to "1.2", "host" to "localhost"),
            )

            val result = frame.serialize()

            assertThat(result).isEqualTo("CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000")
        }

        @Test
        fun `SEND frame with body includes body between double-LF and null byte`() {
            val frame = StompFrame(
                command = StompCommand.SEND,
                headers = mapOf("destination" to "/topic/session"),
                body = """{"type":"MOVE"}""",
            )

            val result = frame.serialize()

            assertThat(result).isEqualTo(
                "SEND\ndestination:/topic/session\n\n{\"type\":\"MOVE\"}\u0000"
            )
        }

        @Test
        fun `frame with no headers produces command, blank line, and null byte`() {
            val frame = StompFrame(command = StompCommand.DISCONNECT)

            val result = frame.serialize()

            assertThat(result).isEqualTo("DISCONNECT\n\n\u0000")
        }

        @Test
        fun `frame with multiple headers writes each header on its own line`() {
            val frame = StompFrame(
                command = StompCommand.SUBSCRIBE,
                headers = mapOf(
                    "id" to "sub-0",
                    "destination" to "/user/queue/events",
                    "ack" to "auto",
                ),
            )

            val result = frame.serialize()

            val lines = result.split("\n")
            assertThat(lines[0]).isEqualTo("SUBSCRIBE")
            assertThat(lines[1]).isEqualTo("id:sub-0")
            assertThat(lines[2]).isEqualTo("destination:/user/queue/events")
            assertThat(lines[3]).isEqualTo("ack:auto")
            assertThat(lines[4]).isEqualTo("")       // blank separator line
            assertThat(result).endsWith("\u0000")
        }
    }

    @Nested
    inner class Parse {

        @Test
        fun `CONNECTED frame is parsed with correct command, headers, and no body`() {
            val raw = "CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.command).isEqualTo(StompCommand.CONNECTED)
            assertThat(frame.headers).containsEntry("version", "1.2")
            assertThat(frame.headers).containsEntry("heart-beat", "0,0")
            assertThat(frame.body).isNull()
        }

        @Test
        fun `MESSAGE frame with body yields correct command, headers, and body`() {
            val raw = "MESSAGE\ndestination:/topic/session\nmessage-id:42\n\nhello world\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.command).isEqualTo(StompCommand.MESSAGE)
            assertThat(frame.headers).containsEntry("destination", "/topic/session")
            assertThat(frame.headers).containsEntry("message-id", "42")
            assertThat(frame.body).isEqualTo("hello world")
        }

        @Test
        fun `SUBSCRIBE frame is parsed and destination header is extracted`() {
            val raw = "SUBSCRIBE\nid:sub-1\ndestination:/queue/reply\n\n\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.command).isEqualTo(StompCommand.SUBSCRIBE)
            assertThat(frame.headers).containsEntry("id", "sub-1")
            assertThat(frame.headers).containsEntry("destination", "/queue/reply")
        }

        @Test
        fun `trailing null byte is stripped and does not appear in body`() {
            val raw = "MESSAGE\ncontent-type:text/plain\n\npayload\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.body).isEqualTo("payload")
            assertThat(frame.body).doesNotContain("\u0000")
        }

        @Test
        fun `unknown command string falls back to StompCommand ERROR`() {
            val raw = "FOOBAR\n\n\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.command).isEqualTo(StompCommand.ERROR)
        }

        @Test
        fun `frame with no body section has null body`() {
            val raw = "CONNECT\naccept-version:1.2\n\n\u0000"

            val frame = StompFrame.parse(raw)

            assertThat(frame.body).isNull()
        }
    }

    @Nested
    inner class RoundTrip {

        @Test
        fun `serialize then parse returns an equivalent frame`() {
            val original = StompFrame(
                command = StompCommand.SEND,
                headers = mapOf("destination" to "/app/move", "content-type" to "application/json"),
                body = """{"cell":5,"letter":"A"}""",
            )

            val roundTripped = StompFrame.parse(original.serialize())

            assertThat(roundTripped.command).isEqualTo(original.command)
            assertThat(roundTripped.headers).isEqualTo(original.headers)
            assertThat(roundTripped.body).isEqualTo(original.body)
        }

        @Test
        fun `parse then serialize reproduces the original wire format`() {
            val wire = "SEND\ndestination:/app/move\ncontent-type:application/json\n\n{\"cell\":5}\u0000"

            val reserialized = StompFrame.parse(wire).serialize()

            assertThat(reserialized).isEqualTo(wire)
        }
    }
}
