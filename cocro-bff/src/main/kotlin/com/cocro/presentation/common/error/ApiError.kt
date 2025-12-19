package com.cocro.presentation.common.error

data class ApiError(
    val code: String,
    val message: String,
    val context: Map<String, String>? = null
)
