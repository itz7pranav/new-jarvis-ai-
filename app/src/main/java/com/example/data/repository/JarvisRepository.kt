package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.GeminiModels
import com.example.data.model.MemoryEntry
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifEmpty { "User" }.take(80)
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.take(200))
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
                    put("preferredModel", GeminiModels.FLASH)
                    put("themeMode", "dark")
                    put("memoryPermissionGranted", true)
                    put("autoSpeakResponses", false)
                    put("wakeWordEnabled", false)
                    put("speechRate", 1.0)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val storedModel = snapshot.getString("preferredModel")
                val normalizedModel = GeminiModels.normalizeFreeTierModel(storedModel)
                if (storedModel != normalizedModel) {
                    docRef.update(
                        mapOf(
                            "preferredModel" to normalizedModel,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateProfileSettings(
        displayName: String? = null,
        preferredModel: String? = null,
        themeMode: String? = null,
        memoryPermissionGranted: Boolean? = null,
        autoSpeakResponses: Boolean? = null,
        wakeWordEnabled: Boolean? = null,
        speechRate: Double? = null,
        selectedVoiceName: String? = null,
        clearSelectedVoice: Boolean = false,
        systemInstructions: String? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = buildMap<String, Any?> {
            if (displayName != null) put("displayName", displayName.trim().ifEmpty { "User" }.take(80))
            if (preferredModel != null) put("preferredModel", preferredModel)
            if (themeMode != null) put("themeMode", themeMode)
            if (memoryPermissionGranted != null) put("memoryPermissionGranted", memoryPermissionGranted)
            if (autoSpeakResponses != null) put("autoSpeakResponses", autoSpeakResponses)
            if (wakeWordEnabled != null) put("wakeWordEnabled", wakeWordEnabled)
            if (speechRate != null) put("speechRate", speechRate.coerceIn(0.5, 2.0))
            if (clearSelectedVoice) {
                put("selectedVoiceName", "")
            } else if (selectedVoiceName != null) {
                put("selectedVoiceName", selectedVoiceName.take(120))
            }
            if (systemInstructions != null) put("systemInstructions", systemInstructions.take(2000))
            put("updatedAt", FieldValue.serverTimestamp())
        }.filterValues { it != null }

        try {
            docRef.update(updates as Map<String, Any>).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    // --- CONVERSATIONS ---

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val colRef = db.collection("users").document(userId).collection("conversations")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<Conversation> { it.pinned }
                            .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversation(
        title: String,
        modelId: String = GeminiModels.FLASH,
        lastMessagePreview: String = ""
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("conversations").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifEmpty { "New Conversation" }.take(160),
            "modelId" to modelId,
            "lastMessagePreview" to lastMessagePreview.take(300),
            "messageCount" to 0L,
            "pinned" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateConversation(
        conversationId: String,
        title: String? = null,
        modelId: String? = null,
        lastMessagePreview: String? = null,
        messageCount: Long? = null,
        pinned: Boolean? = null
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val updates = buildMap<String, Any> {
            if (title != null) put("title", title.trim().ifEmpty { "Untitled" }.take(160))
            if (modelId != null) put("modelId", modelId)
            if (lastMessagePreview != null) put("lastMessagePreview", lastMessagePreview.take(300))
            if (messageCount != null) put("messageCount", messageCount.coerceIn(0L, 10000L))
            if (pinned != null) put("pinned", pinned)
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        val uid = requireUserId()
        val convRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
        val msgsRef = convRef.collection("messages")
        try {
            val msgs = msgsRef.get().await()
            if (!msgs.isEmpty) {
                val chunks = msgs.documents.chunked(400)
                for (chunk in chunks) {
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            convRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path, auth)
            throw e
        }
    }

    // --- MESSAGES ---

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val colRef = db.collection("users").document(userId)
            .collection("conversations").document(conversationId)
            .collection("messages")
        emitAll(
            colRef.orderBy("clientOrder", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
        attachmentType: String = "none",
        attachmentName: String? = null,
        generatedImageFile: String? = null,
        modelUsed: String? = null,
        isError: Boolean = false,
        clientOrder: Long = System.currentTimeMillis()
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document()

        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("conversationId", conversationId)
            put("role", role)
            put("text", text.take(50000))
            put("attachmentType", attachmentType)
            if (!attachmentName.isNullOrBlank()) put("attachmentName", attachmentName.take(255))
            if (!generatedImageFile.isNullOrBlank()) put("generatedImageFile", generatedImageFile.take(255))
            if (!modelUsed.isNullOrBlank()) put("modelUsed", modelUsed.take(80))
            put("isError", isError)
            put("clientOrder", clientOrder)
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateMessage(
        conversationId: String,
        messageId: String,
        text: String,
        modelUsed: String? = null,
        generatedImageFile: String? = null,
        isError: Boolean = false
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document(messageId)

        val updates = buildMap<String, Any> {
            put("text", text.take(50000))
            if (!modelUsed.isNullOrBlank()) put("modelUsed", modelUsed.take(80))
            if (!generatedImageFile.isNullOrBlank()) put("generatedImageFile", generatedImageFile.take(255))
            put("isError", isError)
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteMessagesAfter(conversationId: String, afterClientOrder: Long) {
        val uid = requireUserId()
        val msgsRef = db.collection("users").document(uid)
            .collection("conversations").document(conversationId)
            .collection("messages")
        try {
            val snapshot = msgsRef.whereGreaterThan("clientOrder", afterClientOrder).get().await()
            if (!snapshot.isEmpty) {
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, msgsRef.path, auth)
            throw e
        }
    }

    // --- PERSISTENT MEMORIES ---

    fun observeMemories(userId: String): Flow<List<MemoryEntry>> = flow {
        val colRef = db.collection("users").document(userId).collection("memories")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MemoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun addMemory(
        category: String,
        content: String,
        sourceConversationId: String? = null
    ): String {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document()
        val safeCategory = if (category in listOf("preference", "fact", "project", "context")) {
            category
        } else {
            "context"
        }
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("category", safeCategory)
            put("content", content.trim().take(1000))
            if (!sourceConversationId.isNullOrBlank()) {
                put("sourceConversationId", sourceConversationId.take(128))
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun updateMemory(memoryId: String, category: String, content: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        val safeCategory = if (category in listOf("preference", "fact", "project", "context")) {
            category
        } else {
            "context"
        }
        val updates = mapOf(
            "category" to safeCategory,
            "content" to content.trim().take(1000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            throw e
        }
    }

    suspend fun deleteMemory(memoryId: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            throw e
        }
    }

    suspend fun clearAllMemories() {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("memories")
        try {
            val snapshot = colRef.get().await()
            if (!snapshot.isEmpty) {
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path, auth)
            throw e
        }
    }
}
