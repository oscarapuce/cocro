package com.cocro.kernel.auth.model

import com.cocro.kernel.auth.enum.Role

data class AuthenticatedUser(
    val userId: String,
    val roles: Set<Role>,
)
