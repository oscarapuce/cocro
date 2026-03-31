package com.cocro.domain.common.model

import com.cocro.domain.auth.model.valueobject.UserId

data class Author(
    val id: UserId,
    val username: String,
)

