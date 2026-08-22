package com.pingtrace.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pingtrace.app.PingUiState
import com.pingtrace.app.PingViewModel
import com.pingtrace.app.ui.components.PingChart

/**
 * The single PingTrace screen: config inputs on top, live line chart below,
 * start/stop controls. Drives [PingViewModel].
 */
@Composable
fun PingScreen(
    viewModel: PingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    var host by rememberSaveable { mutableStateOf("8.8.8.8") }
    var intervalText by rememberSaveable { mutableStateOf("1") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "PingTrace",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host / IP address") },
            singleLine = true,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = intervalText,
            onValueChange = { new ->
                // Only allow digits and a single decimal point.
                if (new.isEmpty() || new.all { it.isDigit() || it == '.' }) {
                    intervalText = new
                }
            },
            label = { Text("Ping interval (seconds)") },
            singleLine = true,
            enabled = !state.isRunning,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.isRunning) {
                Button(
                    onClick = {
                        val interval = intervalText.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
                        viewModel.start(host, interval)
                    },
                ) {
                    Text("Start")
                }
            } else {
                Button(
                    onClick = { viewModel.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Stop")
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = statusText(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PingChart(samples = state.samples)

        Text(
            text = if (state.isRunning) "Status: measuring\u2026" else "Status: idle",
            style = MaterialTheme.typography.labelLarge,
            color = if (state.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusText(state: PingUiState): String {
    if (state.samples.isEmpty()) return "No pings yet."
    val last = state.samples.last()
    val reached = state.samples.count { it.succeeded }
    val avg = state.samples.filter { it.succeeded }
        .map { it.rttMs!! }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toLong()
    return buildString {
        append("Samples: ${state.samples.size} · Reached: $reached")
        if (avg != null) append(" · Avg: ${avg}ms")
        append("\nLast: ")
        append(if (last.succeeded) "${last.rttMs}ms" else "unreachable")
    }
}
