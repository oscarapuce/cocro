package com.cocro.infrastructure.util

import org.springframework.stereotype.Component

data class Spice(val name: String, val feminine: Boolean)
data class Color(val feminine: String, val masculine: String)

@Component
open class SpiceNameGenerator {

    private val spices: List<Spice> = loadSpices()
    private val colors: List<Color> = loadColors()

    open fun generate(): String {
        val spice = spices.random()
        val color = colors.random()
        val adjective = if (spice.feminine) color.feminine else color.masculine
        return "${spice.name}-${adjective.replaceFirstChar { it.uppercaseChar() }}"
    }

    private fun loadSpices(): List<Spice> =
        javaClass.classLoader.getResourceAsStream("spices.txt")!!
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val (name, gender) = line.trim().split(" ")
                Spice(name, gender == "F")
            }

    private fun loadColors(): List<Color> =
        javaClass.classLoader.getResourceAsStream("colors.txt")!!
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val (feminine, masculine) = line.trim().split(" ")
                Color(feminine, masculine)
            }
}
