package com.cocro.domain.grid.model.valueobject

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GridValueObjectsTest {

    @Nested
    inner class GridTitleTest {

        @Test
        fun `valid title of 5 chars is accepted`() {
            val title = GridTitle("Hello")
            assertThat(title.value).isEqualTo("Hello")
        }

        @Test
        fun `valid title of 60 chars is accepted`() {
            val title = GridTitle("A".repeat(60))
            assertThat(title.value).hasSize(60)
        }

        @Test
        fun `blank title is rejected`() {
            assertThatThrownBy { GridTitle("   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `empty title is rejected`() {
            assertThatThrownBy { GridTitle("") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `title shorter than 5 chars is rejected`() {
            assertThatThrownBy { GridTitle("Abcd") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `title longer than 60 chars is rejected`() {
            assertThatThrownBy { GridTitle("A".repeat(61)) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class GridWidthTest {

        @Test
        fun `width of 5 is accepted`() {
            assertThat(GridWidth(5).value).isEqualTo(5)
        }

        @Test
        fun `width of 70 is accepted`() {
            assertThat(GridWidth(70).value).isEqualTo(70)
        }

        @Test
        fun `width below 5 is rejected`() {
            assertThatThrownBy { GridWidth(4) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `width above 70 is rejected`() {
            assertThatThrownBy { GridWidth(71) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class GridHeightTest {

        @Test
        fun `height of 5 is accepted`() {
            assertThat(GridHeight(5).value).isEqualTo(5)
        }

        @Test
        fun `height of 50 is accepted`() {
            assertThat(GridHeight(50).value).isEqualTo(50)
        }

        @Test
        fun `height below 5 is rejected`() {
            assertThatThrownBy { GridHeight(4) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `height above 50 is rejected`() {
            assertThatThrownBy { GridHeight(51) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class GridShareCodeTest {

        @Test
        fun `valid 6-char alphanumeric code is accepted`() {
            assertThat(GridShareCode("ABC123").value).isEqualTo("ABC123")
        }

        @Test
        fun `code with lowercase is rejected`() {
            assertThatThrownBy { GridShareCode("abc123") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `code shorter than 6 chars is rejected`() {
            assertThatThrownBy { GridShareCode("ABC12") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `code longer than 6 chars is rejected`() {
            assertThatThrownBy { GridShareCode("ABC1234") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `code with special characters is rejected`() {
            assertThatThrownBy { GridShareCode("ABC12!") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class LetterValueTest {

        @Test
        fun `uppercase letter is accepted`() {
            assertThat(LetterValue('A').value).isEqualTo('A')
        }

        @Test
        fun `lowercase letter is rejected`() {
            assertThatThrownBy { LetterValue('a') }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `digit is rejected`() {
            assertThatThrownBy { LetterValue('1') }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
