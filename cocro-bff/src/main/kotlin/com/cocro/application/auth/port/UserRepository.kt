package com.cocro.application.auth.port

import com.cocro.domain.auth.model.User
import com.cocro.domain.auth.model.valueobject.Username

interface UserRepository {
    fun findByUsername(username: Username): User?

    fun save(user: User): User
}
