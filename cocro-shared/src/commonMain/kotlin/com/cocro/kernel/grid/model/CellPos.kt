package com.cocro.kernel.grid.model

data class CellPos(
    val x: Int,
    val y: Int,
) {
    init {
        require(x >= 0) { "x must be >= 0" }
        require(y >= 0) { "y must be >= 0" }
    }
}
