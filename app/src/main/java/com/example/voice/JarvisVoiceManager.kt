package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

data class VoiceUiState(
    val isListening: Boolean = false,
    val isWakeWordStandby: Boolean = false,
    val isSpeaking: Boolean = false,
    val isTtsReady: Boolean = false,
    val isSpeechRecognitionAvailable: Boolean = true,
    val partialTranscript: String = "",
    val rmsDb: Float = 0f,
    val availableVoices: List<VoiceInfo> = emptyList(),
    val statusMessage: String? = null
)

data class VoiceInfo(
    val name: String,
    val displayLabel: String,
    val locale: String
)

class JarvisVoiceManager(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(
        VoiceUiState(
            isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        )
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var rawVoices: List<Voice> = emptyList()
    private var speechRecognizer: SpeechRecognizer? = null

    private var currentTranscriptCallback: ((String) -> Unit)? = null
    private var wakeWordCallback: ((String) -> Unit)? = null
    private var wakeStandbyActive = false
    private var onSpeakingDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                engine?.language = Locale.getDefault()
                val voices = try {
                    engine?.voices
                        ?.filter { !it.isNetworkConnectionRequired || it.locale.language == Locale.getDefault().language }
                        ?.sortedWith(compareBy({ it.locale.displayLanguage }, { it.name }))
                        .orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
                rawVoices = voices
                val voiceInfos = voices.take(25).mapIndexed { index, v ->
                    val quality = if (v.quality >= Voice.QUALITY_HIGH) "HD" else "Std"
                    VoiceInfo(
                        name = v.name,
                        displayLabel = "${v.locale.displayLanguage} (${v.locale.country.ifEmpty { "Global" }}) • $quality #${index + 1}",
                        locale = v.locale.toLanguageTag()
                    )
                }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("_LAST") == true) {
                            _state.update { it.copy(isSpeaking = false) }
                            mainHandler.post { onSpeakingDoneCallback?.invoke() }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = false) }
                    }
                })
                _state.update { it.copy(isTtsReady = true, availableVoices = voiceInfos) }
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status: $status")
                _state.update {
                    it.copy(
                        isTtsReady = false,
                        statusMessage = "Text-to-speech engine is unavailable on this device."
                    )
                }
            }
        }
    }

    fun startSingleCapture(onResult: (String) -> Unit) {
        wakeStandbyActive = false
        wakeWordCallback = null
        currentTranscriptCallback = onResult
        _state.update { it.copy(isWakeWordStandby = false, statusMessage = null) }
        stopSpeaking()
        launchRecognizer(isWakeMode = false)
    }

    fun startWakeWordStandby(onCommand: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.update {
                it.copy(
                    isSpeechRecognitionAvailable = false,
                    statusMessage = "On-device Speech Recognition service is not available."
                )
            }
            return
        }
        wakeStandbyActive = true
        wakeWordCallback = onCommand
        currentTranscriptCallback = null
        _state.update {
            it.copy(
                isWakeWordStandby = true,
                statusMessage = "Say \"Jarvis\" followed by your request"
            )
        }
        launchRecognizer(isWakeMode = true)
    }

    fun stopListening() {
        wakeStandbyActive = false
        currentTranscriptCallback = null
        wakeWordCallback = null
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer", e)
            }
            _state.update {
                it.copy(
                    isListening = false,
                    isWakeWordStandby = false,
                    partialTranscript = "",
                    rmsDb = 0f
                )
            }
        }
    }

    private fun launchRecognizer(isWakeMode: Boolean) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.update {
                    it.copy(
                        isSpeechRecognitionAvailable = false,
                        isListening = false,
                        statusMessage = "Speech recognition is unavailable on this device."
                    )
                }
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.update { it.copy(isListening = true, partialTranscript = "") }
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            _state.update { it.copy(rmsDb = rmsdB.coerceIn(0f, 12f)) }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            if (!isWakeMode) {
                                _state.update { it.copy(isListening = false, rmsDb = 0f) }
                            }
                        }

                        override fun onError(error: Int) {
                            _state.update { it.copy(isListening = false, rmsDb = 0f) }
                            if (wakeStandbyActive && isWakeMode) {
                                // Restart wake-word standby loop cleanly after transient timeout/no-match
                                mainHandler.postDelayed({
                                    if (wakeStandbyActive) launchRecognizer(isWakeMode = true)
                                }, 450L)
                            } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                                val msg = when (error) {
                                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                        "Network error during speech recognition."
                                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic to try again."
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                                    else -> "Speech recognition error ($error)."
                                }
                                _state.update { it.copy(statusMessage = msg) }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim().orEmpty()
                            _state.update {
                                it.copy(isListening = false, partialTranscript = "", rmsDb = 0f)
                            }

                            if (isWakeMode && wakeStandbyActive) {
                                val lower = text.lowercase(Locale.getDefault())
                                val jarvisIdx = lower.indexOf("jarvis")
                                if (jarvisIdx >= 0) {
                                    val command = text.substring(jarvisIdx + "jarvis".length)
                                        .trim(',', '.', ' ', ':', '!')
                                    if (command.isNotBlank()) {
                                        wakeStandbyActive = false
                                        _state.update { it.copy(isWakeWordStandby = false) }
                                        wakeWordCallback?.invoke(command)
                                    } else {
                                        // User said "Hey Jarvis" alone -> switch to active single capture
                                        _state.update {
                                            it.copy(
                                                isWakeWordStandby = false,
                                                statusMessage = "Listening for your command..."
                                            )
                                        }
                                        val cb = wakeWordCallback
                                        if (cb != null) {
                                            startSingleCapture(cb)
                                        }
                                    }
                                } else if (wakeStandbyActive) {
                                    mainHandler.postDelayed({
                                        if (wakeStandbyActive) launchRecognizer(isWakeMode = true)
                                    }, 350L)
                                }
                            } else if (text.isNotBlank()) {
                                currentTranscriptCallback?.invoke(text)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (partial.isNotEmpty()) {
                                _state.update { it.copy(partialTranscript = partial) }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _state.update {
                    it.copy(
                        isListening = false,
                        isWakeWordStandby = false,
                        statusMessage = e.localizedMessage ?: "Failed to start microphone."
                    )
                }
            }
        }
    }

    fun speak(
        markdownText: String,
        speechRate: Float = 1.0f,
        selectedVoiceName: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val engine = tts ?: return
        if (!_state.value.isTtsReady) return

        val cleanText = stripMarkdownForSpeech(markdownText)
        if (cleanText.isBlank()) {
            onComplete?.invoke()
            return
        }

        onSpeakingDoneCallback = onComplete
        engine.stop()
        engine.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))

        if (!selectedVoiceName.isNullOrBlank()) {
            rawVoices.firstOrNull { it.name == selectedVoiceName }?.let { chosen ->
                engine.voice = chosen
            }
        }

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1000) - 100
        val chunks = chunkText(cleanText, maxLen)
        val batchId = UUID.randomUUID().toString().take(8)
        _state.update { it.copy(isSpeaking = true) }

        chunks.forEachIndexed { idx, chunk ->
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val suffix = if (idx == chunks.lastIndex) "_LAST" else "_$idx"
            engine.speak(chunk, queueMode, null, "$batchId$suffix")
        }
    }

    fun stopSpeaking() {
        onSpeakingDoneCallback = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.update { it.copy(isSpeaking = false) }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun release() {
        wakeStandbyActive = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {
            }
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }

    private fun stripMarkdownForSpeech(input: String): String {
        return input
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("\\*\\*([^*]*)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]*)\\*"), "$1")
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("\\|"), ", ")
            .replace(Regex("---+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun chunkText(text: String, maxChunkSize: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxChunkSize).coerceAtMost(text.length)
            if (end < text.length) {
                val lastPeriod = text.lastIndexOf('.', end)
                if (lastPeriod > start + maxChunkSize / 2) {
                    end = lastPeriod + 1
                }
            }
            result.add(text.substring(start, end).trim())
            start = end
        }
        return result
    }

    private companion object {
        const val TAG = "JarvisVoiceManager"
    }
}
