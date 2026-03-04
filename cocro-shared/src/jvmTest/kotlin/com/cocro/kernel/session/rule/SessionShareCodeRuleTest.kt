package com.cocro.kernel.session.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionShareCodeRuleTest {

    @Test
    fun `should accept valid 4-character alphanumeric code`() {
        // given
        val code = "AB12"

        // when
        val result = SessionShareCodeRule.validate(code)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject code shorter than 4 characters`() {
        // given
        val code = "AB1"

        // when
        val result = SessionShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code longer than 4 characters`() {
        // given
        val code = "AB123"

        // when
        val result = SessionShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code with lowercase letters`() {
        // given
        val code = "ab12"

        // when
        val result = SessionShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code with special characters`() {
        // given
        val code = "A-12"

        // when
        val result = SessionShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }
}
