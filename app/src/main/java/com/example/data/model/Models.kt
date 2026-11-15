package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object GeminiModels {
    const val FLASH = "gemini-3.8-flash"
    const val FLASH_LITE = "gemini-3.5-flash-lite"
    const val IMAGE_GEN = "gemini-nano-banana-2.1"

    val allChatModels = listOf(
        ModelOption(
            id = FLASH,
            displayName = "Gemini 3.8 Flash",
            subtitle = "100% Free • Best all-round reasoning, coding & multimodal",
            requiresBillingNote = false
        ),
        ModelOption(
            id = FLASH_LITE,
            displayName = "Gemini 3.5 Flash-Lite",
            subtitle = "100% Free • Ultra-fast, high-volume workhorse",
            requiresBillingNote = false
        )
    )

    fun normalizeFreeTierModel(modelId: String?): String {
        return when (modelId) {
            FLASH_LITE -> FLASH_LITE
            else -> FLASH
        }
    }
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val requiresBillingNote: Boolean
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "User",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredModel: String = GeminiModels.FLASH,
    val themeMode: String = "dark",
    val memoryPermissionGranted: Boolean = true,
    val autoSpeakResponses: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val speechRate: Double = 1.0,
    val selectedVoiceName: String? = null,
    val systemInstructions: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val modelId: String = GeminiModels.FLASH,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val attachmentType: String = "none",
    val attachmentName: String? = null,
    val generatedImageFile: String? = null,
    val modelUsed: String? = null,
    val isError: Boolean = false,
    val clientOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MemoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "context",
    val content: String = "",
    val sourceConversationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
