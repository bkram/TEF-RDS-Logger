package org.fmdx.rdslogger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalUriHandler
import org.fmdx.rdslogger.ui.theme.TefRdsLoggerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onIpChange: (String) -> Unit,
    onToggleConnection: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topBarState = rememberTopAppBarState()
    val listState = rememberLazyListState()
    var showAbout by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.lines.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TEF RDS Logger",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    Icon(imageVector = Icons.Default.Wifi, contentDescription = null)
                },
                actions = {
                    IconButton(onClick = {
                        showHelp = true
                        showAbout = false
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help")
                    }
                    IconButton(onClick = {
                        showAbout = true
                        showHelp = false
                    }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "About")
                    }
                },
                scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topBarState),
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Connect") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Capture") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Decode") }
                )
            }

            if (selectedTab == 0) {
                OutlinedTextField(
                    value = state.ipAddress,
                    onValueChange = onIpChange,
                    label = { Text("Receiver IP") },
                    placeholder = { Text("192.168.1.10") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRunning
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleConnection,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving
                    ) {
                        val label = if (state.isRunning) "Stop" else "Start"
                        Icon(
                            imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isRunning && state.lines.isNotEmpty() && !state.isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                        enabled = state.lines.isNotEmpty() && !state.isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear")
                    }
                    TextButton(
                        onClick = onQuit,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quit")
                    }
                }
            }

            when (selectedTab) {
                1 -> {
                    StatusRow(state = state)
                    CaptureCard(state = state, listState = listState)
                }
                2 -> DecodeCard(state = state)
            }
        }
    }

    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
    }

    if (showHelp) {
        BackHandler { showHelp = false }
        HelpScreen(onBack = { showHelp = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val uriHandler = LocalUriHandler.current
    val links = listOf(
        "Project site" to "https://github.com/bkram/tef-rds-logger",
        "fm-dx-app" to "https://github.com/bkram/fm-dx-app",
        "TEF6686 ESP32" to "https://github.com/PE5PVB/TEF6686_ESP32"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("TEF RDS Logger", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Lightweight RDS logging companion for TEF WiFi receivers (TCP 7373).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Links",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    links.forEach { (label, url) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(url) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(url) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpScreen(onBack: () -> Unit) {
    val helpItems = listOf(
        HelpItem(
            title = "Getting started",
            description = "Enter your receiver IP, ensure TEF WiFi mode is enabled (TCP 7373), then tap Start."
        ),
        HelpItem(
            title = "Saving logs",
            description = "Stop capture before saving. The file name uses the first PI block and timestamp."
        ),
        HelpItem(
            title = "TEF WiFi protocol",
            description = "Uses the TEF6686_ESP32 RDS Spy stream on TCP 7373. Ensure the device is on the same network."
        ),
        HelpItem(
            title = "Troubleshooting",
            description = "If no data shows, confirm the IP, port accessibility (nc -vz <ip> 7373), and that the receiver outputs RDS."
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(helpItems) { item ->
                HelpCard(item)
            }
        }
    }
}

@Composable
private fun HelpCard(item: HelpItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class HelpItem(
    val title: String,
    val description: String
)

@Composable
private fun StatusRow(state: UiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (state.isRunning) "Capturing…" else "Idle",
            color = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = if (state.totalLines > state.lines.size && state.lines.isNotEmpty()) {
                "${state.totalLines} lines (showing last ${state.lines.size})"
            } else {
                "${state.totalLines} lines"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CaptureCard(state: UiState, listState: LazyListState) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Captured groups",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            if (state.lines.isEmpty()) {
                Text(
                    text = "Waiting for RDS groups…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
                ) {
                    items(state.lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecodeCard(state: UiState) {
    val decoded = state.decodedServices
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (decoded.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No decoded groups yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(decoded) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PI ${item.pi}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            item.ps?.let {
                                Text("PS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            item.longPs?.takeIf { it.isNotEmpty() }?.let {
                                Text("Long PS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                            item.pty?.let {
                                Text(
                                    text = "PTY: $it${item.ptyLabel?.let { label -> " ($label)" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.eon.isNotEmpty()) {
                                Text("EON services", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(item.eon.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item.tp?.let { Text(if (it) "TP" else "No TP", style = MaterialTheme.typography.bodySmall) }
                            item.ta?.let { Text(if (it) "TA" else "No TA", style = MaterialTheme.typography.bodySmall) }
                            item.ms?.let { Text(if (it) "Music" else "Speech", style = MaterialTheme.typography.bodySmall) }
                            if (item.rt?.isNotEmpty() == true) {
                                Text("Radiotext:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(item.rt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item.ptyn?.let {
                                Text("PTYN: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.rtPlus.isNotEmpty()) {
                                Text("RT+ tags:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                item.rtPlus.forEach { tag ->
                                    Text(tag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item.ecc?.let {
                                Text("ECC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text("%02X".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item.lic?.let {
                                Text("LIC", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item.pin?.let {
                                Text("PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.di.isNotEmpty()) {
                                Text("DI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                item.di.forEach { flag ->
                                    Text(
                                        "${flag.label}: ${flag.value}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            item.ct?.let {
                                Text("CT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.eon.isNotEmpty()) {
                                Text("EON", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(item.eon.joinToString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.af.isNotEmpty()) {
                                Text("AF list: ${item.af.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    TefRdsLoggerTheme {
        MainScreen(
            state = UiState(
                ipAddress = "192.168.0.20",
                isRunning = true,
                totalLines = 2,
                lines = listOf(
                    "A1B2 C3D4 E5F6 0A0B @2024/10/08 13:22:15",
                    "1234 5678 9ABC DEF0 @2024/10/08 13:22:16"
                )
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onIpChange = {},
            onToggleConnection = {},
            onSave = {},
            onClear = {},
            onQuit = {}
        )
    }
}
