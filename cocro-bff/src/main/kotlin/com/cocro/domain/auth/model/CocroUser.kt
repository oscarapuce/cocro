package com.cocro.domain.auth.model

interface CocroUser {
    fun userId(): String

    fun roles(): Set<String>

    fun isAdmin(): Boolean
}
