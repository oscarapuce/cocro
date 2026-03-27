package com.cocro.infrastructure.security.spring

import com.cocro.domain.auth.model.AuthenticatedUser
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * Spring Security [Authentication] backed by a [AuthenticatedUser].
 *
 * Used in the WebSocket (STOMP) channel to carry the authenticated user across
 * the inbound channel interceptor → message handler boundary via [SecurityContextHolder].
 *
 * [getPrincipal] returns the [AuthenticatedUser] directly so that
 * [PrincipalCurrentUserProvider] can resolve it without coupling to the presentation layer.
 */
class CocroAuthentication(
    val user: AuthenticatedUser,
) : Authentication {

    private val authorities: List<GrantedAuthority> =
        user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }

    override fun getName(): String = user.userId.toString()
    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
    override fun getCredentials(): Any? = null
    override fun getDetails(): Any? = null
    override fun getPrincipal(): Any = user
    override fun isAuthenticated(): Boolean = true
    override fun setAuthenticated(isAuthenticated: Boolean) = Unit
}
