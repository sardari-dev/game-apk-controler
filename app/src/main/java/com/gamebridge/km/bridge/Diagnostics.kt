package com.gamebridge.km.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-memory diagnostics ring buffer shared by the accessibility
 * service and the app UI (same process). Helps troubleshooting on real
 * devices: key events, mouse events, gesture stats.
 */
object Diagnostics {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private const val MAX = 300

    fun log(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg"
        synchronized(this) {
            _lines.value = (_lines.value + line).takeLast(MAX)
        }
    }

    fun clear() {
        synchronized(this) { _lines.value = emptyList() }
    }
}
