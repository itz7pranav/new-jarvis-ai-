package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.GeminiModels
import com.example.data.model.UserProfile
import com.example.ui.theme.LocalJarvisColors
import com.example.voice.VoiceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    availableVoices: List<VoiceInfo>,
    onUpdateDisplayName: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onUpdateSpeechRate: (Double) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onTestVoice: () -> Unit,
    onUpdateSystemInstructions: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    var displayNameInput by remember(userProfile?.displayName) {
        mutableStateOf(userProfile?.displayName ?: "User")
    }
    var instructionsInput by remember(userProfile?.systemInstructions) {
        mutableStateOf(userProfile?.systemInstructions.orEmpty())
    }
    var speechRateSlider by remember(userProfile?.speechRate) {
        mutableFloatStateOf((userProfile?.speechRate ?: 1.0).toFloat())
    }
    var showVoiceDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Profile", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. User Profile Card
                item {
                    SectionCard(
                        title = "Account Profile",
                        icon = Icons.Outlined.Person
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                if (!userProfile?.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userProfile?.photoUrl,
                                        contentDescription = "User profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile?.displayName ?: "User",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = userProfile?.email ?: "Signed in with Google",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("Preferred Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onUpdateDisplayName(displayNameInput) },
                                enabled = displayNameInput.isNotBlank() &&
                                    displayNameInput.trim() != userProfile?.displayName,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("Save")
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    }
                }

                // 2. Model & Intelligence
                item {
                    SectionCard(
                        title = "AI Intelligence & Persona",
                        icon = Icons.Outlined.AutoAwesome
                    ) {
                        val currentModel = userProfile?.preferredModel ?: GeminiModels.FLASH
                        GeminiModels.allChatModels.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModel(option.id) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentModel == option.id,
                                    onClick = { onSelectModel(option.id) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = option.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = jarvisColors.subtleBorder)

                        OutlinedTextField(
                            value = instructionsInput,
                            onValueChange = { instructionsInput = it },
                            label = { Text("Custom Instructions for Jarvis") },
                            placeholder = { Text("e.g. Always include Kotlin examples, keep answers concise...") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onUpdateSystemInstructions(instructionsInput) },
                            enabled = instructionsInput != userProfile?.systemInstructions.orEmpty(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Instructions")
                        }
                    }
                }

                // 3. Appearance
                item {
                    SectionCard(
                        title = "Appearance",
                        icon = Icons.Outlined.Palette
                    ) {
                        val currentTheme = userProfile?.themeMode ?: "dark"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "dark" to "Obsidian Dark",
                                "light" to "Alabaster Light",
                                "system" to "System"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = currentTheme == key,
                                    onClick = { onSelectTheme(key) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("theme_chip_$key")
                                )
                            }
                        }
                    }
                }

                // 4. Voice Assistant Settings
                item {
                    SectionCard(
                        title = "Voice Assistant",
                        icon = Icons.Outlined.RecordVoiceOver
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-speak AI replies", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Automatically read responses aloud when submitted via voice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userProfile?.autoSpeakResponses ?: false,
                                onCheckedChange = onToggleAutoSpeak
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wake-word \"Jarvis\" in Voice Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Continuously listen for \"Jarvis\" while Voice Mode is open",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userProfile?.wakeWordEnabled ?: false,
                                onCheckedChange = onToggleWakeWord
                            )
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Speed", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${String.format("%.2f", speechRateSlider)}x",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = jarvisColors.accentCyan
                                )
                            }
                            Slider(
                                value = speechRateSlider,
                                onValueChange = { speechRateSlider = it },
                                onValueChangeFinished = { onUpdateSpeechRate(speechRateSlider.toDouble()) },
                                valueRange = 0.6f..1.8f
                            )
                        }

                        Box {
                            val selectedLabel = availableVoices
                                .firstOrNull { it.name == userProfile?.selectedVoiceName }
                                ?.displayLabel
                                ?: "System Default Voice"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showVoiceDropdown = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Voice: $selectedLabel", maxLines = 1)
                                }
                                OutlinedButton(onClick = onTestVoice) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test")
                                }
                            }

                            DropdownMenu(
                                expanded = showVoiceDropdown,
                                onDismissRequest = { showVoiceDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("System Default Voice") },
                                    onClick = {
                                        onSelectVoice(null)
                                        showVoiceDropdown = false
                                    }
                                )
                                availableVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.displayLabel) },
                                        onClick = {
                                            onSelectVoice(voice.name)
                                            showVoiceDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Usage & Capabilities Disclosure
                item {
                    SectionCard(
                        title = "100% Free App Access & Platform Info",
                        icon = Icons.Outlined.Info
                    ) {
                        Text(
                            text = "• Completely free app access: No subscriptions, paywalls, or app-imposed message caps. Full chat, deep history, Memory Vault, voice mode, live web search, image/PDF analysis, and PowerPoint (.pptx) creation are unlocked.\n" +
                                "• Free-tier vs Blaze features: Chat, live web search, and .pptx deck generation run on no-billing Gemini models (3.8 Flash & 3.5 Flash-Lite). AI image generation uses Gemini Nano Banana 2.1, which requires Firebase Blaze billing on the backend.\n" +
                                "• Google/Firebase backend limits: Server-side rate limits (requests per minute/day) on Firebase's Spark tier are enforced by Google's servers and cannot be removed in client code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, jarvisColors.subtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = jarvisColors.accentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}
