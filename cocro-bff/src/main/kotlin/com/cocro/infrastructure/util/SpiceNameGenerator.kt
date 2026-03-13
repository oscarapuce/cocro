package com.cocro.infrastructure.util

import org.springframework.stereotype.Component

@Component
open class SpiceNameGenerator {
    private val spices = listOf(
        "Cardamome", "Curcuma", "Cannelle", "Safran", "Vanille",
        "Gingembre", "Muscade", "Poivre", "Coriandre", "Cumin",
        "Paprika", "Fenugrec", "Anis", "Laurier", "Thym",
        "Romarin", "Fenouil", "Sumac", "Zaatar", "Galanga",
    )

    open fun generate(): String {
        val spice = spices.random()
        val number = (1000..9999).random()
        return "$spice-$number"
    }
}
