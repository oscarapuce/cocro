package com.cocro.domain.grid.rule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LetterRuleTest {

    @Test
    fun `should accept uppercase letter`() {
        // given
        val letter = 'A'

        // when
        val result = LetterRule.validate(letter)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should accept last uppercase letter`() {
        // given
        val letter = 'Z'

        // when
        val result = LetterRule.validate(letter)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `should reject lowercase letter`() {
        // given
        val letter = 'a'

        // when
        val result = LetterRule.validate(letter)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject digit`() {
        // given
        val letter = '1'

        // when
        val result = LetterRule.validate(letter)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `should reject special character`() {
        // given
        val letter = '!'

        // when
        val result = LetterRule.validate(letter)

        // then
        assertThat(result).isFalse()
    }
}
