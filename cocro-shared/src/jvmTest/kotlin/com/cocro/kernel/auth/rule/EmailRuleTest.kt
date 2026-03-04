package com.cocro.kernel.auth.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EmailRuleTest {

    @Test
    fun `should accept valid email`() {
        // given
        val email = "user@example.com"

        // when
        val result = EmailRule.validate(email)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept email with plus addressing`() {
        // given
        val email = "user+tag@example.com"

        // when
        val result = EmailRule.validate(email)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject email without at sign`() {
        // given
        val email = "userexample.com"

        // when
        val result = EmailRule.validate(email)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject email without domain`() {
        // given
        val email = "user@"

        // when
        val result = EmailRule.validate(email)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject blank email`() {
        // given
        val email = ""

        // when
        val result = EmailRule.validate(email)

        // then
        assertThat(result).isFalse()
    }
}
