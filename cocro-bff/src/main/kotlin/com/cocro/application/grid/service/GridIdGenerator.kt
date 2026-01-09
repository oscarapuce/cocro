package com.cocro.application.grid.service

import com.cocro.application.grid.port.GridRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class GridIdGenerator(
    private val gridRepository: GridRepository,
) {
    companion object {
        private const val ALPHANUM =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    fun generateId(): String {
        var candidate: String

        do {
            candidate = randomId()
        } while (gridRepository.existsByShortId(candidate))

        return candidate
    }

    private fun randomId(length: Int = 6): String {
        val alphabet = ALPHANUM
        return buildString(length) {
            repeat(length) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }
}
