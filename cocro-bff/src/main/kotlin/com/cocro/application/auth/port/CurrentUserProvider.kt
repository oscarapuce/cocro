package com.cocro.application.auth.port

import com.cocro.domain.auth.model.AuthenticatedUser

interface CurrentUserProvider {
    fun currentUserOrNull(): AuthenticatedUser?
}
