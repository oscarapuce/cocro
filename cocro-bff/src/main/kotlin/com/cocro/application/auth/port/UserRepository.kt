package com.cocro.application.auth.port

import com.cocro.kernel.auth.model.User
import com.cocro.kernel.auth.model.valueobject.Username

interface UserRepository {
    fun findByUsername(username: Username): User?

    fun save(user: User): User
}
