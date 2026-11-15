package com.example.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AttachmentHelper
import com.example.core.PendingAttachment
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.GeminiModels
import com.example.data.model.UserProfile
import com.example.ui.ChatScreenUiState
import com.example.ui.components.MarkdownText
import com.example.ui.theme.LocalJarvisColors
import com.example.ui.voice.VoiceAssistantOverlay
import com.example.voice.VoiceUiState
import kotlinx.coroutines.launch

@Composable
fun ChatWorkspaceRoute(
    uiState: ChatScreenUiState,
    voiceState: VoiceUiState,
    userProfile: UserProfile?,
    conversations: List<Conversation>,
    messages: List<ChatMessage>,
    memoryCount: Int,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onTogglePinConversation: (String, Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleImageGenMode: () -> Unit,
    onTogglePptGenMode: () -> Unit,
    onSaveMessageToMemory: (String) -> Unit,
    onAttachImage: (android.net.Uri) -> Unit,
    onAttachDocument: (android.net.Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onBeginEditMessage: (ChatMessage) -> Unit,
    onCancelEditMessage: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartComposerDictation: ((String) -> Unit) -> Unit,
    onOpenVoiceOverlay: () -> Unit,
    onCloseVoiceOverlay: () -> Unit,
    onStartVoiceOverlayCapture: () -> Unit,
    onStopVoiceListening: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onOpenMemoryVault: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    onConsumeMemoryToast: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            ChatDrawerContent(
                conversations = conversations,
                activeConversationId = uiState.activeConversationId,
                memoryCount = memoryCount,
                userProfile = userProfile,
                onNewChat = {
                    onNewChat()
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    onSelectConversation(id)
                    if (!isWideScreen) scope.launch { drawerState.close() }
                },
                onRenameConversation = onRenameConversation,
                onTogglePinConversation = onTogglePinConversation,
                onDeleteConversation = onDeleteConversation,
                onOpenMemoryVault = {
                    if (!isWideScreen) scope.launch { drawerState.close() }
                    onOpenMemoryVault()
                },
                onOpenSettings = {
                    if (!isWideScreen) scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        drawerContent()
                    }
                }
            ) {
                ChatScaffoldContent(
                    isWideScreen = true,
                    onOpenDrawer = {},
                    uiState = uiState,
                    voiceState = voiceState,
                    userProfile = userProfile,
                    messages = messages,
                    onNewChat = onNewChat,
                    onSelectModel = onSelectModel,
                    onToggleWebSearch = onToggleWebSearch,
                    onToggleImageGenMode = onToggleImageGenMode,
                    onTogglePptGenMode = onTogglePptGenMode,
                    onSaveMessageToMemory = onSaveMessageToMemory,
                    onAttachImage = onAttachImage,
                    onAttachDocument = onAttachDocument,
                    onClearAttachment = onClearAttachment,
                    onSendMessage = onSendMessage,
                    onStopGenerating = onStopGenerating,
                    onRegenerateMessage = onRegenerateMessage,
                    onBeginEditMessage = onBeginEditMessage,
                    onCancelEditMessage = onCancelEditMessage,
                    onSpeakText = onSpeakText,
                    onStopSpeaking = onStopSpeaking,
                    onStartComposerDictation = onStartComposerDictation,
                    onOpenVoiceOverlay = onOpenVoiceOverlay,
                    onDismissError = onDismissError,
                    onConsumeMemoryToast = onConsumeMemoryToast
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        drawerContent()
                    }
                }
            ) {
                ChatScaffoldContent(
                    isWideScreen = false,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    uiState = uiState,
                    voiceState = voiceState,
                    userProfile = userProfile,
                    messages = messages,
                    onNewChat = onNewChat,
                    onSelectModel = onSelectModel,
                    onToggleWebSearch = onToggleWebSearch,
                    onToggleImageGenMode = onToggleImageGenMode,
                    onTogglePptGenMode = onTogglePptGenMode,
                    onSaveMessageToMemory = onSaveMessageToMemory,
                    onAttachImage = onAttachImage,
                    onAttachDocument = onAttachDocument,
                    onClearAttachment = onClearAttachment,
                    onSendMessage = onSendMessage,
                    onStopGenerating = onStopGenerating,
                    onRegenerateMessage = onRegenerateMessage,
                    onBeginEditMessage = onBeginEditMessage,
                    onCancelEditMessage = onCancelEditMessage,
                    onSpeakText = onSpeakText,
                    onStopSpeaking = onStopSpeaking,
                    onStartComposerDictation = onStartComposerDictation,
                    onOpenVoiceOverlay = onOpenVoiceOverlay,
                    onDismissError = onDismissError,
                    onConsumeMemoryToast = onConsumeMemoryToast
                )
            }
        }

        AnimatedVisibility(visible = uiState.isVoiceOverlayOpen) {
            val lastModelReply = remember(messages) {
                messages.lastOrNull { it.role == "model" && !it.isError }?.text
            }
            VoiceAssistantOverlay(
                voiceState = voiceState,
                isThinking = uiState.isGenerating,
                lastResponsePreview = lastModelReply,
                wakeWordEnabled = userProfile?.wakeWordEnabled ?: false,
                onToggleWakeWord = onToggleWakeWord,
                onStartListening = onStartVoiceOverlayCapture,
                onStopListening = onStopVoiceListening,
                onInterruptSpeaking = onStopSpeaking,
                onClose = onCloseVoiceOverlay
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScaffoldContent(
    isWideScreen: Boolean,
    onOpenDrawer: () -> Unit,
    uiState: ChatScreenUiState,
    voiceState: VoiceUiState,
    userProfile: UserProfile?,
    messages: List<ChatMessage>,
    onNewChat: () -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleImageGenMode: () -> Unit,
    onTogglePptGenMode: () -> Unit,
    onSaveMessageToMemory: (String) -> Unit,
    onAttachImage: (android.net.Uri) -> Unit,
    onAttachDocument: (android.net.Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onRegenerateMessage: (ChatMessage) -> Unit,
    onBeginEditMessage: (ChatMessage) -> Unit,
    onCancelEditMessage: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onStartComposerDictation: ((String) -> Unit) -> Unit,
    onOpenVoiceOverlay: () -> Unit,
    onDismissError: () -> Unit,
    onConsumeMemoryToast: () -> Unit
) {
    val context = LocalContext.current
    val jarvisColors = LocalJarvisColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Pre-fill input when editing a previous message
    LaunchedEffect(uiState.editingMessage?.id) {
        val editing = uiState.editingMessage
        if (editing != null) {
            inputText = editing.text
        }
    }

    LaunchedEffect(uiState.memoryToast) {
        val toastMsg = uiState.memoryToast
        if (!toastMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(toastMsg)
            onConsumeMemoryToast()
        }
    }

    LaunchedEffect(messages.size, uiState.streamingText.length, uiState.isGenerating) {
        val totalItems = messages.size + (if (uiState.isGenerating) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAttachImage(uri)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onAttachDocument(uri)
    }

    var pendingExportSource by remember { mutableStateOf<java.io.File?>(null) }
    val savePngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destUri ->
        val src = pendingExportSource
        pendingExportSource = null
        if (destUri != null && src != null) {
            scope.launch {
                runCatching { AttachmentHelper.copyFileToUri(context, src, destUri) }
                    .onSuccess { Toast.makeText(context, "Image saved!", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val savePptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.presentationml.presentation")
    ) { destUri ->
        val src = pendingExportSource
        pendingExportSource = null
        if (destUri != null && src != null) {
            scope.launch {
                runCatching { AttachmentHelper.copyFileToUri(context, src, destUri) }
                    .onSuccess { Toast.makeText(context, "Presentation (.pptx) saved!", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice input.",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingMicAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!isWideScreen) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open conversations")
                        }
                    }
                },
                title = {
                    Box {
                        val activeModel = GeminiModels.allChatModels
                            .firstOrNull { it.id == uiState.selectedModelId }
                            ?: GeminiModels.allChatModels.first()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                            modifier = Modifier
                                .clickable { showModelMenu = true }
                                .testTag("model_selector_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = activeModel.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = "Switch Gemini model",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            GeminiModels.allChatModels.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = option.displayName,
                                                fontWeight = if (option.id == uiState.selectedModelId) {
                                                    FontWeight.Bold
                                                } else FontWeight.Normal
                                            )
                                            Text(
                                                text = option.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectModel(option.id)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (voiceState.isSpeaking) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier.testTag("top_stop_speaking_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.StopCircle,
                                contentDescription = "Stop Jarvis speaking",
                                tint = jarvisColors.accentAmber
                            )
                        }
                    }
                    IconButton(
                        onClick = { runWithMicPermission { onOpenVoiceOverlay() } },
                        modifier = Modifier.testTag("open_voice_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Open Jarvis Voice Mode",
                            tint = jarvisColors.accentCyan
                        )
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("top_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddComment,
                            contentDescription = "New Conversation"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!uiState.errorBanner.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.errorBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Message Feed or Welcome Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !uiState.isGenerating) {
                    EmptyChatWelcome(
                        userName = userProfile?.displayName ?: "there",
                        onPromptSelected = { prompt ->
                            onSendMessage(prompt)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            if (msg.role == "user") {
                                UserMessageBubble(
                                    message = msg,
                                    onEdit = { onBeginEditMessage(msg) }
                                )
                            } else {
                                AssistantMessageBubble(
                                    message = msg,
                                    isSpeaking = voiceState.isSpeaking,
                                    onCopy = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Jarvis reply", msg.text))
                                        Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                                    },
                                    onSpeak = { onSpeakText(msg.text) },
                                    onStopSpeaking = onStopSpeaking,
                                    onRegenerate = { onRegenerateMessage(msg) },
                                    onSaveToMemory = { onSaveMessageToMemory(msg.text) },
                                    onExportImage = { file ->
                                        pendingExportSource = file
                                        savePngLauncher.launch(file.name)
                                    },
                                    onExportPpt = { file ->
                                        pendingExportSource = file
                                        savePptLauncher.launch(file.name)
                                    }
                                )
                            }
                        }

                        if (uiState.isGenerating) {
                            item(key = "streaming_active_item") {
                                StreamingAssistantCard(
                                    streamingText = uiState.streamingText,
                                    statusNotice = uiState.streamingStatusNotice,
                                    onStopGenerating = onStopGenerating
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Composer Area
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Editing banner
                if (uiState.editingMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Editing message — sending will regenerate reply",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(
                                onClick = {
                                    onCancelEditMessage()
                                    inputText = ""
                                },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                // Pending attachment preview
                if (uiState.pendingAttachment != null) {
                    AttachmentPreviewChip(
                        attachment = uiState.pendingAttachment,
                        onClear = onClearAttachment
                    )
                }

                // Capability Pills (Web Search + Free-tier badge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = uiState.webSearchEnabled,
                        onClick = onToggleWebSearch,
                        label = {
                            Text(if (uiState.webSearchEnabled) "Live Web Search" else "Web Search Off")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("web_search_toggle_chip")
                    )

                    FilterChip(
                        selected = uiState.imageGenMode,
                        onClick = onToggleImageGenMode,
                        label = { Text("Create Image") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("image_gen_toggle_chip")
                    )

                    FilterChip(
                        selected = uiState.pptGenMode,
                        onClick = onTogglePptGenMode,
                        label = { Text("Create PPT") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Slideshow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("ppt_gen_toggle_chip")
                    )

                    if (voiceState.isListening) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = jarvisColors.accentCyan.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, jarvisColors.accentCyan)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = jarvisColors.accentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = voiceState.partialTranscript.ifBlank { "Listening..." },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = jarvisColors.accentCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Composer Input Box
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = true },
                                modifier = Modifier.testTag("attach_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Attach image or PDF document"
                                )
                            }
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Upload Photo / Screenshot") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Image, contentDescription = null)
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Upload PDF / Document") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Description, contentDescription = null)
                                    },
                                    onClick = {
                                        showAttachMenu = false
                                        documentPickerLauncher.launch(
                                            arrayOf("application/pdf", "text/*")
                                        )
                                    }
                                )
                            }
                        }

                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = when {
                                        uiState.imageGenMode -> "Describe an image to generate or edit..."
                                        uiState.pptGenMode -> "Describe your PowerPoint topic & slides..."
                                        else -> "Message Jarvis..."
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        IconButton(
                            onClick = {
                                runWithMicPermission {
                                    onStartComposerDictation { transcript ->
                                        inputText = if (inputText.isBlank()) transcript else "$inputText $transcript"
                                    }
                                }
                            },
                            modifier = Modifier.testTag("composer_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Voice dictation",
                                tint = if (voiceState.isListening) jarvisColors.accentCyan
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.isGenerating) {
                            FilledIconButton(
                                onClick = onStopGenerating,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(44.dp)
                                    .testTag("stop_generating_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Stop generating"
                                )
                            }
                        } else {
                            val canSend = inputText.isNotBlank() || uiState.pendingAttachment != null
                            FilledIconButton(
                                onClick = {
                                    val toSend = inputText
                                    inputText = ""
                                    onSendMessage(toSend)
                                },
                                enabled = canSend,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(44.dp)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatWelcome(
    userName: String,
    onPromptSelected: (String) -> Unit
) {
    val jarvisColors = LocalJarvisColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            border = BorderStroke(1.5.dp, jarvisColors.accentCyan.copy(alpha = 0.55f)),
            modifier = Modifier.size(74.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_core_avatar),
                contentDescription = "Jarvis Core",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hello, $userName",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ask anything, search the live web, generate images, build PowerPoint decks, or speak hands-free.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val starters = listOf(
            "Search the live web for today's top AI & technology headlines.",
            "Create a 6-slide PowerPoint presentation on Renewable Energy.",
            "Generate an image of a glowing holographic AI command center.",
            "Write a clean Kotlin coroutine retry helper with exponential backoff."
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            starters.forEach { prompt ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPromptSelected(prompt) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = jarvisColors.accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    onEdit: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = jarvisColors.userBubbleContainer,
            border = BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = 0.25f)),
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (message.attachmentType != "none" && !message.attachmentName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (message.attachmentType == "image") {
                                    Icons.Outlined.Image
                                } else Icons.Outlined.Description,
                                contentDescription = null,
                                tint = jarvisColors.accentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = message.attachmentName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = jarvisColors.userBubbleText
                )
            }
        }

        TextButton(
            onClick = onEdit,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit message",
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRegenerate: () -> Unit,
    onSaveToMemory: () -> Unit,
    onExportImage: (java.io.File) -> Unit,
    onExportPpt: (java.io.File) -> Unit
) {
    val context = LocalContext.current
    val jarvisColors = LocalJarvisColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_jarvis_core_avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
            )
            Text(
                text = "JARVIS",
                style = MaterialTheme.typography.labelLarge,
                color = jarvisColors.accentCyan
            )
            if (!message.modelUsed.isNullOrBlank()) {
                Text(
                    text = "• ${message.modelUsed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                jarvisColors.assistantCardContainer
            },
            border = BorderStroke(
                1.dp,
                if (message.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else jarvisColors.subtleBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!message.generatedImageFile.isNullOrBlank()) {
                    val imgFile = remember(message.generatedImageFile) {
                        AttachmentHelper.getGeneratedImageFile(context, message.generatedImageFile)
                    }
                    if (imgFile != null) {
                        AsyncImage(
                            model = imgFile,
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onExportImage(imgFile) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Image", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { shareGeneratedFile(context, imgFile, "image/png", "Share Generated Image") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                val parsedPpt = remember(message.text) { splitBodyAndPpt(message.text) }
                val splitSources = remember(parsedPpt.bodyWithoutPpt) { splitBodyAndSources(parsedPpt.bodyWithoutPpt) }

                MarkdownText(
                    markdown = splitSources.first,
                    textColor = if (message.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                val pptAttachment = parsedPpt.pptInfo
                if (pptAttachment != null) {
                    val pptFile = remember(pptAttachment.fileName) {
                        AttachmentHelper.getGeneratedPptFile(context, pptAttachment.fileName)
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = jarvisColors.accentAmber.copy(alpha = 0.18f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Slideshow,
                                            contentDescription = null,
                                            tint = jarvisColors.accentAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pptAttachment.title.ifBlank { pptAttachment.fileName },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeKb = pptFile?.let { "${(it.length() / 1024).coerceAtLeast(1)} KB" } ?: "Deck ready"
                                    Text(
                                        text = "${pptAttachment.slideCount} slides • PowerPoint (.pptx) • $sizeKb",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (pptFile != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { onExportPpt(pptFile) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save .pptx", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            shareGeneratedFile(
                                                context,
                                                pptFile,
                                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                                "Share PowerPoint (.pptx)"
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share / Open", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                if (splitSources.second.isNotEmpty()) {
                    val uriHandler = LocalUriHandler.current
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "LIVE SOURCES",
                            style = MaterialTheme.typography.labelSmall,
                            color = jarvisColors.accentCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            splitSources.second.forEach { (title, url) ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable {
                                        runCatching { uriHandler.openUri(url) }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Public,
                                            contentDescription = null,
                                            tint = jarvisColors.accentCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCopy,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy response",
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelMedium)
            }

            if (!message.isError) {
                TextButton(
                    onClick = if (isSpeaking) onStopSpeaking else onSpeak,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop speaking" else "Read aloud",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isSpeaking) "Stop" else "Speak",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onRegenerate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Regenerate response",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regenerate", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = onSaveToMemory,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "Save to Memory Vault",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remember", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantCard(
    streamingText: String,
    statusNotice: String?,
    onStopGenerating: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    val infinite = rememberInfiniteTransition(label = "stream_shimmer")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = jarvisColors.accentCyan
                )
                Text(
                    text = "Jarvis is thinking...",
                    style = MaterialTheme.typography.labelLarge,
                    color = jarvisColors.accentCyan
                )
            }

            OutlinedButton(
                onClick = onStopGenerating,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stop", style = MaterialTheme.typography.labelMedium)
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = jarvisColors.assistantCardContainer,
            border = BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = alpha * 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (streamingText.isBlank()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = statusNotice?.takeIf { it.isNotBlank() } ?: "Connecting to Gemini...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(modifier = Modifier.padding(14.dp)) {
                    MarkdownText(markdown = streamingText)
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewChip(
    attachment: PendingAttachment,
    onClear: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, jarvisColors.accentCyan.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (attachment) {
                is PendingAttachment.ImageAttachment -> {
                    Image(
                        bitmap = attachment.bitmap.asImageBitmap(),
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                is PendingAttachment.DocumentAttachment -> {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = jarvisColors.accentAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = attachment.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private data class PptLinkInfo(
    val fileName: String,
    val slideCount: Int,
    val title: String
)

private data class ParsedPptMessage(
    val bodyWithoutPpt: String,
    val pptInfo: PptLinkInfo?
)

private fun splitBodyAndPpt(fullText: String): ParsedPptMessage {
    val pptRegex = Regex("""\n*\[PPTX Ready \([^\)]+\)\]\(jarvis-pptx://([^)|]+)\|(\d+)\|([^)]*)\)\s*$""")
    val match = pptRegex.find(fullText) ?: return ParsedPptMessage(fullText, null)
    val fileName = match.groupValues[1].trim()
    val slideCount = match.groupValues[2].toIntOrNull() ?: 1
    val title = match.groupValues[3].trim()
    val body = fullText.substring(0, match.range.first).trimEnd()
    return ParsedPptMessage(body, PptLinkInfo(fileName, slideCount, title))
}

private fun shareGeneratedFile(
    context: Context,
    file: java.io.File,
    mimeType: String,
    chooserTitle: String
) {
    runCatching {
        val uri = AttachmentHelper.getShareUri(context, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
    }.onFailure {
        Toast.makeText(context, "Unable to open share sheet: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun splitBodyAndSources(fullText: String): Pair<String, List<Pair<String, String>>> {
    val marker = "\n\n---\n**Sources:**\n"
    val idx = fullText.lastIndexOf(marker)
    if (idx == -1) return fullText to emptyList()

    val body = fullText.substring(0, idx).trimEnd()
    val sourcesSection = fullText.substring(idx + marker.length)
    val linkRegex = Regex("""-\s+\[([^\]]+)\]\((https?://[^)\s]+)\)""")
    val links = linkRegex.findAll(sourcesSection)
        .map { it.groupValues[1].trim() to it.groupValues[2].trim() }
        .toList()
    return if (links.isEmpty()) fullText to emptyList() else body to links
}

