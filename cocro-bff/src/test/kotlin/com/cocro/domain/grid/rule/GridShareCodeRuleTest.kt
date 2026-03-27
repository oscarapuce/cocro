package com.cocro.domain.grid.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GridShareCodeRuleTest {

    @Test
    fun `should accept valid 6-character alphanumeric code`() {
        // given
        val code = "ABC123"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept all-uppercase code`() {
        // given
        val code = "ABCDEF"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject code shorter than 6 characters`() {
        // given
        val code = "ABC12"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code longer than 6 characters`() {
        // given
        val code = "ABC1234"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code with lowercase letters`() {
        // given
        val code = "abc123"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject code with special characters`() {
        // given
        val code = "ABC-23"

        // when
        val result = GridShareCodeRule.validate(code)

        // then
        assertThat(result).isFalse()
    }
}
