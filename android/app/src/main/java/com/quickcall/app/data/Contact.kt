package com.quickcall.app.data

import java.util.UUID

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val status: String = "new",
    val notes: String = "",
    val favorite: Boolean = false,
    val callCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val calledAt: Long? = null
)
