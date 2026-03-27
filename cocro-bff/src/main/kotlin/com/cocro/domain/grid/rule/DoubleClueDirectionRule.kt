package com.cocro.domain.grid.rule

import com.cocro.domain.common.rule.CocroRule
import com.cocro.domain.grid.enums.ClueDirection

object DoubleClueDirectionRule : CocroRule<ClueDirection>() {
    override val arity: Int = 2

    override fun isValid(values: List<ClueDirection>): Boolean {
        if (values.size != 2) return false

        val dir1 = values[0]
        val dir2 = values[1]

        return (dir1 == ClueDirection.FROM_SIDE || dir1 == ClueDirection.RIGHT) &&
            (dir2 == ClueDirection.FROM_BELOW || dir2 == ClueDirection.DOWN)
    }
}
