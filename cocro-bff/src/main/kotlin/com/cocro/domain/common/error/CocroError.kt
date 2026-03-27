package com.cocro.domain.common.error

interface CocroError {
    val errorCode: ErrorCode

    fun context(): Map<String, String>? = null
}
