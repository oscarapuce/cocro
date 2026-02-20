package com.cocro.application.auth.mapper

import com.cocro.application.auth.dto.AuthSuccess
import com.cocro.kernel.auth.model.User

internal fun User.toAuthSuccess(jwtToken: String): AuthSuccess =
    AuthSuccess(
        userId = this.id.value.toString(),
        username = this.username.value,
        roles = this.roles.map { it.name }.toSet(),
        token = jwtToken,
    )
