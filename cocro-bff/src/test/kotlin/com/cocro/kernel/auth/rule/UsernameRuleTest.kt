package com.cocro.kernel.auth.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UsernameRuleTest {

    @Test
    fun `should accept valid alphanumeric username`() {
        // given
        val username = "player1"

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept username with underscore`() {
        // given
        val username = "player_one"

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject username shorter than min length`() {
        // given
        val username = "ab"

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject username longer than max length`() {
        // given
        val username = "a".repeat(UsernameRule.USERNAME_MAX_LENGTH + 1)

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject username with special characters`() {
        // given
        val username = "player!"

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject blank username`() {
        // given
        val username = "   "

        // when
        val result = UsernameRule.validate(username)

        // then
        assertThat(result).isFalse()
    }
}
