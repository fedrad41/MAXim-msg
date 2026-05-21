package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getPrimaryUser(): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
}

@Dao
interface TerminalLogDao {
    @Query("SELECT * FROM terminal_logs ORDER BY timestamp ASC")
    fun getAllLogsFlow(): Flow<List<TerminalLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TerminalLogEntity)

    @Query("DELETE FROM terminal_logs")
    suspend fun clearLogs()

    @Query("SELECT * FROM terminal_logs ORDER BY timestamp ASC")
    suspend fun getAllLogs(): List<TerminalLogEntity>

    @Query("SELECT * FROM terminal_logs WHERE onlineMessageId = :onlineId LIMIT 1")
    suspend fun getLogByOnlineId(onlineId: String): TerminalLogEntity?
}

@Dao
interface VirtualFileDao {
    @Query("SELECT * FROM virtual_files ORDER BY filename ASC")
    fun getAllFilesFlow(): Flow<List<VirtualFileEntity>>

    @Query("SELECT * FROM virtual_files ORDER BY filename ASC")
    suspend fun getAllFiles(): List<VirtualFileEntity>

    @Query("SELECT * FROM virtual_files WHERE filename = :filename LIMIT 1")
    suspend fun getFileByName(filename: String): VirtualFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VirtualFileEntity)

    @Query("DELETE FROM virtual_files WHERE filename = :filename")
    suspend fun deleteFileByName(filename: String)
}
