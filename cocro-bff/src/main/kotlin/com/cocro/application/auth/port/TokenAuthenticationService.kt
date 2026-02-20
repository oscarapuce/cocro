package com.cocro.application.auth.port

import com.cocro.kernel.auth.model.AuthenticatedUser

interface TokenAuthenticationService {
    /**
     * @return AuthenticatedUser si le token est valide, null sinon
     */
    fun authenticate(token: String): AuthenticatedUser?
}
