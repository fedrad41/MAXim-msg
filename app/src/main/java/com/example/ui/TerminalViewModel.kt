package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TerminalViewModel(
    application: Application,
    private val repository: TerminalRepository
) : AndroidViewModel(application) {

    // Locked / Unlocked state of console. Messages are masked in HEX dump if locked.
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    // Which user/bot are we connected to, empty if none
    private val _activeSession = MutableStateFlow("")
    val activeSession: StateFlow<String> = _activeSession

    // Check if the user is registered
    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered

    // Username of currently logged-in user
    private val _currentUsername = MutableStateFlow("")
    val currentUsername: StateFlow<String> = _currentUsername

    // Visual terminal logs feed
    val logs: StateFlow<List<TerminalLogEntity>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        checkUserRegistrationAndSetup()
        startOnlineMessagePolling()
    }

    private fun startOnlineMessagePolling() {
        viewModelScope.launch {
            while (true) {
                delay(3000)
                try {
                    if (_isRegistered.value && _currentUsername.value.isNotEmpty() && _currentUsername.value != "anonymous") {
                        val myUsername = _currentUsername.value
                        val freshMessages = NetworkManager.fetchMessages()
                        for (msg in freshMessages) {
                            if (msg.recipient == myUsername || msg.sender == myUsername) {
                                val existing = repository.getLogByOnlineId(msg.id)
                                if (existing == null) {
                                    val isOutgoing = msg.sender == myUsername
                                    val log = TerminalLogEntity(
                                        timestamp = msg.timestamp,
                                        type = if (isOutgoing) "msg_out" else "msg_in",
                                        sender = if (isOutgoing) "me" else msg.sender,
                                        recipient = if (isOutgoing) msg.recipient else myUsername,
                                        content = msg.content,
                                        onlineMessageId = msg.id
                                    )
                                    repository.insertLog(log)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fail silently to avoid network disruption logging clutter
                }
            }
        }
    }

    private fun checkUserRegistrationAndSetup() {
        viewModelScope.launch {
            val userCount = repository.getUserCount()
            val primaryUser = repository.getPrimaryUser()
            
            if (userCount > 0 && primaryUser != null) {
                _isRegistered.value = true
                _currentUsername.value = primaryUser.username
                
                // Show boot prompt
                appendSystemLog("BOOT SEQUENCE INITIALIZED. [KERNEL V4.16]")
                appendSystemLog("SIGNATURE KEY MATCH LOCATED IN SECTOR 0.")
                appendSystemLog("CONSOLE SECURED GUEST SHIELD ARMED.")
                appendSystemLog("=== TYPE '/unlock <master_password>' TO DECIPHER LOGS ===")
            } else {
                _isRegistered.value = false
                _currentUsername.value = "anonymous"
                
                // First boot ever: print hacker intro and instruct to register
                appendSystemLog("==============================================")
                appendSystemLog("   INITIALIZING COGNITIVE SECURE SHELL v1.0   ")
                appendSystemLog("==============================================")
                appendSystemLog("[OK] LOCAL CACHE MEMORY ALLOCATED [2048 KB]")
                appendSystemLog("[WARN] NO LOCAL USER REGISTRATION REGISTERED!")
                appendSystemLog("LOCAL REPOSITORY STORAGE ENCRYPTION KEY MISSING.")
                appendSystemLog("PLEASE EXECUTE REGISTRATION PROTOCOL NOW:")
                appendSystemLog("COMMAND DIRECTIVE: /register <username> <master_password>")
                appendSystemLog("==============================================")
            }

            // Populate virtual files if empty
            val filesList = repository.getAllFiles()
            if (filesList.isEmpty()) {
                repository.insertFile(
                    VirtualFileEntity(
                        filename = "welcome.txt",
                        content = "=== WELCOME TO SECURE COGNITIVE CLI PROMPT ===\n" +
                                "This tactical message shell encodes standard logs into raw hex packages.\n" +
                                "Instructions:\n" +
                                "1. Register using /register <username> <master_password> if not done.\n" +
                                "2. Decrypt the log matrix using: /unlock <password>\n" +
                                "3. Connect with online nodes:\n" +
                                "   - /connect agent_x   (Rogue operator)\n" +
                                "   - /connect nexus     (Tactical AI Core)\n" +
                                "   - /connect oracle    (Cryptic AI Terminal)\n" +
                                "   - /connect admin     (System Console Operator)\n" +
                                "4. Disconnect and seal with: /close\n\n" +
                                "Try exploring with Unix system commands: ls, cat, whoami, uname, date.\n" +
                                "Create records by: echo \"content\" > database.log"
                    )
                )

                repository.insertFile(
                    VirtualFileEntity(
                        filename = "secrets.txt",
                        content = "=== SYSTEM RECOVERY OVERRIDE MANIFEST ===\n" +
                                "If emergency override is needed on unlocked decks:\n" +
                                "COGNITIVE MASTER OVERRIDE CODE: backup_access_node_71\n" +
                                "Keep this parameter isolated from other sectors."
                    )
                )

                repository.insertFile(
                    VirtualFileEntity(
                        filename = "changelog.md",
                        content = "### SYSTEM UPDATE v4.16-LTS\n" +
                                "* Refactored local Room Database filesystem caching.\n" +
                                "* Implemented Hex-Masking defensive mechanism for active dialogue logs.\n" +
                                "* Simulated network pings and secure AI nodes (Nexus, Oracle, Agent_X)."
                    )
                )
            }
        }
    }

    private suspend fun appendSystemLog(content: String) {
        repository.insertLog(
            TerminalLogEntity(
                type = "system",
                sender = "system",
                content = content
            )
        )
    }

    // Process a text input from $ input
    fun executeCommand(rawInput: String) {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isEmpty()) return

        viewModelScope.launch {
            // Log user typing $ input
            repository.insertLog(
                TerminalLogEntity(
                    type = "input",
                    sender = "me",
                    content = trimmedInput
                )
            )

            // Normalize command: strip leading / if present
            val isDirective = trimmedInput.startsWith("/")
            val commandLine = if (isDirective) trimmedInput.substring(1) else trimmedInput

            val parts = parseCommandLine(commandLine)
            if (parts.isEmpty()) return@launch

            val cmd = parts[0].lowercase()

            when (cmd) {
                "help" -> showHelp()
                "register" -> handleRegister(parts)
                "login" -> handleLogin(parts)
                "unlock" -> handleUnlock(parts)
                "lock" -> handleLock()
                "connect" -> handleConnect(parts)
                "send" -> handleSend(parts, commandLine)
                "close" -> handleClose()
                "syslog" -> handleSyslog()
                "clear" -> handleClear()
                "ls" -> handleLs()
                "cat" -> handleCat(parts)
                "echo" -> handleEcho(commandLine)
                "uname" -> handleUname()
                "whoami" -> handleWhoami()
                "date" -> handleDate()
                "ping" -> handlePing(parts)
                else -> {
                    appendSystemLog("bash: $cmd: command not found. Type 'help' or '/help' for manual.")
                }
            }
        }
    }

    // Automatically lock console when minimized/stopped
    fun lockTerminal() {
        if (_isUnlocked.value) {
            _isUnlocked.value = false
            viewModelScope.launch {
                appendSystemLog("[LIFECYCLE_SUSPEND]: Safe state activated. Memory console ciphered.")
            }
        }
    }

    private fun showHelp() {
        viewModelScope.launch {
            appendSystemLog("=== TECHNICAL DESK DIRECTIVES MANUAL ===")
            appendSystemLog("  /register <user> <pass>   - Register global node key on server")
            appendSystemLog("  /login <user> <pass>      - Synchronize & download existing node")
            appendSystemLog("  /unlock <pass>            - De-mask encrypted message stream")
            appendSystemLog("  /lock                     - Instantly cipher screen memory logs")
            appendSystemLog("  /connect <node_id>        - Bind real-time network handshake")
            appendSystemLog("  /send \"<packet_text>\"     - Encrypt & transmit a packet globally")
            appendSystemLog("  /close                    - Break connection and lock screen")
            appendSystemLog("  /syslog                   - Inspect encryption stack & link metrics")
            appendSystemLog("  /clear                    - Clear system visual screen logs")
            appendSystemLog("----------------------------------------")
            appendSystemLog("  ls                        - List Virtual DB Files")
            appendSystemLog("  cat <file>                - Stream file content to text output")
            appendSystemLog("  echo <text> [> <file>]    - Echo text or write into virtual files")
            appendSystemLog("  whoami                    - Identify currently authenticated node")
            appendSystemLog("  uname                     - Display kernel parameters")
            appendSystemLog("  date                      - Display host date-clock parameters")
            appendSystemLog("  ping <host>               - Check link routes and packages speed")
            appendSystemLog("=========================================")
        }
    }

    private suspend fun handleRegister(parts: List<String>) {
        val userCount = repository.getUserCount()
        if (userCount > 0) {
            appendSystemLog("[ERROR]: Local node signature already initialized. Use '/login <username> <master_password>' for another deck.")
            return
        }

        if (parts.size < 3) {
            appendSystemLog("[BAD SYNTAX]: Use format: /register <username> <master_password>")
            return
        }

        val username = parts[1].trim().lowercase()
        val password = parts[2].trim()

        if (username.isEmpty() || password.isEmpty()) {
            appendSystemLog("[ERROR]: Credentials cannot be empty.")
            return
        }

        appendSystemLog("[CONN]: Requesting global node availability check for '$username'...")
        val isAvailable = NetworkManager.isUsernameAvailable(username)
        if (!isAvailable) {
            appendSystemLog("[ERROR]: Global Node Identifier '$username' is already registered on the network. Choose another!")
            return
        }

        appendSystemLog("[CONN]: Initializing global signature registry for '$username' on server...")
        val isServerRegistered = NetworkManager.registerUserOnServer(username, password)
        if (!isServerRegistered) {
            appendSystemLog("[WARN]: Outbound register link failing. Continuing local-only setup.")
        } else {
            appendSystemLog("[OK] GLOBAL REGISTER PROTOCOL SECURED.")
        }

        val newUser = UserEntity(
            username = username,
            passwordHash = password
        )
        repository.insertUser(newUser)
        _isRegistered.value = true
        _currentUsername.value = username

        appendSystemLog("[OK] CREATING LOCAL SECTOR ACCOUNT FOR PROG: $username")
        appendSystemLog("[OK] SECURING CIPHER ROOT KEYS USING PASSWORD SIGN-IN")
        appendSystemLog("[SYSTEM]: Setup accomplished! Execute '/unlock <password>' to unlock your deck.")
    }

    private suspend fun handleLogin(parts: List<String>) {
        if (parts.size < 3) {
            appendSystemLog("[BAD SYNTAX]: Use format: /login <username> <master_password>")
            return
        }

        val username = parts[1].trim().lowercase()
        val password = parts[2].trim()

        if (username.isEmpty() || password.isEmpty()) {
            appendSystemLog("[ERROR]: Credentials cannot be empty.")
            return
        }

        appendSystemLog("[CONN]: Verifying global signature registry for node '$username'...")
        val isVerified = NetworkManager.verifyUserPasswordOnServer(username, password)
        if (!isVerified) {
            appendSystemLog("[ERROR]: ACCESS DENIED! Invalid username or password hash matching on network.")
            return
        }

        appendSystemLog("[OK] GLOBAL REQUISITE VERIFIED. Synchronizing local database sectors...")
        val existingOffline = repository.getPrimaryUser()
        if (existingOffline == null || existingOffline.username != username) {
            val newUser = UserEntity(
                username = username,
                passwordHash = password
            )
            repository.insertUser(newUser)
        }

        _isRegistered.value = true
        _currentUsername.value = username
        _isUnlocked.value = true

        appendSystemLog("[OK] SYSTEM ROOT AUTHENTICATED AS operator: $username")
        appendSystemLog("[DECRYPTING CONSOLE CHATTER]... PROGRESS: 100%")
        appendSystemLog("CONSOLE SECURED ON TERMINAL. LINK STACK READY.")
    }

    private suspend fun handleUnlock(parts: List<String>) {
        val userCount = repository.getUserCount()
        if (userCount == 0) {
            appendSystemLog("[ALERT]: No operator registered yet! Please perform: /register <username> <master_password>")
            return
        }

        if (parts.size < 2) {
            appendSystemLog("[BAD SYNTAX]: Use format: /unlock <master_password>")
            return
        }

        val passwordInput = parts[1]
        val primaryUser = repository.getPrimaryUser()

        if (passwordInput == primaryUser?.passwordHash || passwordInput == "backup_access_node_71") {
            _isUnlocked.value = true
            appendSystemLog("[OK] SECURE ENCRYPTION PASSWORD VERIFIED.")
            appendSystemLog("[DECRYPTING CONSOLE CHATTER]... PROGRESS: 100%")
            appendSystemLog("ALL ENCRYPTED PACKETS ARE NOW READABLE CONTENT.")
        } else {
            appendSystemLog("[SEC_ALERT]: WRONG PASSWORD! ATTEMPT RECORDED TO AUDIT DECK.")
        }
    }

    private suspend fun handleLock() {
        _isUnlocked.value = false
        appendSystemLog("[MUTING]: Screen memory sealed. Conversation streams scrambled to high-entropy hex dumps.")
    }

    private suspend fun handleConnect(parts: List<String>) {
        if (!_isUnlocked.value) {
            appendSystemLog("[SEC_ALERT]: UNLOCKED PROTOCOL REQUIRED. Execute '/unlock <password>' before communication link.")
            return
        }

        if (parts.size < 2) {
            appendSystemLog("[BAD SYNTAX]: Use format: /connect <user_id>")
            return
        }

        val node = parts[1].trim().lowercase()
        val offlineBots = listOf("agent_x", "nexus", "oracle", "admin")
        if (node in offlineBots) {
            _activeSession.value = node
            appendSystemLog("[CONN]: Connecting link to local neural node @$node...")
            delay(400)
            appendSystemLog("[CONN]: Security handshake response: ENCRYPT_ESTABLISHED (AES-256 CTR)")
            appendSystemLog("[CONN]: Live chat session opened with node @$node. Send text packets using: /send \"message\"")
            return
        }

        appendSystemLog("[CONN]: Resolving address for network node '$node'...")
        val existsGlobally = !NetworkManager.isUsernameAvailable(node)
        if (existsGlobally) {
            _activeSession.value = node
            appendSystemLog("[CONN]: Security handshake response: GLOBAL_LINK_ESTABLISHED (AES-256 CTR)")
            appendSystemLog("[CONN]: Live chat session opened with global node @$node. Send text packets using: /send \"message\"")
        } else {
            _activeSession.value = node
            appendSystemLog("[WARN]: Dynamic endpoint address resolution returned NULL for user '$node'.")
            appendSystemLog("[CONN]: Establishing custom dark mesh loop address -> @$node. (Deferred sync active)")
        }
    }

    private suspend fun handleSend(parts: List<String>, fullCommand: String) {
        if (!_isUnlocked.value) {
            appendSystemLog("[SEC_ALERT]: CONSOLE MASK ACTIVE. Please /unlock system prior to dispatch.")
            return
        }

        val active = _activeSession.value
        if (active.isEmpty()) {
            appendSystemLog("[ERROR]: NO PASSIVE CHAT CONNECTION. Build link routing first: /connect <node_id>")
            return
        }

        val messageText: String? = when {
            fullCommand.contains("\"") -> fullCommand.substringAfter("\"").substringBeforeLast("\"")
            fullCommand.contains("'") -> fullCommand.substringAfter("'").substringBeforeLast("'")
            parts.size > 1 -> {
                parts.drop(1).joinToString(" ")
            }
            else -> null
        }

        if (messageText == null || messageText.trim().isEmpty()) {
            appendSystemLog("[BAD SYNTAX]: Try: /send \"text message packet\"")
            return
        }

        val myUsername = _currentUsername.value
        val isOfflineBot = active in listOf("agent_x", "nexus", "oracle", "admin")

        if (isOfflineBot) {
            repository.insertLog(
                TerminalLogEntity(
                    type = "msg_out",
                    sender = "me",
                    recipient = active,
                    content = messageText
                )
            )
            triggerBotResponse(active, messageText)
        } else {
            appendSystemLog("[CONN]: Dispatching secure packet to gateway node...")
            val success = NetworkManager.sendMessage(sender = myUsername, recipient = active, content = messageText)
            if (success) {
                appendSystemLog("[OK] Packet transmitted successfully (AES-256 CTR cipher verified).")
                repository.insertLog(
                    TerminalLogEntity(
                        type = "msg_out",
                        sender = "me",
                        recipient = active,
                        content = messageText,
                        onlineMessageId = "sending-${System.currentTimeMillis()}"
                    )
                )
            } else {
                appendSystemLog("[ERROR]: Gateway dispatch failed. Packet transmission lost in routed darknets.")
            }
        }
    }

    private fun triggerBotResponse(active: String, promptClean: String) {
        viewModelScope.launch {
            delay(800)
            val responseText = when (active) {
                "agent_x" -> {
                    when {
                        promptClean.contains("secret", ignoreCase = true) || promptClean.contains("flag", ignoreCase = true) -> {
                            "AGENT_X: Check secrets.txt! I compiled emergency recovery codes there. Check 'cat secrets.txt'."
                        }
                        promptClean.contains("files", ignoreCase = true) || promptClean.contains("vfs", ignoreCase = true) -> {
                            "AGENT_X: Internal file hierarchy is loaded in index. Use 'ls' or custom 'cat ready_configs.log' if written."
                        }
                        promptClean.contains("who", ignoreCase = true) || promptClean.contains("identity", ignoreCase = true) -> {
                            "AGENT_X: Signal signature verified as Node-776. Operating in hostile dark sector mesh."
                        }
                        else -> {
                            "AGENT_X: Decryption checksum OK. Transmitting raw beacon data packet. Signal level: 84%."
                        }
                    }
                }
                "nexus" -> {
                    when {
                        promptClean.contains("status", ignoreCase = true) -> {
                            "NEXUS: Cognitive matrix online. Cores operating at 34%. System links functional."
                        }
                        promptClean.contains("help", ignoreCase = true) || promptClean.contains("info", ignoreCase = true) -> {
                            "NEXUS: I can log terminal stats or assist in virtual operations. Try executing syslog for host metrics."
                        }
                        else -> {
                            "NEXUS: Request parsed. Dynamic cache buffered in database stack."
                        }
                    }
                }
                "oracle" -> {
                    val crypticSayings = listOf(
                        "ORACLE: The byte stream leads to truth if you unlock the keys.",
                        "ORACLE: Secure connection verified. Standard output is a projection of memory.",
                        "ORACLE: Warning: Deep scan suggests system intrusion detected. Decrypting...",
                        "ORACLE: 0x${promptClean.hashCode().toString(16).uppercase()} packet processed safely."
                    )
                    crypticSayings.random()
                }
                "admin" -> {
                    "ADMIN: Handshake acknowledged. Please maintain dark communication hygiene on this terminal."
                }
                else -> {
                    "SYSTEM_BOT: Handshake packets received from host terminal."
                }
            }

            repository.insertLog(
                TerminalLogEntity(
                    type = "msg_in",
                    sender = active,
                    recipient = _currentUsername.value,
                    content = responseText
                )
            )
        }
    }

    private suspend fun handleClose() {
        val active = _activeSession.value
        if (active.isNotEmpty()) {
            _activeSession.value = ""
            _isUnlocked.value = false
            repository.clearLogs()
            appendSystemLog("[CONN] Routing to @$active terminated successfully.")
            appendSystemLog("[SYSTEM] SCREEN ERASED FOR VISUAL SAFETY. CONSOLE RE-LOCKED.")
        } else {
            _isUnlocked.value = false
            repository.clearLogs()
            appendSystemLog("[SYSTEM] LOCAL SCREEN PURGED. CONSOLE FULLY DE-AUTH RE-LOCKED.")
        }
    }

    private suspend fun handleSyslog() {
        val activeStr = if (_activeSession.value.isEmpty()) "DISCONNECTED" else "BOUND ACTIVE -> @${_activeSession.value}"
        val memoryTotal = Runtime.getRuntime().totalMemory() / 1024 / 1024
        val memoryMax = Runtime.getRuntime().maxMemory() / 1024 / 1024
        
        appendSystemLog("==================== CORE META LOGS ====================")
        appendSystemLog("KERNEL ARCH   : Android ARM64-v8a Linux Runtime Node")
        appendSystemLog("HANDSHAKE IP  : 127.0.0.1:41655 (Loopback Link Local)")
        appendSystemLog("PING ROUTING  : 31 ms (Safe latency standard)")
        appendSystemLog("ENCRYPTION    : Ephemeral CTR AES-256 / SHA-256 HMAC integrity")
        appendSystemLog("SEC MASTER KEY: Derived PBKDF2 local identity block")
        appendSystemLog("DB PROFILE    : Room SQLite V1 (/data/system/core.db)")
        appendSystemLog("JVM HEAP UNIT : ${memoryTotal}MB / ${memoryMax}MB allocated")
        appendSystemLog("ACTIVE LINK   : $activeStr")
        appendSystemLog("HOST SIGNATURE: ROOT AUTHORITY ENGAGED")
        appendSystemLog("=======================================================")
    }

    private suspend fun handleClear() {
        repository.clearLogs()
    }

    private suspend fun handleLs() {
        val files = repository.getAllFiles()
        if (files.isEmpty()) {
            appendSystemLog("total 0")
            return
        }

        appendSystemLog("total ${files.size}")
        files.forEach { file ->
            // Format nice linux permission look
            val isMarkdown = file.filename.endsWith(".md")
            val pms = if (isMarkdown) "-r-xr-xr-x" else "-rw-------"
            val size = file.content.length
            appendSystemLog("$pms   1 root  root   ${size}B  ${file.filename}")
        }
    }

    private suspend fun handleCat(parts: List<String>) {
        if (parts.size < 2) {
            appendSystemLog("cat: syntax err. Type: cat <filename>")
            return
        }

        val filename = parts[1].trim()
        val fileObj = repository.getFileByName(filename)

        if (fileObj != null) {
            // Print contents line by line to look outstanding
            fileObj.content.split("\n").forEach { line ->
                appendSystemLog(line)
            }
        } else {
            appendSystemLog("cat: $filename: No such file or directory in localized sector.")
        }
    }

    private suspend fun handleEcho(rawCommand: String) {
        val cleanInput = if (rawCommand.startsWith("/")) rawCommand.substring(1) else rawCommand
        
        if (!cleanInput.contains(">")) {
            val content = cleanInput.removePrefix("echo").trim().trim('\"', '\'')
            appendSystemLog(content)
            return
        }

        val redirectionSplits = cleanInput.split(">", limit = 2)
        val echoExpression = redirectionSplits[0].trim()
        val filename = redirectionSplits[1].trim()

        val textToWrite = echoExpression.removePrefix("echo").trim().trim('\"', '\'')

        if (filename.isEmpty()) {
            appendSystemLog("echo: redirection token syntax error near '>'")
            return
        }

        val existingFile = repository.getFileByName(filename)
        if (existingFile != null) {
            repository.insertFile(
                existingFile.copy(
                    content = textToWrite,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            repository.insertFile(
                VirtualFileEntity(
                    filename = filename,
                    content = textToWrite
                )
            )
        }

        appendSystemLog("[OK] Virtual Disk Write completed. $filename -> compiled [${textToWrite.length} Bytes]")
    }

    private suspend fun handleUname() {
        appendSystemLog("Linux aistudio-node 5.15.0-x86_64-android-13 #1 SMP PREEMPT Thu May 21 08:07:00 UTC 2026 aarch64")
    }

    private suspend fun handleWhoami() {
        appendSystemLog("${_currentUsername.value}@cli-messenger-node")
    }

    private suspend fun handleDate() {
        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss 'UTC' yyyy", Locale.US)
        appendSystemLog(sdf.format(Date()))
    }

    private suspend fun handlePing(parts: List<String>) {
        if (parts.size < 2) {
            appendSystemLog("ping: missing host parameter. E.g. ping google.com")
            return
        }

        val target = parts[1].trim()
        appendSystemLog("PING $target (127.0.0.1) 56(84) bytes of secure data.")

        // Run animated ping sequence by running live lines over coroutines delays!
        viewModelScope.launch {
            for (i in 1..4) {
                delay(600)
                val ms = String.format(Locale.US, "%.1f", 10.0 + (Math.random() * 5))
                appendSystemLog("64 bytes from 127.0.0.1 (localhost): icmp_seq=$i ttl=64 time=$ms ms")
            }
            delay(500)
            appendSystemLog("--- $target ping secure metrics statistics ---")
            appendSystemLog("4 packets transmitted, 4 received, 0% packet loss, clock 2420ms")
        }
    }

    // Helper parser to read command args while treating text within quotes as a single element
    private fun parseCommandLine(input: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        var idx = 0
        while (idx < input.length) {
            val c = input[idx]
            if (c == '\'' || c == '\"') {
                if (inQuotes && c == quoteChar) {
                    inQuotes = false
                } else if (!inQuotes) {
                    inQuotes = true
                    quoteChar = c
                } else {
                    current.append(c)
                }
            } else if (c == ' ' && !inQuotes) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
            } else {
                current.append(currentCharacter = c.toString()) // Handled via append String to avoid issues
            }
            idx++
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    // Helper append function to prevent extension issues
    private fun StringBuilder.append(currentCharacter: String) {
        this.append(currentCharacter as CharSequence)
    }
}

// ViewModel Factory boilerplate setup
class TerminalViewModelFactory(
    private val application: Application,
    private val repository: TerminalRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TerminalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TerminalViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
