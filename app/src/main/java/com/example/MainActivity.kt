package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.AppDatabase
import com.example.data.TerminalLogEntity
import com.example.data.TerminalRepository
import com.example.ui.TerminalViewModel
import com.example.ui.TerminalViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.SolidBlack
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init database context and repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TerminalRepository(
            userDao = database.userDao(),
            terminalLogDao = database.terminalLogDao(),
            virtualFileDao = database.virtualFileDao()
        )

        val viewModel: TerminalViewModel by viewModels {
            TerminalViewModelFactory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current

                // Securely transition back into sealed HEX-mode whenever the app is minimized
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            viewModel.lockTerminal()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SolidBlack
                ) {
                    TerminalConsoleScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalConsoleScreen(viewModel: TerminalViewModel) {
    val logs by viewModel.logs.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()

    var inputValue by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // Keep scrolling list bottom-aligned for real-time output stream feel
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SolidBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SECURE_SHELL://",
                            color = TerminalGreen,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeSession.isNotEmpty()) "@$activeSession" else "STNDBY",
                            color = TerminalWhite,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isUnlocked) TerminalGreen else Color.Red)
                        )
                        Text(
                            text = if (isUnlocked) "UNLOCKED" else "HEX_MASKED",
                            color = if (isUnlocked) TerminalGreen else Color.Red,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0E11),
                    titleContentColor = TerminalGreen
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0E11))
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Secondary console banner indicating system registers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF13161A))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOST_NODE: $currentUsername",
                        color = TerminalGreen,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = if (activeSession.isNotEmpty()) "MATRIX_LINK: ACTIVE_HANDSHAKE" else "SHIELD: ARMORED_TUNNEL",
                        color = if (isUnlocked) TerminalGreen else Color.Yellow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$ ",
                        color = TerminalGreen,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    BasicTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cli_input_field")
                            .padding(vertical = 4.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TerminalWhite
                        ),
                        cursorBrush = SolidColor(TerminalGreen),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (inputValue.isNotBlank()) {
                                    viewModel.executeCommand(inputValue)
                                    inputValue = ""
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = {
                            if (inputValue.isNotBlank()) {
                                viewModel.executeCommand(inputValue)
                                inputValue = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("cli_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute Directive",
                            tint = TerminalGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Monitor scanning screen rendering layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SolidBlack)
                .drawWithContent {
                    drawContent()
                    // Recreate immersive phosphor matrix scanline visual effects
                    val spacingLines = 8.dp.toPx()
                    val lineCount = (size.height / spacingLines).toInt()
                    for (i in 0..lineCount) {
                        val y = i * spacingLines
                        drawLine(
                            color = Color(0x0C00FF33), // Ultra light green transparent grid glow
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    TerminalLineItem(log, isUnlocked)
                }
            }
        }
    }
}

@Composable
fun TerminalLineItem(log: TerminalLogEntity, isUnlocked: Boolean) {
    val displayContent = remember(log.content, isUnlocked, log.type) {
        when {
            // Mask chat packet exchanges to high-security HEX structures unless authorized
            (log.type == "msg_in" || log.type == "msg_out") && !isUnlocked -> {
                convertStringToHexDump(log.content)
            }
            else -> log.content
        }
    }

    val displayPrefix = remember(log.type, log.sender, log.recipient, isUnlocked) {
        when (log.type) {
            "input" -> "$ "
            "msg_out" -> {
                val lockLabel = if (!isUnlocked) "[HEX_ENC]" else "[CLEAR]"
                "me -> @${log.recipient} $lockLabel: "
            }
            "msg_in" -> {
                val lockLabel = if (!isUnlocked) "[HEX_ENC]" else "[CLEAR]"
                "@${log.sender} -> me $lockLabel: "
            }
            else -> "" // Direct execution feed line output
        }
    }

    val textColor = remember(log.type, log.content) {
        when (log.type) {
            "input" -> TerminalWhite
            "msg_out" -> TerminalGreen
            "msg_in" -> Color(0xFF8BFA8B)
            "system" -> {
                when {
                    log.content.contains("[ERROR]") || log.content.contains("[CRITICAL]") || log.content.contains("[SEC_ALERT]") -> Color.Red
                    log.content.contains("[WARN]") -> Color.Yellow
                    log.content.contains("[OK]") -> Color(0xFF33FF99)
                    else -> TerminalGreen
                }
            }
            else -> TerminalGreen
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        if (displayPrefix.isNotEmpty()) {
            Text(
                text = displayPrefix,
                color = if (log.type == "input") TerminalGreen else textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = displayContent,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun convertStringToHexDump(text: String): String {
    val bytes = text.toByteArray(StandardCharsets.UTF_8)
    return bytes.joinToString(separator = " ", prefix = "[", postfix = "]") { byte ->
        String.format("0x%02X", byte)
    }
}
