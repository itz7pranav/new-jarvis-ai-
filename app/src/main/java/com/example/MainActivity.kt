package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.ui.JarvisViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.chat.ChatWorkspaceRoute
import com.example.ui.memory.MemoryScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.JarvisTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val ROUTE_CHAT = "chat"
private const val ROUTE_MEMORY = "memory"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            JarvisAppRoot()
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun JarvisAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        JarvisTheme(themeMode = "dark") {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            AuthenticatedJarvisShell(currentUserId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedJarvisShell(currentUserId: String) {
    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = JarvisViewModel.provideFactory(currentUserId)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()

    JarvisTheme(themeMode = userProfile?.themeMode ?: "dark") {
        NavHost(
            navController = navController,
            startDestination = ROUTE_CHAT
        ) {
            composable(ROUTE_CHAT) {
                ChatWorkspaceRoute(
                    uiState = uiState,
                    voiceState = voiceState,
                    userProfile = userProfile,
                    conversations = conversations,
                    messages = messages,
                    memoryCount = memories.size,
                    onNewChat = viewModel::startNewConversation,
                    onSelectConversation = viewModel::selectConversation,
                    onRenameConversation = viewModel::renameConversation,
                    onTogglePinConversation = viewModel::togglePinConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    onSelectModel = viewModel::setSelectedModel,
                    onToggleWebSearch = viewModel::toggleWebSearch,
                    onToggleImageGenMode = viewModel::toggleImageGenMode,
                    onTogglePptGenMode = viewModel::togglePptGenMode,
                    onAttachImage = viewModel::attachImage,
                    onAttachDocument = viewModel::attachDocument,
                    onClearAttachment = viewModel::clearAttachment,
                    onSendMessage = { prompt -> viewModel.sendMessage(prompt) },
                    onStopGenerating = viewModel::stopGenerating,
                    onRegenerateMessage = viewModel::regenerateResponse,
                    onSaveMessageToMemory = viewModel::saveMessageToMemory,
                    onBeginEditMessage = viewModel::beginEditMessage,
                    onCancelEditMessage = viewModel::cancelEditMessage,
                    onSpeakText = viewModel::speakText,
                    onStopSpeaking = viewModel::stopSpeaking,
                    onStartComposerDictation = viewModel::startSingleDictation,
                    onOpenVoiceOverlay = viewModel::openVoiceOverlay,
                    onCloseVoiceOverlay = viewModel::closeVoiceOverlay,
                    onStartVoiceOverlayCapture = viewModel::startVoiceOverlayCapture,
                    onStopVoiceListening = viewModel::stopVoiceListening,
                    onToggleWakeWord = viewModel::updateWakeWordEnabled,
                    onOpenMemoryVault = { navController.navigate(ROUTE_MEMORY) },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onDismissError = viewModel::dismissErrorBanner,
                    onConsumeMemoryToast = viewModel::consumeMemoryToast
                )
            }

            composable(ROUTE_MEMORY) {
                MemoryScreen(
                    memories = memories,
                    memoryPermissionGranted = userProfile?.memoryPermissionGranted ?: true,
                    onToggleMemoryPermission = viewModel::toggleMemoryPermission,
                    onAddMemory = viewModel::addMemory,
                    onUpdateMemory = viewModel::updateMemory,
                    onDeleteMemory = viewModel::deleteMemory,
                    onClearAllMemories = viewModel::clearAllMemories,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    userProfile = userProfile,
                    availableVoices = voiceState.availableVoices,
                    onUpdateDisplayName = viewModel::updateDisplayName,
                    onSelectModel = viewModel::updateDefaultModel,
                    onSelectTheme = viewModel::updateThemeMode,
                    onToggleAutoSpeak = viewModel::updateAutoSpeak,
                    onToggleWakeWord = viewModel::updateWakeWordEnabled,
                    onUpdateSpeechRate = viewModel::updateSpeechRate,
                    onSelectVoice = viewModel::updateSelectedVoice,
                    onTestVoice = viewModel::testVoice,
                    onUpdateSystemInstructions = viewModel::updateSystemInstructions,
                    onSignOut = {
                        signOutUser(credentialManager = credentialManager, scope = scope)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
