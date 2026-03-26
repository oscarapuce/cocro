package com.cocro.kernel.auth.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordRuleTest {

    @Test
    fun `should accept valid strong password`() {
        // given
        val password = "Secure1!"

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject password without uppercase`() {
        // given
        val password = "secure1!"

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject password without digit`() {
        // given
        val password = "SecurePass!"

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject password without special character`() {
        // given
        val password = "Secure123"

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject password shorter than min length`() {
        // given
        val password = "S1!"

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject password longer than max length`() {
        // given
        val password = "Secure1!" + "a".repeat(PasswordRule.MAX_LENGTH)

        // when
        val result = PasswordRule.validate(password)

        // then
        assertThat(result).isFalse()
    }
}
