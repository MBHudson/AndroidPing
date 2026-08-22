package com.pingtrace.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingtrace.app.pinger.PingResult
import com.pingtrace.app.pinger.PingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Immutable UI state for the single PingTrace screen. */
data class PingUiState(
    val isRunning: Boolean = false,
    val samples: List<PingResult> = emptyList(),
)

/**
 * Holds all ping state and drives the background ping loop.
 *
 * The loop runs on [Dispatchers.IO] so the UI stays responsive; each completed
 * sample is appended to the [PingUiState.samples] StateFlow list (cap applied),
 * which the line chart observes to update live.
 */
class PingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private var loopJob: Job? = null

    /** Starts a new ping loop against [host] every [intervalSec] seconds. */
    fun start(host: String, intervalSec: Double) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return

        stop()

        val intervalMs = maxOf(MIN_INTERVAL_MS, (intervalSec * 1000).toLong())
        _uiState.value = PingUiState(isRunning = true, samples = emptyList())

        var counter = 0L
        loopJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val sample = PingService.ping(trimmed, counter)
                counter++
                val appended = (_uiState.value.samples + sample).takeLast(MAX_SAMPLES)
                _uiState.value = _uiState.value.copy(samples = appended)
                if (!isActive) break
                delay(intervalMs)
            }
        }
    }

    /** Stops the running ping loop and flips the running flag off. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        _uiState.value = _uiState.value.copy(isRunning = false)
    }

    override fun onCleared() {
        loopJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_SAMPLES = 120
        private const val MIN_INTERVAL_MS = 500L
    }
}
