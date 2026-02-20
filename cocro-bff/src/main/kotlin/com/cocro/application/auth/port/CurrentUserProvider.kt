package com.cocro.application.auth.port

import com.cocro.kernel.auth.model.AuthenticatedUser

interface CurrentUserProvider {
    fun currentUserOrNull(): AuthenticatedUser?
}
