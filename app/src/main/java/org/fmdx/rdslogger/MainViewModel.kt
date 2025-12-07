package org.fmdx.rdslogger

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val ipAddress: String = "",
    val isRunning: Boolean = false,
    val isSaving: Boolean = false,
    val lines: List<String> = emptyList(),
    val totalLines: Int = 0,
    val decodedServices: List<RdsSnapshot> = emptyList()
)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private var tefSocket: Socket? = null
    private var tefJob: Job? = null
    private var userStopped = false
    private val allLines = mutableListOf<String>()
    private val rdsDecoder = RdsDecoder()

    private val hexLineRegex = Regex("^[0-9a-fA-F-]{16}$")
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    private val filenameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
    private val tefWifiPort = 7373
    private val tefHandshakeCommand = "?F\n"
    private val maxLines = 1000

    fun updateIp(ip: String) {
        _uiState.update { it.copy(ipAddress = ip) }
    }

    fun start(ip: String = _uiState.value.ipAddress) {
        val trimmedIp = ip.trim()
        if (trimmedIp.isEmpty()) {
            sendMessage("Enter a valid host")
            return
        }
        if (_uiState.value.isRunning) return

        userStopped = false
        resetLog()
        connectTefWifi(trimmedIp)
    }

    fun stop() {
        userStopped = true
        tefJob?.cancel()
        tefSocket?.close()
        tefSocket = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun clearLines() {
        synchronized(allLines) { allLines.clear() }
        _uiState.update { it.copy(lines = emptyList(), totalLines = 0) }
    }

    fun saveToFile(context: Context) {
        val snapshot = _uiState.value
        if (snapshot.isRunning) {
            sendMessage("Stop capture before saving")
            return
        }
        val allSnapshot = synchronized(allLines) { allLines.toList() }
        if (allSnapshot.isEmpty()) {
            sendMessage("No data to save")
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fileName = buildFileName(allSnapshot)
            val file = File(directory, fileName)

            try {
                BufferedWriter(FileWriter(file)).use { writer ->
                    allSnapshot.forEach { writer.appendLine(it) }
                }
                sendMessage("Saved to file: ${file.name}")
            } catch (_: IOException) {
                sendMessage("Failed to save file")
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun appendLine(formattedLine: String) {
        val timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(timestampFormatter)
        val withTime = "$formattedLine @$timestamp"
        synchronized(allLines) { allLines.add(withTime) }
        _uiState.update { current ->
            val updated = (current.lines + withTime).takeLast(maxLines)
            current.copy(lines = updated, totalLines = current.totalLines + 1)
        }
    }

    private fun resetLog() {
        synchronized(allLines) { allLines.clear() }
        rdsDecoder.reset()
        _uiState.update { it.copy(lines = emptyList(), totalLines = 0, decodedServices = emptyList()) }
    }

    @VisibleForTesting
    internal fun formatLine(line: String): String {
        val upper = line.uppercase(Locale.getDefault())
        return upper.chunked(4).joinToString(" ")
    }

    @VisibleForTesting
    internal fun buildFileName(lines: List<String>): String {
        val prefix = lines.firstOrNull()
            ?.take(4)
            ?.takeIf { it.all { ch -> ch.isLetterOrDigit() && ch != '-' } }
            ?: "rds"
        val timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(filenameFormatter)
        return "${prefix}_$timestamp.spy"
    }

    private fun sendMessage(message: String) {
        viewModelScope.launch {
            eventsChannel.send(UiEvent.Message(message))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private fun connectTefWifi(host: String) {
        tefJob?.cancel()
        tefJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                tefSocket = socket
                socket.connect(InetSocketAddress(host, tefWifiPort), 5_000)
                _uiState.update { it.copy(isRunning = true, lines = emptyList()) }

                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(tefHandshakeCommand)
                writer.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (trimmed == "G:") {
                        val dataLine = reader.readLine() ?: break
                        handleIncomingPayload("G:\n${dataLine.trim()}")
                    } else if (trimmed.startsWith("G:")) {
                        handleIncomingPayload(trimmed)
                    }
                }
            } catch (t: Throwable) {
                if (!userStopped) {
                    sendMessage("Connection failed: ${t.localizedMessage ?: "unknown error"}")
                }
            } finally {
                tefSocket?.close()
                tefSocket = null
                if (!userStopped) {
                    _uiState.update { it.copy(isRunning = false) }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun handleIncomingPayload(payload: String) {
        val lines = payload.replace("\r", "").lines()
        var idx = 0
        while (idx < lines.size) {
            if (lines[idx].trim() == "G:" && idx + 1 < lines.size) {
                val dataLine = lines[idx + 1].trim()
                if (dataLine.startsWith("RESET")) {
                    resetLog()
                } else if (dataLine.length == 16 && hexLineRegex.matches(dataLine)) {
                    appendLine(formatLine(dataLine))
                    rdsDecoder.ingest(dataLine)?.let {
                        _uiState.update { current ->
                            current.copy(decodedServices = rdsDecoder.snapshots())
                        }
                    }
                }
                idx += 2
            } else {
                idx++
            }
        }
    }

    @VisibleForTesting
    internal class RdsDecoder {
        private val services = mutableMapOf<String, RdsServiceState>()
        private val rtPlusAid = 0x4BD7
        private var hasRtPlus = false

        fun reset() {
            services.clear()
            hasRtPlus = false
        }

        fun ingest(hexLine: String): RdsServiceState? {
            val sanitized = hexLine.trim()
            if (sanitized.length != 16 || !sanitized.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val words = sanitized.chunked(4).map { it.toInt(16) }
            val pi = "%04X".format(words[0])
            val groupType = (words[1] shr 12) and 0xF
            val versionB = (words[1] and 0x0800) != 0
            val pty = (words[1] shr 5) and 0x1F
            val tp = (words[1] and 0x0400) != 0
            val ta = (words[1] and 0x0010) != 0
            val state = services.getOrPut(pi) { RdsServiceState(pi) }
            state.pty = pty
            state.tp = tp
            state.ta = ta

            when (groupType) {
                0 -> handleBasicTuning(state, words, versionB)
                2 -> handleRadiotext(state, words, versionB)
                10 -> handlePtyn(state, words)
                3 -> handleOda(words)
                1 -> {
                    handlePin(state, words, versionB)
                    handleSlowLabeling(state, words, versionB)
                }
                11 -> handleRtPlus(state, words, versionB)
                14 -> handleEon(state, words, versionB)
                15 -> handleLongPs(state, words, versionB)
                4 -> handleCt(state, words, versionB)
            }
            return state
        }

        fun snapshots(): List<RdsSnapshot> {
            return services.values.map { state ->
                RdsSnapshot(
                    pi = state.pi,
                    ps = state.psString(),
                    rt = state.rtString(),
                    pty = state.pty,
                    ptyLabel = state.pty?.let { ptyLabels[it] },
                    tp = state.tp,
                    ta = state.ta,
                    ms = state.ms,
                    af = state.afList(),
                    longPs = state.longPsString(),
                    rtPlus = state.rtPlusTags.toList(),
                    ptyn = state.ptynString(),
                    di = state.diFlags(),
                    eon = state.eonList(),
                    ecc = state.ecc,
                    lic = state.lic,
                    pin = state.pin,
                    ct = state.ct
                )
            }.sortedBy { it.pi }
        }

        private fun handleBasicTuning(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            val segment = words[1] and 0x3
            val c = words[2]
            val d = words[3]
            state.ms = (words[1] and 0x0008) != 0
            val chars = if (versionB) {
                listOf(d shr 8, d and 0xFF)
            } else {
                listOf(c shr 8, c and 0xFF, d shr 8, d and 0xFF)
            }
            val psChars = state.ps.toMutableList()
            chars
                // PS is always conveyed in block D; ignore block C contents per IEC 62106 / UECP SLC layout
                .takeLast(2)
                .forEachIndexed { idx, code ->
                    val pos = segment * 2 + idx
                    if (pos < psChars.size && code in 0x20..0x7E) {
                        psChars[pos] = code.toChar()
                    }
                }
            state.ps = psChars.joinToString("")
            // DI bit in block B (bit2) with segment address in bits0-1
            val diIndex = words[1] and 0x3
            val diValue = (words[1] shr 2) and 0x1
            val diMutable = state.di.toMutableList()
            if (diIndex in diMutable.indices) {
                val previous = diMutable[diIndex]
                diMutable[diIndex] = diValue
                if (previous != diValue) {
                    logDiUpdate(state.pi, diIndex, diValue, words)
                }
            }
            state.di = diMutable
            state.updatePs()

            if (!versionB) {
                val byte1 = c shr 8
                val byte2 = c and 0xFF
                decodeAfByte(state, byte1)
                decodeAfByte(state, byte2)
            }
        }

        private fun decodeAfByte(state: RdsServiceState, value: Int) {
            when {
                value in 1..204 -> {
                    if (state.afCount > 0 && state.afs.size >= state.afCount) return
                    val freq = 87.5 + (value.toDouble() / 10.0)
                    state.afs.add("%.1f".format(Locale.US, freq))
                }
                value in 224..249 -> {
                    state.afCount = value - 224
                    state.afs.clear()
                }
            }
        }

        private fun handleRadiotext(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            val abFlag = (words[1] and 0x0010) != 0
            if (abFlag != state.rtAB) {
                state.rtAB = abFlag
                state.clearRt()
            }
            val segment = words[1] and 0x0F
            if (versionB) {
                val pos = segment * 2
                writeRtChars(state, pos, listOf(words[3] shr 8, words[3] and 0xFF))
            } else {
                val pos = segment * 4
                writeRtChars(state, pos, listOf(words[2] shr 8, words[2] and 0xFF, words[3] shr 8, words[3] and 0xFF))
            }
        }

        private fun writeRtChars(state: RdsServiceState, start: Int, chars: List<Int>) {
            val rtChars = state.rt.toMutableList()
            chars.forEachIndexed { idx, code ->
                val pos = start + idx
                if (pos in rtChars.indices) {
                    val ch = mapValidChar(code) ?: return@forEachIndexed
                    rtChars[pos] = ch
                }
            }
            state.rt = rtChars.joinToString("")
        }

        private fun handlePtyn(state: RdsServiceState, words: List<Int>) {
            val segment = words[1] and 0x0F
            val chars = listOf(words[2] shr 8, words[2] and 0xFF)
            val ptynChars = state.ptyn.toMutableList()
            chars.forEachIndexed { idx, code ->
                val pos = segment * 2 + idx
                if (pos in ptynChars.indices) {
                    val ch = mapValidChar(code) ?: return@forEachIndexed
                    ptynChars[pos] = ch
                }
            }
            state.ptyn = ptynChars.joinToString("")
        }

        private fun handleOda(words: List<Int>) {
            // Group 3A: AID in block D
            val aid = words[3]
            if (aid == rtPlusAid) hasRtPlus = true
        }

        private fun handlePin(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (versionB) return
            state.pin = "%04X".format(words[3])
        }

        private fun handleSlowLabeling(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (versionB) return
            val blockC = words[2]
            val blockD = words[3]
            val variant = (blockC shr 12) and 0x7
            val data = ((blockC and 0xF) shl 8) or (blockD and 0xFF)
            // Variant 0 of type 1A carries the Extended Country Code (ECC) per UECP/IEC 62106.
            if (variant == 0) {
                state.ecc = data and 0xFF
            }
        }

        private fun handleEon(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (versionB) return
            val otherPi = "%04X".format(words[2])
            state.eonPis.add(otherPi)
        }

        private fun logDiUpdate(pi: String, index: Int, value: Int, words: List<Int>) {
            val blocks = words.joinToString(" ") { "%04X".format(it) }
            Log.d(
                TAG,
                "DI update pi=$pi idx=$index value=$value blocks=$blocks"
            )
        }

        private fun handleCt(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (versionB) return
            val mjd = (words[2] shl 15) or (words[3] shr 1)
            val minute = ((words[3] and 0x01) shl 5) + ((words[2] shr 0) and 0x1F)
            val hour = (words[3] shr 6) and 0x1F
            val offsetSign = if ((words[3] and 0x20) != 0) -1 else 1
            val offsetHalfHours = (words[3] shr 0) and 0x1F
            val offsetMinutes = offsetHalfHours * 30 * offsetSign
            val date = mjdToDate(mjd) ?: return
            val time = try {
                java.time.LocalTime.of(hour, minute)
            } catch (_: Throwable) {
                return
            }
            val formatted = "$date ${time.toString().padEnd(5, '0')} (UTC${if (offsetMinutes >= 0) "+" else ""}${offsetMinutes / 60})"
            state.ct = formatted
        }

        private fun handleRtPlus(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (versionB || !hasRtPlus) return
            val tag1Content = (words[2] shr 12) and 0x0F
            val tag1Start = (words[2] shr 6) and 0x3F
            val tag1Len = words[2] and 0x3F
            val tag2Content = (words[3] shr 12) and 0x0F
            val tag2Start = (words[3] shr 6) and 0x3F
            val tag2Len = words[3] and 0x3F
            state.rtPlusTags.clear()
            listOf(
                rtPlusLabel(tag1Content, state.rt, tag1Start, tag1Len),
                rtPlusLabel(tag2Content, state.rt, tag2Start, tag2Len)
            ).forEach { label ->
                if (label != null) state.rtPlusTags.add(label)
            }
        }

        private fun rtPlusLabel(contentType: Int, rt: String, start: Int, len: Int): String? {
            if (len <= 0 || start < 0 || start + len > rt.length) return null
            val text = rt.drop(start).take(len).trim()
            if (text.isEmpty()) return null
            val label = when (contentType) {
                1 -> "Item.Title"
                4 -> "Artist"
                31 -> "Info"
                32 -> "News"
                36 -> "Sport"
                39 -> "Weather"
                else -> "Tag$contentType"
            }
            return "$label: $text"
        }

        private fun handleLongPs(state: RdsServiceState, words: List<Int>, versionB: Boolean) {
            if (!versionB) return
            val segment = words[1] and 0x0F
            val chars = listOf(words[3] shr 8, words[3] and 0xFF)
            val pos = segment * 2
            val longPsChars = state.longPs.toMutableList()
            chars.forEachIndexed { idx, code ->
                val target = pos + idx
                if (target in longPsChars.indices) {
                    val ch = mapValidChar(code) ?: return@forEachIndexed
                    longPsChars[target] = ch
                }
            }
            state.longPs = longPsChars.joinToString("")
        }

        private fun mapValidChar(code: Int): Char? {
            return when (code) {
                in 0x20..0x7E -> code.toChar()
                0x00 -> ' '
                else -> null
            }
        }

        private fun mjdToDate(mjd: Int): java.time.LocalDate? {
            if (mjd <= 0) return null
            var k = mjd + 2400001 + 68569
            val n = (4 * k) / 146097
            k -= (146097 * n + 3) / 4
            val i = (4000 * (k + 1)) / 1461001
            k = k - (1461 * i) / 4 + 31
            val j = (80 * k) / 2447
            val day = k - (2447 * j) / 80
            k = j / 11
            val month = j + 2 - 12 * k
            val year = 100 * (n - 49) + i + k
            return try {
                java.time.LocalDate.of(year, month, day)
            } catch (_: Throwable) {
                null
            }
        }

        companion object {
            private const val TAG = "RdsDecoder"
        }
    }

    internal data class RdsServiceState(
        val pi: String,
        var ps: String = " ".repeat(8),
        var psCandidate: String = "",
        var psRepeat: Int = 0,
        var psCommitted: String? = null,
        var rt: String = " ".repeat(64),
        var rtAB: Boolean = false,
        var ptyn: String = " ".repeat(8),
        val afs: MutableSet<String> = linkedSetOf(),
        var afCount: Int = 0,
        var pty: Int? = null,
        var tp: Boolean? = null,
        var ta: Boolean? = null,
        var ms: Boolean? = null,
        var longPs: String = " ".repeat(64),
        val rtPlusTags: MutableList<String> = mutableListOf(),
        var di: List<Int> = listOf(-1, -1, -1, -1),
        val eonPis: MutableSet<String> = linkedSetOf(),
        var ecc: Int? = null,
        var lic: String? = null,
        var pin: String? = null,
        var ct: String? = null
    ) {
        fun psString(): String? = psCommitted ?: ps.trim().takeIf { it.isNotEmpty() }
        fun rtString(): String? = rt.trim().takeIf { it.isNotEmpty() }
        fun afList(): List<String> = afs.toList()
        fun longPsString(): String? = longPs.trim().takeIf { it.isNotEmpty() }
        fun ptynString(): String? = ptyn.trim().takeIf { it.isNotEmpty() }
        fun eonList(): List<String> = eonPis.toList()
        fun diFlags(): List<DiFlag> {
            if (di.none { it >= 0 }) return emptyList()
            val labels = listOf("Stereo/Mono", "Artificial head", "Compressed", "Dynamic PTY")
            val flags = mutableListOf<DiFlag>()
            di.forEachIndexed { idx, bit ->
                if (bit >= 0 && idx in labels.indices) {
                    val value = when (idx) {
                        0 -> if (bit == 1) "Stereo" else "Mono"
                        1 -> if (bit == 1) "On" else "Off"
                        2 -> if (bit == 1) "On" else "Off"
                        3 -> if (bit == 1) "Dynamic" else "Static"
                        else -> return@forEachIndexed
                    }
                    flags.add(DiFlag(labels[idx], value))
                }
            }
            return flags
        }
        fun clearRt() {
            rt = " ".repeat(64)
        }

        fun updatePs() {
            val candidate = ps.trim()
            if (candidate.length < 4 || candidate.isBlank()) return
            if (candidate != psCandidate) {
                psCandidate = candidate
                psRepeat = 1
            } else {
                psRepeat++
            }
            if (psRepeat >= 3) {
                psCommitted = psCandidate
            }
        }
    }

    companion object {
        fun decodeLine(line: String): DecodedGroup? {
            val payload = line.substringBefore("@").replace(" ", "").trim()
            if (payload.length != 16 || !payload.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val chunks = payload.chunked(4)
            val words = chunks.map { it.toInt(16) }
            val blockB = words[1]
            val groupType = (blockB shr 12) and 0xF
            val version = if ((blockB and 0x0800) != 0) 'B' else 'A'
            val pty = (blockB shr 5) and 0x1F
            val pi = "%04X".format(words[0])
            val groupCode = "%X$version".format(groupType)
            return DecodedGroup(pi = pi, group = groupCode, pty = pty, raw = line)
        }

        fun decodeLines(lines: List<String>, limit: Int = 200): List<DecodedGroup> {
            return lines.takeLast(limit).mapNotNull { decodeLine(it) }
        }

        private val ptyLabels = listOf(
            "None", "News", "Current Affairs", "Information", "Sport", "Education",
            "Drama", "Culture", "Science", "Varied", "Pop Music", "Rock Music",
            "Easy Listening", "Light Classical", "Serious Classical", "Other Music",
            "Weather", "Finance", "Children’s", "Social Affairs", "Religion", "Phone-In",
            "Travel", "Leisure", "Jazz", "Country", "National Music", "Oldies",
            "Folk", "Documentary", "Alarm Test", "Alarm"
        )
    }
}

data class DecodedGroup(
    val pi: String,
    val group: String,
    val pty: Int,
    val raw: String
)

data class RdsSnapshot(
    val pi: String,
    val ps: String?,
    val rt: String?,
    val pty: Int?,
    val ptyLabel: String?,
    val tp: Boolean?,
    val ta: Boolean?,
    val ms: Boolean?,
    val af: List<String>,
    val longPs: String? = null,
    val rtPlus: List<String> = emptyList(),
    val ptyn: String? = null,
    val di: List<DiFlag> = emptyList(),
    val eon: List<String> = emptyList(),
    val ecc: Int? = null,
    val lic: String? = null,
    val pin: String? = null,
    val ct: String? = null
)

data class DiFlag(val label: String, val value: String)
