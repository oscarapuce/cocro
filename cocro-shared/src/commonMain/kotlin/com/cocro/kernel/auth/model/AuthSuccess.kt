package com.cocro.kernel.auth.model

import com.cocro.kernel.auth.enum.Role

data class AuthSuccess(
    val userId: String,
    val username: String,
    val roles: Set<Role>,
    val token: String,
)
