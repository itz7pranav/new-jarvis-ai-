package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.JarvisAiService
import com.example.core.AttachmentHelper
import com.example.core.GeneratedDeck
import com.example.core.PendingAttachment
import com.example.core.PptxGenerator
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.GeminiModels
import com.example.data.model.MemoryEntry
import com.example.data.model.UserProfile
import com.example.data.repository.JarvisRepository
import com.example.voice.JarvisVoiceManager
import com.example.voice.VoiceUiState
import com.google.firebase.Firebase
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatScreenUiState(
    val activeConversationId: String? = null,
    val selectedModelId: String = GeminiModels.FLASH,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val streamingStatusNotice: String? = null,
    val pendingAttachment: PendingAttachment? = null,
    val webSearchEnabled: Boolean = true,
    val imageGenMode: Boolean = false,
    val pptGenMode: Boolean = false,
    val editingMessage: ChatMessage? = null,
    val isVoiceOverlayOpen: Boolean = false,
    val errorBanner: String? = null,
    val memoryToast: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModel(
    private val appContext: Context,
    private val repository: JarvisRepository,
    private val aiService: JarvisAiService,
    private val voiceManager: JarvisVoiceManager,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatScreenUiState())
    val uiState: StateFlow<ChatScreenUiState> = _uiState.asStateFlow()

    val voiceState: StateFlow<VoiceUiState> = voiceManager.state

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            val fsError = unwrapFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", fsError ?: error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations(currentUserId)
        .catch { error ->
            val fsError = unwrapFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing conversations", fsError ?: error)
            _uiState.update {
                it.copy(errorBanner = (fsError ?: error).localizedMessage ?: "Failed to load conversations.")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val memories: StateFlow<List<MemoryEntry>> = repository.observeMemories(currentUserId)
        .catch { error ->
            val fsError = unwrapFirestoreException(error)
            if (fsError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing memories", fsError ?: error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val activeMessages: StateFlow<List<ChatMessage>> = _uiState
        .flatMapLatest { state ->
            val convId = state.activeConversationId
            if (convId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, convId)
                .catch { error ->
                    val fsError = unwrapFirestoreException(error)
                    if (fsError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing messages", fsError ?: error)
                    _uiState.update {
                        it.copy(errorBanner = (fsError ?: error).localizedMessage ?: "Failed to load messages.")
                    }
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private var activeGenerationJob: Job? = null
    private var syncedInitialModel = false

    init {
        viewModelScope.launch {
            try {
                val user = Firebase.auth.currentUser
                repository.ensureUserProfile(
                    displayName = user?.displayName ?: "User",
                    email = user?.email,
                    photoUrl = user?.photoUrl?.toString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not ensure user profile", e)
            }
        }

        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null && !syncedInitialModel) {
                    syncedInitialModel = true
                    val normalized = GeminiModels.normalizeFreeTierModel(profile.preferredModel)
                    _uiState.update { it.copy(selectedModelId = normalized) }
                    if (normalized != profile.preferredModel) {
                        try {
                            repository.updateProfileSettings(preferredModel = normalized)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun startNewConversation() {
        stopGenerating()
        voiceManager.stopSpeaking()
        val defaultModel = GeminiModels.normalizeFreeTierModel(
            userProfile.value?.preferredModel ?: _uiState.value.selectedModelId
        )
        _uiState.update {
            it.copy(
                activeConversationId = null,
                selectedModelId = defaultModel,
                imageGenMode = false,
                pptGenMode = false,
                streamingText = "",
                pendingAttachment = null,
                editingMessage = null,
                errorBanner = null
            )
        }
    }

    fun selectConversation(conversationId: String) {
        if (_uiState.value.activeConversationId == conversationId) return
        stopGenerating()
        voiceManager.stopSpeaking()
        val rawModel = conversations.value.firstOrNull { it.id == conversationId }?.modelId
            ?: userProfile.value?.preferredModel
        val convModel = GeminiModels.normalizeFreeTierModel(rawModel)
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                selectedModelId = convModel,
                imageGenMode = false,
                pptGenMode = false,
                streamingText = "",
                pendingAttachment = null,
                editingMessage = null,
                errorBanner = null
            )
        }
    }

    fun setSelectedModel(modelId: String) {
        val safeModelId = GeminiModels.normalizeFreeTierModel(modelId)
        _uiState.update { it.copy(selectedModelId = safeModelId) }
        val activeConvId = _uiState.value.activeConversationId
        if (activeConvId != null) {
            viewModelScope.launch {
                try {
                    repository.updateConversation(conversationId = activeConvId, modelId = safeModelId)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun toggleWebSearch() {
        _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
    }

    fun toggleImageGenMode() {
        _uiState.update {
            val next = !it.imageGenMode
            it.copy(
                imageGenMode = next,
                pptGenMode = if (next) false else it.pptGenMode
            )
        }
    }

    fun togglePptGenMode() {
        _uiState.update {
            val next = !it.pptGenMode
            it.copy(
                pptGenMode = next,
                imageGenMode = if (next) false else it.imageGenMode
            )
        }
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val attachment = AttachmentHelper.loadImageBitmap(appContext, uri)
                _uiState.update { it.copy(pendingAttachment = attachment, errorBanner = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Could not load image.") }
            }
        }
    }

    fun attachDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val attachment = AttachmentHelper.loadDocument(appContext, uri)
                _uiState.update { it.copy(pendingAttachment = attachment, errorBanner = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Could not load document.") }
            }
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun beginEditMessage(message: ChatMessage) {
        _uiState.update { it.copy(editingMessage = message) }
    }

    fun cancelEditMessage() {
        _uiState.update { it.copy(editingMessage = null) }
    }

    fun sendMessage(rawPrompt: String, spokenInitiated: Boolean = false) {
        val editing = _uiState.value.editingMessage
        if (editing != null) {
            commitEditedMessage(editing, rawPrompt)
            return
        }

        val attachment = _uiState.value.pendingAttachment
        val trimmedPrompt = rawPrompt.trim()
        if (trimmedPrompt.isEmpty() && attachment == null) return

        val modelId = GeminiModels.normalizeFreeTierModel(_uiState.value.selectedModelId)
        val useWebSearch = _uiState.value.webSearchEnabled
        val wantsImage = _uiState.value.imageGenMode ||
            (!_uiState.value.pptGenMode && aiService.isExplicitImageRequest(trimmedPrompt))
        val wantsPpt = !wantsImage &&
            (_uiState.value.pptGenMode || aiService.isExplicitPptRequest(trimmedPrompt))

        _uiState.update {
            it.copy(
                pendingAttachment = null,
                errorBanner = null,
                isGenerating = true,
                streamingText = "",
                streamingStatusNotice = when {
                    wantsImage -> "Generating image with Gemini (may take 30–60s)..."
                    wantsPpt -> "Designing slides & building .pptx deck..."
                    else -> null
                }
            )
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            var convId = _uiState.value.activeConversationId
            val historySnapshot = activeMessages.value

            try {
                if (convId == null) {
                    val smartTitle = aiService.buildSmartLocalTitle(
                        trimmedPrompt.ifBlank { attachment?.displayName ?: "New Conversation" }
                    )
                    convId = repository.createConversation(
                        title = smartTitle,
                        modelId = modelId,
                        lastMessagePreview = trimmedPrompt.ifBlank { attachment?.displayName.orEmpty() }
                    )
                    _uiState.update { it.copy(activeConversationId = convId) }
                }

                val attachmentType = when (attachment) {
                    is PendingAttachment.ImageAttachment -> "image"
                    is PendingAttachment.DocumentAttachment ->
                        if (attachment.mimeType == "application/pdf") "pdf" else "document"
                    null -> "none"
                }

                val userOrder = System.currentTimeMillis()
                repository.addMessage(
                    conversationId = convId,
                    role = "user",
                    text = trimmedPrompt.ifBlank {
                        when (attachment) {
                            is PendingAttachment.ImageAttachment -> "Analyze attached image: ${attachment.displayName}"
                            is PendingAttachment.DocumentAttachment -> "Analyze attached file: ${attachment.displayName}"
                            null -> ""
                        }
                    },
                    attachmentType = attachmentType,
                    attachmentName = attachment?.displayName,
                    clientOrder = userOrder
                )

                if (wantsImage) {
                    val inputBitmap = (attachment as? PendingAttachment.ImageAttachment)?.bitmap
                    val generatedBitmap = aiService.generateImage(
                        prompt = trimmedPrompt.removePrefix("/image ").removePrefix("/img "),
                        inputBitmap = inputBitmap
                    )
                    val savedFileName = AttachmentHelper.saveGeneratedImage(appContext, generatedBitmap)
                    val caption = if (inputBitmap != null) {
                        "Edited image for: \"$trimmedPrompt\""
                    } else {
                        "Generated image for: \"$trimmedPrompt\""
                    }
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = caption,
                        generatedImageFile = savedFileName,
                        modelUsed = GeminiModels.IMAGE_GEN,
                        clientOrder = userOrder + 1
                    )
                    repository.updateConversation(
                        conversationId = convId,
                        lastMessagePreview = caption.take(280),
                        messageCount = (historySnapshot.size + 2).toLong()
                    )
                    return@launch
                }

                if (wantsPpt) {
                    val deckPrompt = trimmedPrompt.removePrefix("/ppt ").removePrefix("/slides ")
                    val deck = aiService.generatePresentationDeck(
                        topicPrompt = deckPrompt.ifBlank { "Summary Presentation" },
                        attachment = attachment,
                        historyMessages = historySnapshot,
                        modelId = modelId,
                        webSearchEnabled = useWebSearch
                    )
                    val pptFile = PptxGenerator.createPptxFile(appContext, deck)
                    val pptMarkdown = formatDeckReplyMarkdown(deck, pptFile.name)
                    repository.addMessage(
                        conversationId = convId,
                        role = "model",
                        text = pptMarkdown,
                        modelUsed = modelId,
                        clientOrder = userOrder + 1
                    )
                    repository.updateConversation(
                        conversationId = convId,
                        lastMessagePreview = "PowerPoint: ${deck.deckTitle} (${1 + deck.slides.size} slides)".take(280),
                        messageCount = (historySnapshot.size + 2).toLong()
                    )
                    return@launch
                }

                var finalReply = ""
                var latestLinks: List<Pair<String, String>> = emptyList()
                var actualModelUsed = modelId

                aiService.streamChatReply(
                    modelId = modelId,
                    historyMessages = historySnapshot,
                    userPrompt = trimmedPrompt,
                    attachment = attachment,
                    memories = memories.value,
                    customInstructions = userProfile.value?.systemInstructions,
                    webSearchEnabled = useWebSearch
                ).collect { event ->
                    if (event.accumulatedText.isNotEmpty()) {
                        finalReply = event.accumulatedText
                    }
                    if (event.groundingLinks.isNotEmpty()) {
                        latestLinks = event.groundingLinks
                    }
                    actualModelUsed = event.activeModelUsed
                    _uiState.update {
                        it.copy(
                            streamingText = finalReply,
                            streamingStatusNotice = event.statusNotice
                        )
                    }
                }

                if (latestLinks.isNotEmpty()) {
                    val sourcesMd = buildString {
                        append("\n\n---\n**Sources:**\n")
                        latestLinks.take(5).forEach { (title, url) ->
                            append("- [$title]($url)\n")
                        }
                    }
                    finalReply += sourcesMd
                }

                if (finalReply.isBlank()) {
                    throw IllegalStateException("Gemini returned an empty response.")
                }

                repository.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = finalReply,
                    modelUsed = actualModelUsed,
                    clientOrder = userOrder + 1
                )
                repository.updateConversation(
                    conversationId = convId,
                    lastMessagePreview = finalReply.replace("\n", " ").take(280),
                    messageCount = (historySnapshot.size + 2).toLong()
                )

                if (userProfile.value?.memoryPermissionGranted == true) {
                    launch {
                        extractAndStoreMemories(trimmedPrompt, convId)
                    }
                }

                val shouldSpeak = spokenInitiated || (userProfile.value?.autoSpeakResponses == true)
                if (shouldSpeak) {
                    speakText(finalReply)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                val msg = "Request timed out after 60 seconds. Please check your connection and try again."
                persistErrorReply(convId, msg)
            } catch (e: CancellationException) {
                val partial = _uiState.value.streamingText
                if (partial.isNotBlank() && convId != null) {
                    viewModelScope.launch {
                        try {
                            repository.addMessage(
                                conversationId = convId,
                                role = "model",
                                text = "$partial\n\n*(Stopped)*",
                                modelUsed = modelId,
                                clientOrder = System.currentTimeMillis()
                            )
                        } catch (_: Exception) {
                        }
                    }
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI generation failed", e)
                persistErrorReply(convId, buildFriendlyAiError(e, isImageGen = wantsImage))
            } finally {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        streamingStatusNotice = null
                    )
                }
            }
        }
    }

    private suspend fun persistErrorReply(convId: String?, errorMessage: String) {
        _uiState.update { it.copy(errorBanner = errorMessage) }
        if (convId != null) {
            try {
                repository.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = errorMessage,
                    isError = true,
                    clientOrder = System.currentTimeMillis()
                )
            } catch (_: Exception) {
            }
        }
    }

    fun stopGenerating() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        _uiState.update {
            it.copy(isGenerating = false)
        }
    }

    fun regenerateResponse(targetModelMessage: ChatMessage) {
        val convId = _uiState.value.activeConversationId ?: return
        val msgs = activeMessages.value
        val idx = msgs.indexOfFirst { it.id == targetModelMessage.id }
        if (idx <= 0) return

        val precedingUserMsg = msgs.subList(0, idx).lastOrNull { it.role == "user" } ?: return
        val historyBeforeUser = msgs.subList(0, msgs.indexOf(precedingUserMsg))

        viewModelScope.launch {
            try {
                repository.deleteMessage(convId, targetModelMessage.id)
            } catch (_: Exception) {
            }
        }

        val modelId = GeminiModels.normalizeFreeTierModel(_uiState.value.selectedModelId)
        val useWebSearch = _uiState.value.webSearchEnabled

        _uiState.update {
            it.copy(isGenerating = true, streamingText = "", errorBanner = null)
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            try {
                var finalReply = ""
                var latestLinks: List<Pair<String, String>> = emptyList()
                var actualModelUsed = modelId
                aiService.streamChatReply(
                    modelId = modelId,
                    historyMessages = historyBeforeUser,
                    userPrompt = precedingUserMsg.text,
                    attachment = null,
                    memories = memories.value,
                    customInstructions = userProfile.value?.systemInstructions,
                    webSearchEnabled = useWebSearch
                ).collect { event ->
                    if (event.accumulatedText.isNotEmpty()) {
                        finalReply = event.accumulatedText
                    }
                    if (event.groundingLinks.isNotEmpty()) {
                        latestLinks = event.groundingLinks
                    }
                    actualModelUsed = event.activeModelUsed
                    _uiState.update {
                        it.copy(
                            streamingText = finalReply,
                            streamingStatusNotice = event.statusNotice
                        )
                    }
                }

                if (latestLinks.isNotEmpty()) {
                    val sourcesMd = buildString {
                        append("\n\n---\n**Sources:**\n")
                        latestLinks.take(5).forEach { (title, url) ->
                            append("- [$title]($url)\n")
                        }
                    }
                    finalReply += sourcesMd
                }

                if (finalReply.isBlank()) throw IllegalStateException("Empty regenerated response.")

                repository.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = finalReply,
                    modelUsed = actualModelUsed,
                    clientOrder = precedingUserMsg.clientOrder + 1
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                persistErrorReply(convId, buildFriendlyAiError(e))
            } finally {
                _uiState.update { it.copy(isGenerating = false, streamingText = "", streamingStatusNotice = null) }
            }
        }
    }

    private fun commitEditedMessage(originalMessage: ChatMessage, newText: String) {
        val convId = _uiState.value.activeConversationId ?: return
        val cleanNewText = newText.trim()
        if (cleanNewText.isBlank()) return

        _uiState.update {
            it.copy(
                editingMessage = null,
                isGenerating = true,
                streamingText = "",
                errorBanner = null
            )
        }

        val historyBeforeEdit = activeMessages.value.filter { it.clientOrder < originalMessage.clientOrder }
        val modelId = GeminiModels.normalizeFreeTierModel(_uiState.value.selectedModelId)
        val useWebSearch = _uiState.value.webSearchEnabled

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            try {
                repository.updateMessage(
                    conversationId = convId,
                    messageId = originalMessage.id,
                    text = cleanNewText
                )
                repository.deleteMessagesAfter(convId, originalMessage.clientOrder)

                var finalReply = ""
                var latestLinks: List<Pair<String, String>> = emptyList()
                var actualModelUsed = modelId
                aiService.streamChatReply(
                    modelId = modelId,
                    historyMessages = historyBeforeEdit,
                    userPrompt = cleanNewText,
                    attachment = null,
                    memories = memories.value,
                    customInstructions = userProfile.value?.systemInstructions,
                    webSearchEnabled = useWebSearch
                ).collect { event ->
                    if (event.accumulatedText.isNotEmpty()) {
                        finalReply = event.accumulatedText
                    }
                    if (event.groundingLinks.isNotEmpty()) {
                        latestLinks = event.groundingLinks
                    }
                    actualModelUsed = event.activeModelUsed
                    _uiState.update {
                        it.copy(
                            streamingText = finalReply,
                            streamingStatusNotice = event.statusNotice
                        )
                    }
                }

                if (latestLinks.isNotEmpty()) {
                    finalReply += buildString {
                        append("\n\n---\n**Sources:**\n")
                        latestLinks.take(5).forEach { (title, url) -> append("- [$title]($url)\n") }
                    }
                }

                repository.addMessage(
                    conversationId = convId,
                    role = "model",
                    text = finalReply.ifBlank { "No response received." },
                    modelUsed = actualModelUsed,
                    clientOrder = originalMessage.clientOrder + 1
                )
                repository.updateConversation(
                    conversationId = convId,
                    lastMessagePreview = finalReply.replace("\n", " ").take(280)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Edit message regeneration failed", e)
                persistErrorReply(convId, buildFriendlyAiError(e))
            } finally {
                _uiState.update { it.copy(isGenerating = false, streamingText = "", streamingStatusNotice = null) }
            }
        }
    }

    private suspend fun extractAndStoreMemories(
        userPrompt: String,
        conversationId: String
    ) {
        val extracted = aiService.extractMemoriesLocally(userPrompt, memories.value)
        if (extracted.isNotEmpty()) {
            extracted.forEach { mem ->
                try {
                    repository.addMemory(
                        category = mem.category,
                        content = mem.content,
                        sourceConversationId = conversationId
                    )
                } catch (_: Exception) {
                }
            }
            _uiState.update { it.copy(memoryToast = "Jarvis remembered ${extracted.size} new detail(s)") }
        }
    }

    fun saveMessageToMemory(text: String) {
        val snippet = text.trim().take(400)
        if (snippet.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addMemory(
                    category = "context",
                    content = snippet,
                    sourceConversationId = _uiState.value.activeConversationId
                )
                _uiState.update { it.copy(memoryToast = "Saved to Memory Vault") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to save memory.") }
            }
        }
    }

    // --- CONVERSATION MANAGEMENT ---

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                repository.updateConversation(conversationId = conversationId, title = newTitle)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Rename failed.") }
            }
        }
    }

    fun togglePinConversation(conversationId: String, pinned: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateConversation(conversationId = conversationId, pinned = pinned)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Pin update failed.") }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                if (_uiState.value.activeConversationId == conversationId) {
                    _uiState.update { it.copy(activeConversationId = null, streamingText = "") }
                }
                repository.deleteConversation(conversationId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Delete failed.") }
            }
        }
    }

    // --- MEMORY MANAGEMENT ---

    fun toggleMemoryPermission(granted: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(memoryPermissionGranted = granted)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to update permission.") }
            }
        }
    }

    fun addMemory(category: String, content: String) {
        viewModelScope.launch {
            try {
                repository.addMemory(category = category, content = content)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to save memory.") }
            }
        }
    }

    fun updateMemory(memoryId: String, category: String, content: String) {
        viewModelScope.launch {
            try {
                repository.updateMemory(memoryId = memoryId, category = category, content = content)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to update memory.") }
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMemory(memoryId)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to delete memory.") }
            }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            try {
                repository.clearAllMemories()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to clear memories.") }
            }
        }
    }

    // --- PROFILE & SETTINGS ---

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(displayName = name)
            } catch (_: Exception) {
            }
        }
    }

    fun updateDefaultModel(modelId: String) {
        val safeModel = GeminiModels.normalizeFreeTierModel(modelId)
        _uiState.update { it.copy(selectedModelId = safeModel) }
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(preferredModel = safeModel)
            } catch (_: Exception) {
            }
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(themeMode = mode)
            } catch (_: Exception) {
            }
        }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(autoSpeakResponses = enabled)
            } catch (_: Exception) {
            }
        }
    }

    fun updateWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(wakeWordEnabled = enabled)
            } catch (_: Exception) {
            }
        }
        if (_uiState.value.isVoiceOverlayOpen) {
            if (enabled) {
                voiceManager.startWakeWordStandby { cmd ->
                    sendMessage(cmd, spokenInitiated = true)
                }
            } else {
                voiceManager.stopListening()
            }
        }
    }

    fun updateSpeechRate(rate: Double) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(speechRate = rate)
            } catch (_: Exception) {
            }
        }
    }

    fun updateSelectedVoice(voiceName: String?) {
        viewModelScope.launch {
            try {
                if (voiceName == null) {
                    repository.updateProfileSettings(clearSelectedVoice = true)
                } else {
                    repository.updateProfileSettings(selectedVoiceName = voiceName)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateSystemInstructions(instructions: String) {
        viewModelScope.launch {
            try {
                repository.updateProfileSettings(systemInstructions = instructions)
            } catch (_: Exception) {
            }
        }
    }

    // --- VOICE ASSISTANT ---

    fun startSingleDictation(onTranscript: (String) -> Unit) {
        voiceManager.startSingleCapture(onTranscript)
    }

    fun openVoiceOverlay() {
        _uiState.update { it.copy(isVoiceOverlayOpen = true) }
        if (userProfile.value?.wakeWordEnabled == true) {
            voiceManager.startWakeWordStandby { cmd ->
                sendMessage(cmd, spokenInitiated = true)
            }
        } else {
            startVoiceOverlayCapture()
        }
    }

    fun startVoiceOverlayCapture() {
        voiceManager.startSingleCapture { transcript ->
            if (transcript.isNotBlank()) {
                sendMessage(transcript, spokenInitiated = true)
            }
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun closeVoiceOverlay() {
        voiceManager.stopListening()
        voiceManager.stopSpeaking()
        _uiState.update { it.copy(isVoiceOverlayOpen = false) }
    }

    fun speakText(text: String) {
        val profile = userProfile.value
        val rate = (profile?.speechRate ?: 1.0).toFloat()
        val voiceName = profile?.selectedVoiceName?.takeIf { it.isNotBlank() }
        voiceManager.speak(
            markdownText = text,
            speechRate = rate,
            selectedVoiceName = voiceName,
            onComplete = {
                if (_uiState.value.isVoiceOverlayOpen && userProfile.value?.wakeWordEnabled == true) {
                    voiceManager.startWakeWordStandby { cmd ->
                        sendMessage(cmd, spokenInitiated = true)
                    }
                }
            }
        )
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun testVoice() {
        speakText("Hello! I am Jarvis, your personal AI assistant. How can I help you today?")
    }

    fun dismissErrorBanner() {
        _uiState.update { it.copy(errorBanner = null) }
    }

    fun consumeMemoryToast() {
        _uiState.update { it.copy(memoryToast = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeGenerationJob?.cancel()
        voiceManager.release()
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private fun formatDeckReplyMarkdown(deck: GeneratedDeck, pptFileName: String): String {
        val totalSlides = 1 + deck.slides.size
        return buildString {
            appendLine("## ${deck.deckTitle}")
            if (deck.subtitle.isNotBlank()) {
                appendLine("*${deck.subtitle}*")
            }
            appendLine()
            appendLine("**Slide 1 (Cover):** ${deck.deckTitle}")
            deck.slides.forEachIndexed { index, slide ->
                appendLine()
                appendLine("### Slide ${index + 2}: ${slide.title}")
                slide.bullets.forEach { bullet ->
                    appendLine("- $bullet")
                }
                if (!slide.keyTakeaway.isNullOrBlank()) {
                    appendLine("> **Key Takeaway:** ${slide.keyTakeaway}")
                }
                if (!slide.speakerNotes.isNullOrBlank()) {
                    appendLine("*Speaker Notes:* ${slide.speakerNotes}")
                }
            }
            append("\n\n[PPTX Ready ($totalSlides slides)](jarvis-pptx://$pptFileName|$totalSlides|${deck.deckTitle.replace("|", "-")})")
        }
    }

    private fun buildFriendlyAiError(e: Exception, isImageGen: Boolean = false): String {
        val raw = e.localizedMessage ?: e.message ?: "Unknown AI error"
        val lower = raw.lowercase()
        return when {
            isImageGen && (
                lower.contains("billing") ||
                    lower.contains("tier") ||
                    lower.contains("quota") ||
                    lower.contains("429") ||
                    lower.contains("permission") ||
                    lower.contains("not enabled") ||
                    lower.contains("not found")
                ) ->
                "Image generation (${GeminiModels.IMAGE_GEN}) requires Firebase Blaze (pay-as-you-go) billing on the backend project ($raw). Text chat, live web search, and PowerPoint (.pptx) creation remain free to use."
            lower.contains("403") || lower.contains("appcheck") ->
                "App Check attestation failed (HTTP 403): $raw"
            e is QuotaExceededException || lower.contains("quota") || lower.contains("429") ->
                "Google's free-tier rate limit was temporarily reached ($raw). Please wait a moment or switch to Gemini 3.5 Flash-Lite."
            else -> raw
        }
    }

    companion object {
        private const val TAG = "JarvisViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = JarvisRepository(db, Firebase.auth)
                JarvisViewModel(
                    appContext = app,
                    repository = repo,
                    aiService = JarvisAiService(),
                    voiceManager = JarvisVoiceManager(app),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
