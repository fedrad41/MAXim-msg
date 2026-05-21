package com.example.data

import kotlinx.coroutines.flow.Flow

class TerminalRepository(
    private val userDao: UserDao,
    private val terminalLogDao: TerminalLogDao,
    private val virtualFileDao: VirtualFileDao
) {
    val allLogs: Flow<List<TerminalLogEntity>> = terminalLogDao.getAllLogsFlow()
    val allFiles: Flow<List<VirtualFileEntity>> = virtualFileDao.getAllFilesFlow()

    suspend fun getPrimaryUser(): UserEntity? = userDao.getPrimaryUser()
    suspend fun getUserByUsername(username: String): UserEntity? = userDao.getUserByUsername(username)
    suspend fun getUserCount(): Int = userDao.getUserCount()
    suspend fun insertUser(user: UserEntity) = userDao.insertUser(user)

    suspend fun insertLog(log: TerminalLogEntity) = terminalLogDao.insertLog(log)
    suspend fun clearLogs() = terminalLogDao.clearLogs()
    suspend fun getAllLogs(): List<TerminalLogEntity> = terminalLogDao.getAllLogs()
    suspend fun getLogByOnlineId(onlineId: String): TerminalLogEntity? = terminalLogDao.getLogByOnlineId(onlineId)

    suspend fun getAllFiles(): List<VirtualFileEntity> = virtualFileDao.getAllFiles()
    suspend fun getFileByName(filename: String): VirtualFileEntity? = virtualFileDao.getFileByName(filename)
    suspend fun insertFile(file: VirtualFileEntity) = virtualFileDao.insertFile(file)
    suspend fun deleteFileByName(filename: String) = virtualFileDao.deleteFileByName(filename)
}
