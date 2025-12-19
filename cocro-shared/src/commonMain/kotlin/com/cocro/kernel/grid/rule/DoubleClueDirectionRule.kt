package com.cocro.kernel.grid.rule

import com.cocro.kernel.common.rule.CocroRule
import com.cocro.kernel.grid.model.Cell
import com.cocro.kernel.grid.model.enums.ClueDirection

object DoubleClueDirectionRule : CocroRule<Cell.ClueCell.DoubleClueCell> {

    override fun validate(value: Cell.ClueCell.DoubleClueCell): Boolean {
        val dir1 = value.first.direction
        val dir2 = value.second.direction
        return (dir1 == ClueDirection.FROM_SIDE || dir1 == ClueDirection.RIGHT) &&
                (dir2 == ClueDirection.FROM_BELOW || dir2 == ClueDirection.DOWN)
    }
}