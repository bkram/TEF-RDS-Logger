package org.fmdx.rdslogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.fmdx.rdslogger.ui.theme.TefRdsLoggerTheme
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("rdslogger_prefs", MODE_PRIVATE)
        val lastIp = prefs.getString("last_ip", "").orEmpty()
        if (lastIp.isNotBlank()) {
            viewModel.updateIp(lastIp)
        }

        setContent {
            TefRdsLoggerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                        }
                    }
                }

                MainScreen(
                    state = uiState,
                    snackbarHostState = snackbarHostState,
                    onIpChange = { ip ->
                        viewModel.updateIp(ip)
                        prefs.edit { putString("last_ip", ip.trim()) }
                    },
                    onToggleConnection = {
                        if (uiState.isRunning) viewModel.stop() else viewModel.start(uiState.ipAddress)
                    },
                    onSave = { viewModel.saveToFile(applicationContext) },
                    onClear = viewModel::clearLines,
                    onQuit = { finish() }
                )
            }
        }
    }
}
