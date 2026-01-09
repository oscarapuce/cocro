package com.cocro.kernel.auth.model

interface CocroUser {
    fun userId(): String

    fun roles(): Set<String>

    fun isAdmin(): Boolean
}
