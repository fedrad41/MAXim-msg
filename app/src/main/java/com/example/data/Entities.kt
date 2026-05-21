package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val passwordHash: String,
    val registeredAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "terminal_logs")
data class TerminalLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "input", "system", "msg_in", "msg_out"
    val sender: String, // "me", "system", or the sender ID
    val recipient: String = "", // active connect session user id
    val content: String, // Cleartext text (will be displayed as hex dynamic dump in UI if locked)
    val onlineMessageId: String? = null
)

@Entity(tableName = "virtual_files")
data class VirtualFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filename: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)
