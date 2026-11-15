package com.example.ui.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalJarvisColors
import com.example.voice.VoiceUiState

@Composable
fun VoiceAssistantOverlay(
    voiceState: VoiceUiState,
    isThinking: Boolean,
    lastResponsePreview: String?,
    wakeWordEnabled: Boolean,
    onToggleWakeWord: (Boolean) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onInterruptSpeaking: () -> Unit,
    onClose: () -> Unit
) {
    val jarvisColors = LocalJarvisColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val activeColor = when {
        voiceState.isSpeaking -> jarvisColors.accentAmber
        voiceState.isListening || voiceState.isWakeWordStandby -> jarvisColors.accentCyan
        isThinking -> jarvisColors.accentCyan
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = jarvisColors.accentCyan
                )
                Text(
                    text = "JARVIS VOICE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_voice_overlay_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Voice Mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Center Orb + Transcript
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FilterChip(
                selected = wakeWordEnabled,
                onClick = { onToggleWakeWord(!wakeWordEnabled) },
                label = {
                    Text(
                        if (wakeWordEnabled) "Wake-Word \"Jarvis\" Standby: ON"
                        else "Enable \"Jarvis\" Wake-Word Standby"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("wake_word_chip")
            )

            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                val rmsScale = 1f + (voiceState.rmsDb / 24f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = this.center
                    val baseR = size.minDimension * 0.28f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseR * 1.9f * pulse * rmsScale
                        )
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.45f),
                        radius = baseR * 1.28f * pulse,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = activeColor.copy(alpha = 0.22f),
                        radius = baseR * 1.55f * rmsScale,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                FilledIconButton(
                    onClick = {
                        when {
                            voiceState.isSpeaking -> onInterruptSpeaking()
                            voiceState.isListening -> onStopListening()
                            else -> onStartListening()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = activeColor.copy(alpha = 0.22f),
                        contentColor = activeColor
                    ),
                    modifier = Modifier
                        .size(96.dp)
                        .testTag("voice_orb_action_button")
                ) {
                    Icon(
                        imageVector = when {
                            voiceState.isSpeaking -> Icons.Filled.Stop
                            voiceState.isListening -> Icons.Filled.Stop
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = when {
                            voiceState.isSpeaking -> "Interrupt Jarvis"
                            voiceState.isListening -> "Stop Listening"
                            else -> "Tap to Speak"
                        },
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = when {
                    voiceState.isSpeaking -> "Speaking... Tap orb to interrupt"
                    isThinking -> "Jarvis is thinking..."
                    voiceState.isWakeWordStandby -> "Standby — Say \"Jarvis\" followed by your request"
                    voiceState.isListening -> "Listening..."
                    else -> "Tap the orb to speak"
                },
                style = MaterialTheme.typography.titleMedium,
                color = activeColor,
                textAlign = TextAlign.Center
            )

            if (voiceState.partialTranscript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, jarvisColors.subtleBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${voiceState.partialTranscript}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (!lastResponsePreview.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastResponsePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (!voiceState.statusMessage.isNullOrBlank()) {
                Text(
                    text = voiceState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Honest Android capability disclosure at bottom
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Hands-free \"Jarvis\" wake-word uses active foreground speech recognition while this screen is open. Background screen-off hotword detection requires privileged system hardware DSP permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
