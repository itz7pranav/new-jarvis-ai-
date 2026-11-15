package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.core.GeneratedDeck
import com.example.core.PendingAttachment
import com.example.core.PresentationSlide
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModels
import com.example.data.model.MemoryEntry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class StreamChunkEvent(
    val accumulatedText: String,
    val groundingLinks: List<Pair<String, String>> = emptyList(),
    val activeModelUsed: String = GeminiModels.FLASH,
    val statusNotice: String? = null
)

data class ExtractedMemory(
    val category: String,
    val content: String
)

class JarvisAiService(
    private val liveWebSearchService: LiveWebSearchService = LiveWebSearchService()
) {

    private val imageGenModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModels.IMAGE_GEN,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val deckSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "deckTitle" to Schema.string(description = "Compelling presentation title (max 70 chars)"),
                "subtitle" to Schema.string(description = "Concise presentation subtitle or audience framing"),
                "accentTheme" to Schema.enumeration(
                    values = listOf("cyan", "emerald", "amber", "indigo"),
                    description = "Visual accent color matching the topic"
                ),
                "slides" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Clear slide heading"),
                            "bullets" to Schema.array(
                                items = Schema.string(),
                                description = "3 to 5 crisp, informative bullet points (max 140 chars each)"
                            ),
                            "keyTakeaway" to Schema.string(
                                description = "One-sentence bottom highlight or conclusion for this slide",
                                nullable = true
                            ),
                            "speakerNotes" to Schema.string(
                                description = "Concise presenter talking points for this slide",
                                nullable = true
                            )
                        ),
                        optionalProperties = listOf("keyTakeaway", "speakerNotes")
                    ),
                    description = "4 to 8 well-structured content slides"
                )
            )
        )
    }

    suspend fun generateImage(
        prompt: String,
        inputBitmap: Bitmap? = null
    ): Bitmap {
        val cleanPrompt = prompt.trim().ifBlank { "A futuristic glowing holographic assistant core" }
        return withTimeout(60_000L) {
            val response = if (inputBitmap != null) {
                imageGenModel.generateContent(
                    content {
                        image(inputBitmap)
                        text(cleanPrompt)
                    }
                )
            } else {
                imageGenModel.generateContent(cleanPrompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()
            imagePart?.image ?: throw IllegalStateException(
                "No image was returned by ${GeminiModels.IMAGE_GEN}."
            )
        }
    }

    suspend fun generatePresentationDeck(
        topicPrompt: String,
        attachment: PendingAttachment?,
        historyMessages: List<ChatMessage>,
        modelId: String,
        webSearchEnabled: Boolean
    ): GeneratedDeck {
        val primaryModelId = GeminiModels.normalizeFreeTierModel(modelId)
        val liveContext = if (webSearchEnabled && liveWebSearchService.shouldPreFetchLiveWeb(topicPrompt)) {
            val searchOut = liveWebSearchService.searchLive(topicPrompt)
            if (searchOut.hasAnyContent()) formatLiveWebContext(searchOut) else null
        } else {
            null
        }

        val recentContext = historyMessages
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(4)
            .joinToString("\n") { "${it.role}: ${it.text.take(400)}" }

        val instructionPrompt = buildString {
            appendLine("Create a professional, well-researched PowerPoint presentation deck based on the request below.")
            appendLine("Produce 4 to 8 insightful content slides (in addition to the automatic cover slide). Keep bullets crisp, scannable, and data-rich.")
            if (recentContext.isNotBlank()) {
                appendLine("\nRecent conversation context:\n$recentContext")
            }
            if (!liveContext.isNullOrBlank()) {
                appendLine("\n$liveContext")
            }
            appendLine("\nPresentation Request: $topicPrompt")
        }

        val modelsToTry = if (primaryModelId == GeminiModels.FLASH) {
            listOf(GeminiModels.FLASH, GeminiModels.FLASH_LITE)
        } else {
            listOf(GeminiModels.FLASH_LITE)
        }

        var lastError: Exception? = null
        for ((idx, candidate) in modelsToTry.withIndex()) {
            try {
                val deckModel = Firebase.ai.generativeModel(
                    modelName = candidate,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = deckSchema
                    }
                )
                val userContent = buildUserContent(instructionPrompt, attachment)
                val response = withTimeout(60_000L) {
                    deckModel.generateContent(userContent)
                }
                val rawJson = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini returned empty presentation JSON.")
                return parseGeneratedDeckJson(rawJson, topicPrompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (isRetryableOrToolFallbackError(e, false) && idx < modelsToTry.lastIndex) {
                    delay(1_200L)
                    continue
                }
                throw e
            }
        }
        throw (lastError ?: IllegalStateException("Failed to generate presentation."))
    }

    internal fun parseGeneratedDeckJson(rawJson: String, fallbackTopic: String): GeneratedDeck {
        val obj = JSONObject(rawJson)
        val deckTitle = obj.optString("deckTitle").trim().ifBlank { buildSmartLocalTitle(fallbackTopic) }
        val subtitle = obj.optString("subtitle").trim().ifBlank { "Created with Jarvis AI" }
        val accentHex = when (obj.optString("accentTheme").lowercase()) {
            "emerald" -> "10B981"
            "amber" -> "F59E0B"
            "indigo" -> "6366F1"
            else -> "00B4D8"
        }
        val slidesArr = obj.optJSONArray("slides")
        val slides = mutableListOf<PresentationSlide>()
        if (slidesArr != null) {
            for (i in 0 until slidesArr.length()) {
                val sObj = slidesArr.optJSONObject(i) ?: continue
                val sTitle = sObj.optString("title").trim().ifBlank { "Slide ${i + 1}" }
                val bulletsArr = sObj.optJSONArray("bullets")
                val bullets = mutableListOf<String>()
                if (bulletsArr != null) {
                    for (b in 0 until bulletsArr.length()) {
                        val text = bulletsArr.optString(b).trim()
                        if (text.isNotBlank()) bullets.add(text)
                    }
                }
                if (bullets.isEmpty()) bullets.add("Key insights on $sTitle")
                val takeaway = sObj.optString("keyTakeaway").trim().takeIf { it.isNotBlank() && it != "null" }
                val notes = sObj.optString("speakerNotes").trim().takeIf { it.isNotBlank() && it != "null" }
                slides.add(
                    PresentationSlide(
                        title = sTitle,
                        bullets = bullets,
                        keyTakeaway = takeaway,
                        speakerNotes = notes
                    )
                )
            }
        }
        if (slides.isEmpty()) {
            throw IllegalStateException("Presentation contained no slides.")
        }
        return GeneratedDeck(
            deckTitle = deckTitle,
            subtitle = subtitle,
            accentColorHex = accentHex,
            slides = slides
        )
    }

    fun isExplicitImageRequest(prompt: String): Boolean {
        val lower = prompt.trim().lowercase()
        if (lower.startsWith("/image ") || lower.startsWith("/img ")) return true
        return Regex("""^(?:please\s+)?(?:can you\s+)?(?:generate|create|draw|paint|render|make|design)\s+(?:an?\s+)?(?:ai\s+)?(?:image|picture|photo|illustration|artwork|logo|wallpaper|poster|portrait)\b""")
            .containsMatchIn(lower)
    }

    fun isExplicitPptRequest(prompt: String): Boolean {
        val lower = prompt.trim().lowercase()
        if (lower.startsWith("/ppt ") || lower.startsWith("/slides ")) return true
        return Regex("""^(?:please\s+)?(?:can you\s+)?(?:generate|create|make|build|design|prepare)\s+(?:an?\s+)?(?:\d+[-\s]slide\s+)?(?:ppt|pptx|powerpoint|presentation|slide\s*deck|slides)\b""")
            .containsMatchIn(lower)
    }

    fun streamChatReply(
        modelId: String,
        historyMessages: List<ChatMessage>,
        userPrompt: String,
        attachment: PendingAttachment?,
        memories: List<MemoryEntry>,
        customInstructions: String?,
        webSearchEnabled: Boolean
    ): Flow<StreamChunkEvent> = flow {
        val primaryModelId = GeminiModels.normalizeFreeTierModel(modelId)
        val systemText = buildSystemPrompt(memories, customInstructions, webSearchEnabled)
        val promptUrls = liveWebSearchService.extractUrls(userPrompt)

        var liveSearchOutput: LiveWebSearchOutput? = null
        if (webSearchEnabled && liveWebSearchService.shouldPreFetchLiveWeb(userPrompt)) {
            emit(
                StreamChunkEvent(
                    accumulatedText = "",
                    activeModelUsed = primaryModelId,
                    statusNotice = if (promptUrls.isNotEmpty()) {
                        "Reading webpage & searching live web..."
                    } else {
                        "Searching the live web..."
                    }
                )
            )
            liveSearchOutput = liveWebSearchService.searchLive(userPrompt)
        }

        data class AttemptPlan(
            val modelId: String,
            val useGoogleSearchTool: Boolean,
            val useUrlContextTool: Boolean,
            val ensureLiveSearchFallback: Boolean,
            val maxHistoryChars: Int,
            val delayBeforeMs: Long,
            val notice: String?
        )

        val secondaryModel = if (primaryModelId == GeminiModels.FLASH) {
            GeminiModels.FLASH_LITE
        } else {
            GeminiModels.FLASH
        }

        val attemptPlans = buildList {
            // 1. Primary model with Gemini Search Grounding (+ UrlContext when URLs are present)
            add(
                AttemptPlan(
                    modelId = primaryModelId,
                    useGoogleSearchTool = webSearchEnabled,
                    useUrlContextTool = webSearchEnabled && promptUrls.isNotEmpty(),
                    ensureLiveSearchFallback = false,
                    maxHistoryChars = DEFAULT_HISTORY_CHAR_BUDGET,
                    delayBeforeMs = 0L,
                    notice = null
                )
            )
            // 2. If UrlContext + GoogleSearch combination or primary model hit an error/limit, try alternate free-tier model with GoogleSearch
            add(
                AttemptPlan(
                    modelId = secondaryModel,
                    useGoogleSearchTool = webSearchEnabled,
                    useUrlContextTool = false,
                    ensureLiveSearchFallback = webSearchEnabled,
                    maxHistoryChars = DEFAULT_HISTORY_CHAR_BUDGET,
                    delayBeforeMs = 1_200L,
                    notice = "Switching to $secondaryModel..."
                )
            )
            // 3. Backoff retry on Flash-Lite with LiveWebSearchService results (no GoogleSearch tool in case grounding tool is rate-limited/restricted)
            if (webSearchEnabled) {
                add(
                    AttemptPlan(
                        modelId = GeminiModels.FLASH_LITE,
                        useGoogleSearchTool = false,
                        useUrlContextTool = false,
                        ensureLiveSearchFallback = true,
                        maxHistoryChars = COMPACT_HISTORY_CHAR_BUDGET,
                        delayBeforeMs = 1_500L,
                        notice = "Using live web search results with Gemini..."
                    )
                )
            } else {
                add(
                    AttemptPlan(
                        modelId = GeminiModels.FLASH_LITE,
                        useGoogleSearchTool = false,
                        useUrlContextTool = false,
                        ensureLiveSearchFallback = false,
                        maxHistoryChars = COMPACT_HISTORY_CHAR_BUDGET,
                        delayBeforeMs = 2_500L,
                        notice = "Retrying after brief server cooldown..."
                    )
                )
            }
        }

        var lastError: Exception? = null
        for ((idx, plan) in attemptPlans.withIndex()) {
            if (plan.delayBeforeMs > 0L) {
                if (!plan.notice.isNullOrBlank()) {
                    emit(
                        StreamChunkEvent(
                            accumulatedText = "",
                            activeModelUsed = plan.modelId,
                            statusNotice = plan.notice
                        )
                    )
                }
                delay(plan.delayBeforeMs)
            }

            if (plan.ensureLiveSearchFallback && liveSearchOutput == null && userPrompt.isNotBlank()) {
                emit(
                    StreamChunkEvent(
                        accumulatedText = "",
                        activeModelUsed = plan.modelId,
                        statusNotice = "Fetching live web results..."
                    )
                )
                liveSearchOutput = liveWebSearchService.searchLive(userPrompt)
            }

            val tools = buildList {
                if (plan.useGoogleSearchTool) add(Tool.googleSearch())
                if (plan.useUrlContextTool) add(Tool.urlContext())
            }.takeIf { it.isNotEmpty() }

            val liveResults = liveSearchOutput
            val liveContextText = if (liveResults != null && liveResults.hasAnyContent()) {
                formatLiveWebContext(liveResults)
            } else {
                null
            }

            val effectiveSystemText = buildString {
                append(systemText)
                if (webSearchEnabled && !plan.useGoogleSearchTool) {
                    if (liveContextText != null) {
                        append("\n\nLive Web Search Results are attached to the user's turn with URLs and timestamps. Base your real-time facts on these live results and cite their URLs.")
                    } else {
                        append("\n\nFallback Note: Live web search could not retrieve results for this query (network/server busy). Do NOT invent live news, prices, or current facts—be transparent if real-time verification was unavailable.")
                    }
                }
            }

            val enrichedPrompt = if (liveContextText != null) {
                "$userPrompt\n\n$liveContextText"
            } else {
                userPrompt
            }

            val userContent = buildUserContent(enrichedPrompt, attachment)
            val sdkHistory = buildHistoryContents(historyMessages, plan.maxHistoryChars)

            val preFetchedLinks = buildList {
                liveResults?.urlSummaries?.forEach { add(it.title to it.url) }
                liveResults?.results?.forEach { add("${it.title} (${it.source})" to it.url) }
            }

            var emittedAnyToken = false
            try {
                streamWithSingleModel(
                    modelId = plan.modelId,
                    systemText = effectiveSystemText,
                    tools = tools,
                    sdkHistory = sdkHistory,
                    userContent = userContent,
                    baseLinks = preFetchedLinks
                ).collect { chunk ->
                    if (chunk.accumulatedText.isNotEmpty()) {
                        emittedAnyToken = true
                    }
                    emit(chunk)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val retryable = isRetryableOrToolFallbackError(e, plan.useGoogleSearchTool || plan.useUrlContextTool)
                if (!emittedAnyToken && retryable && idx < attemptPlans.lastIndex) {
                    Log.w(TAG, "Attempt ${idx + 1} (${plan.modelId}, search=${plan.useGoogleSearchTool}) fallback: ${e.message}")
                    continue
                }
                throw e
            }
        }
        if (lastError != null) throw lastError
    }

    private fun formatLiveWebContext(output: LiveWebSearchOutput): String {
        return buildString {
            appendLine("[LIVE REAL-TIME WEB SEARCH RESULTS — Query: \"${output.query}\"]")
            if (output.urlSummaries.isNotEmpty()) {
                appendLine("\nFetched Webpage Content:")
                output.urlSummaries.forEachIndexed { idx, doc ->
                    appendLine("${idx + 1}. Title: ${doc.title}")
                    appendLine("   URL: ${doc.url}")
                    appendLine("   Content Excerpt: ${doc.snippet}")
                }
            }
            if (output.results.isNotEmpty()) {
                appendLine("\nLive Search & News Results:")
                output.results.forEachIndexed { idx, res ->
                    val datePart = res.publishedAt?.takeIf { it.isNotBlank() }?.let { " | Published: $it" }.orEmpty()
                    appendLine("${idx + 1}. [${res.source}$datePart] ${res.title}")
                    appendLine("   URL: ${res.url}")
                    appendLine("   Snippet: ${res.snippet}")
                }
            }
            appendLine("[END LIVE WEB SEARCH RESULTS]")
        }.trim()
    }

    private fun isRetryableOrToolFallbackError(e: Exception, usedSearchTool: Boolean): Boolean {
        if (e is QuotaExceededException) return true
        val msg = e.message.orEmpty().lowercase()
        val quotaOrSize = msg.contains("429") ||
            msg.contains("quota") ||
            msg.contains("resource_exhausted") ||
            msg.contains("rate limit") ||
            msg.contains("too many requests") ||
            (msg.contains("token") && msg.contains("limit"))
        if (quotaOrSize) return true

        if (usedSearchTool) {
            return msg.contains("tool") ||
                msg.contains("grounding") ||
                msg.contains("google_search") ||
                msg.contains("url_context") ||
                msg.contains("not supported") ||
                msg.contains("billing") ||
                msg.contains("permission")
        }
        return false
    }

    private fun streamWithSingleModel(
        modelId: String,
        systemText: String,
        tools: List<Tool>?,
        sdkHistory: List<Content>,
        userContent: Content,
        baseLinks: List<Pair<String, String>>
    ): Flow<StreamChunkEvent> = flow {
        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content("system") { text(systemText) },
            tools = tools
        )
        val chat = model.startChat(history = sdkHistory)
        val accumulated = StringBuilder()
        val collectedLinks = linkedMapOf<String, String>()
        baseLinks.forEach { (title, url) ->
            if (url.isNotBlank()) {
                collectedLinks[url] = title
            }
        }

        withTimeout(60_000L) {
            chat.sendMessageStream(userContent).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                }
                chunk.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.forEach { gChunk ->
                    val web = gChunk.web
                    val uri = web?.uri
                    if (web != null && !uri.isNullOrBlank()) {
                        val title = web.title?.takeIf { it.isNotBlank() } ?: web.domain ?: uri
                        collectedLinks[uri] = title
                    }
                }
                emit(
                    StreamChunkEvent(
                        accumulatedText = accumulated.toString(),
                        groundingLinks = collectedLinks.entries.map { it.value to it.key },
                        activeModelUsed = modelId
                    )
                )
            }
        }
    }

    private fun buildUserContent(
        userPrompt: String,
        attachment: PendingAttachment?
    ): Content = content("user") {
        when (attachment) {
            is PendingAttachment.ImageAttachment -> {
                image(attachment.bitmap)
            }
            is PendingAttachment.DocumentAttachment -> {
                if (attachment.extractedText != null) {
                    text("[Attached document: ${attachment.displayName}]\n```\n${attachment.extractedText}\n```")
                } else {
                    inlineData(attachment.bytes, attachment.mimeType)
                }
            }
            null -> Unit
        }
        val effectivePrompt = when {
            userPrompt.isNotBlank() -> userPrompt
            attachment is PendingAttachment.ImageAttachment -> "Please analyze this image in detail."
            attachment is PendingAttachment.DocumentAttachment ->
                "Please analyze and summarize this attached document (${attachment.displayName})."
            else -> "Hello"
        }
        text(effectivePrompt)
    }

    /**
     * Zero-quota local title formatter: avoids burning an extra Gemini API call on every new chat.
     */
    fun buildSmartLocalTitle(firstPrompt: String): String {
        val cleaned = firstPrompt
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("?")
            .trim()
        if (cleaned.isEmpty()) return "New Conversation"
        val words = cleaned.split(" ")
        val summary = if (words.size <= 6) cleaned else words.take(6).joinToString(" ")
        return summary.take(46).let { if (cleaned.length > it.length) "$it…" else it }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /**
     * Zero-quota on-device memory extractor: detects explicit user preferences, identity facts,
     * and ongoing projects locally so every chat turn consumes at most 1 Gemini API call.
     */
    fun extractMemoriesLocally(
        userPrompt: String,
        existingMemories: List<MemoryEntry>
    ): List<ExtractedMemory> {
        val trimmed = userPrompt.trim()
        if (trimmed.length < 12 || containsSensitivePattern(trimmed)) return emptyList()

        val existingLower = existingMemories.map { it.content.lowercase() }
        val candidates = mutableListOf<ExtractedMemory>()

        val explicitPatterns = listOf(
            Regex("""(?i)\b(?:remember(?:\s+that)?|don'?t\s+forget(?:\s+that)?|keep\s+in\s+mind(?:\s+that)?)\s+([^.!?\n]{4,180})""") to "context",
            Regex("""(?i)\b(?:my\s+name\s+is|call\s+me)\s+([A-Za-z][A-Za-z0-9 .'\-]{1,40})""") to "fact",
            Regex("""(?i)\b(?:i\s+(?:prefer|always\s+use|like\s+to\s+use|want\s+answers?\s+in))\s+([^.!?\n]{3,140})""") to "preference",
            Regex("""(?i)\b(?:my\s+(?:current\s+)?project\s+is|i'?m\s+(?:building|working\s+on))\s+([^.!?\n]{4,160})""") to "project"
        )

        for ((pattern, category) in explicitPatterns) {
            val match = pattern.find(trimmed) ?: continue
            val captured = match.groupValues.getOrNull(1)?.trim()?.trimEnd('.', ',', ';') ?: continue
            if (captured.length < 3 || containsSensitivePattern(captured)) continue

            val formatted = when (category) {
                "fact" -> "User prefers to be called $captured"
                "preference" -> "User preference: $captured"
                "project" -> "Ongoing project: $captured"
                else -> captured.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }.take(240)

            val norm = formatted.lowercase()
            val alreadyStored = existingLower.any { existing ->
                existing == norm || existing.contains(captured.lowercase())
            }
            if (!alreadyStored && candidates.none { it.content.equals(formatted, ignoreCase = true) }) {
                candidates.add(ExtractedMemory(category = category, content = formatted))
            }
            if (candidates.size >= 2) break
        }
        return candidates
    }

    private fun buildSystemPrompt(
        memories: List<MemoryEntry>,
        customInstructions: String?,
        webSearchEnabled: Boolean
    ): String {
        val nowFormatted = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' HH:mm z", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        val sb = StringBuilder()
        sb.appendLine(
            "You are JARVIS AI, a highly intelligent, friendly, precise, and conversational personal AI assistant. " +
                "Adapt explanations to the user's level, reason step-by-step on math, science, and code, format structured answers cleanly in Markdown (code blocks with language tags, tables, lists), and respond in the same language the user writes in."
        )
        sb.appendLine("Current Local Date & Time: $nowFormatted.")
        sb.appendLine(
            "Honesty & Grounding Rule: Never invent facts, citations, or search results. When live Google Search or live web search results are provided, prioritize up-to-date facts and cite sources accurately. If you do not know something or live data was unavailable, state so clearly."
        )
        if (!webSearchEnabled) {
            sb.appendLine(
                "Note: Live Web Search is currently toggled OFF by the user for this message. Do not claim to have searched live web pages."
            )
        }
        if (!customInstructions.isNullOrBlank()) {
            sb.appendLine("\nUser's Custom Instructions:\n${customInstructions.trim()}")
        }
        if (memories.isNotEmpty()) {
            sb.appendLine("\nRemembered User Context & Preferences (apply naturally when relevant):")
            var memChars = 0
            for (mem in memories.take(60)) {
                val line = "- [${mem.category}] ${mem.content}"
                if (memChars + line.length > MAX_MEMORY_PROMPT_CHARS) break
                sb.appendLine(line)
                memChars += line.length
            }
        }
        return sb.toString().trim()
    }

    private fun buildHistoryContents(
        messages: List<ChatMessage>,
        maxHistoryChars: Int
    ): List<Content> {
        val valid = messages.filter { !it.isError && it.text.isNotBlank() }
        val selected = mutableListOf<ChatMessage>()
        var charCount = 0
        for (msg in valid.asReversed()) {
            if (selected.size >= MAX_HISTORY_MESSAGES) break
            val len = msg.text.length
            if (selected.isNotEmpty() && charCount + len > maxHistoryChars) break
            selected.add(msg)
            charCount += len
        }
        return selected.asReversed().map { msg ->
            val role = if (msg.role == "model") "model" else "user"
            content(role) { text(msg.text) }
        }
    }

    private fun containsSensitivePattern(text: String): Boolean {
        val lower = text.lowercase()
        val sensitiveKeywords = listOf("password", "passcode", "api key", "secret key", "token", "cvv", "ssn", "pin is")
        return sensitiveKeywords.any { lower.contains(it) }
    }

    private companion object {
        const val TAG = "JarvisAiService"
        const val MAX_HISTORY_MESSAGES = 100
        const val DEFAULT_HISTORY_CHAR_BUDGET = 80_000
        const val COMPACT_HISTORY_CHAR_BUDGET = 16_000
        const val MAX_MEMORY_PROMPT_CHARS = 10_000
    }
}
