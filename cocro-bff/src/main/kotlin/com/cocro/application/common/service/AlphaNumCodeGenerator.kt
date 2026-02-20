package com.cocro.application.common.service

import kotlin.random.Random

abstract class AlphaNumCodeGenerator<T>(
    private val size: Int,
    private val alphabet: String = ALPHANUM,
) {
    companion object {
        private const val ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    protected abstract fun wrap(raw: String): T

    protected abstract fun exists(candidate: T): Boolean

    fun generateId(): T {
        var candidate: T

        do {
            candidate = wrap(randomCode())
        } while (exists(candidate))

        return candidate
    }

    private fun randomCode(): String =
        buildString(size) {
            repeat(size) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
}
