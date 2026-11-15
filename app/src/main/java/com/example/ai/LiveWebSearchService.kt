package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

data class LiveWebResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String,
    val publishedAt: String? = null
)

data class LiveWebSearchOutput(
    val query: String,
    val results: List<LiveWebResult>,
    val urlSummaries: List<LiveWebResult> = emptyList()
) {
    fun hasAnyContent(): Boolean = results.isNotEmpty() || urlSummaries.isNotEmpty()
}

class LiveWebSearchService {

    suspend fun searchLive(userPrompt: String): LiveWebSearchOutput = withContext(Dispatchers.IO) {
        val urls = extractUrls(userPrompt)
        val cleanQuery = buildSearchQuery(userPrompt)

        val output = withTimeoutOrNull(TOTAL_SEARCH_TIMEOUT_MS) {
            supervisorScope {
                val ddgDeferred = async {
                    if (cleanQuery.isNotBlank()) fetchDuckDuckGoResults(cleanQuery) else emptyList()
                }
                val newsDeferred = async {
                    if (cleanQuery.isNotBlank()) fetchGoogleNewsRss(cleanQuery) else emptyList()
                }
                val wikiDeferred = async {
                    if (cleanQuery.isNotBlank() && !isPureNewsQuery(cleanQuery)) {
                        fetchWikipediaResults(cleanQuery)
                    } else {
                        emptyList()
                    }
                }
                val urlDeferred = async {
                    if (urls.isNotEmpty()) {
                        urls.take(2).mapNotNull { fetchUrlContent(it) }
                    } else {
                        emptyList()
                    }
                }

                val ddg = runCatching { ddgDeferred.await() }.getOrDefault(emptyList())
                val news = runCatching { newsDeferred.await() }.getOrDefault(emptyList())
                val wiki = runCatching { wikiDeferred.await() }.getOrDefault(emptyList())
                val urlDocs = runCatching { urlDeferred.await() }.getOrDefault(emptyList())

                val merged = mergeDeduplicatedResults(
                    primary = if (isNewsOrCurrentEventQuery(userPrompt)) news + ddg + wiki else ddg + news + wiki
                )
                LiveWebSearchOutput(
                    query = cleanQuery,
                    results = merged.take(MAX_RESULTS),
                    urlSummaries = urlDocs
                )
            }
        }

        output ?: LiveWebSearchOutput(query = cleanQuery, results = emptyList(), urlSummaries = emptyList())
    }

    fun shouldPreFetchLiveWeb(prompt: String): Boolean {
        if (extractUrls(prompt).isNotEmpty()) return true
        val lower = prompt.lowercase().trim()
        if (lower.length < 4) return false

        val liveTriggers = listOf(
            "search", "web", "google", "look up", "lookup", "find online",
            "latest", "recent", "news", "today", "tonight", "yesterday", "this week",
            "this month", "this year", "current", "currently", "right now", "live",
            "weather", "forecast", "temperature", "stock", "price", "crypto", "bitcoin",
            "exchange rate", "score", "match", "winner", "who won", "standings",
            "release date", "released", "update", "breaking", "trending", "headline",
            "2025", "2026", "2027", "president", "ceo", "prime minister", "election",
            "schedule", "status of", "what happened", "is it true"
        )
        return liveTriggers.any { lower.contains(it) }
    }

    internal fun buildSearchQuery(prompt: String): String {
        val withoutUrls = URL_REGEX.replace(prompt, " ")
        return withoutUrls
            .replace(Regex("(?i)^(please\\s+)?(search(\\s+the\\s+web)?(\\s+for)?|look\\s+up|google|find\\s+(me\\s+)?(online|info\\s+on)?|tell\\s+me\\s+(about)?)\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
    }

    internal fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', ';', '"', '\'') }
            .filter { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            .distinct()
            .toList()
    }

    private fun isNewsOrCurrentEventQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return listOf("news", "latest", "today", "headline", "breaking", "score", "winner", "2026", "recent", "current").any {
            lower.contains(it)
        }
    }

    private fun isPureNewsQuery(query: String): Boolean {
        val lower = query.lowercase()
        return lower.contains("news") || lower.contains("headline") || lower.contains("weather") || lower.contains("score")
    }

    private fun fetchGoogleNewsRss(query: String): List<LiveWebResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val endpoint = "https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en"
            val xml = httpGet(endpoint) ?: return emptyList()
            parseGoogleNewsRss(xml)
        } catch (e: Exception) {
            Log.w(TAG, "Google News RSS fetch failed: ${e.message}")
            emptyList()
        }
    }

    internal fun parseGoogleNewsRss(xml: String): List<LiveWebResult> {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            val items = doc.getElementsByTagName("item")
            val out = mutableListOf<LiveWebResult>()
            val count = minOf(items.length, 6)
            for (i in 0 until count) {
                val el = items.item(i) as? Element ?: continue
                val title = el.getElementsByTagName("title").item(0)?.textContent?.trim().orEmpty()
                val link = el.getElementsByTagName("link").item(0)?.textContent?.trim().orEmpty()
                val pubDate = el.getElementsByTagName("pubDate").item(0)?.textContent?.trim()
                val descRaw = el.getElementsByTagName("description").item(0)?.textContent?.trim().orEmpty()
                val sourceEl = el.getElementsByTagName("source").item(0)?.textContent?.trim()
                if (title.isNotBlank() && link.startsWith("http")) {
                    out.add(
                        LiveWebResult(
                            title = decodeHtmlEntities(title),
                            url = link,
                            snippet = stripHtml(descRaw).take(320).ifBlank { decodeHtmlEntities(title) },
                            source = sourceEl?.takeIf { it.isNotBlank() } ?: "Google News",
                            publishedAt = pubDate
                        )
                    )
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "RSS parse error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchDuckDuckGoResults(query: String): List<LiveWebResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val html = httpGet("https://html.duckduckgo.com/html/?q=$encoded")
        val parsedHtml = if (!html.isNullOrBlank()) parseDuckDuckGoHtml(html) else emptyList()
        if (parsedHtml.isNotEmpty()) return parsedHtml

        // Fallback to DuckDuckGo Instant Answer JSON API
        val json = httpGet("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            ?: return emptyList()
        return parseDuckDuckGoInstantJson(json)
    }

    internal fun parseDuckDuckGoHtml(html: String): List<LiveWebResult> {
        val out = mutableListOf<LiveWebResult>()
        val titleLinkRegex = Regex(
            """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val snippetRegex = Regex(
            """<(?:a|div)[^>]*class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</(?:a|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        val linkMatches = titleLinkRegex.findAll(html).toList()
        val snippetMatches = snippetRegex.findAll(html).toList()

        for (i in linkMatches.indices) {
            val hrefRaw = decodeHtmlEntities(linkMatches[i].groupValues[1])
            val resolvedUrl = resolveDuckDuckGoUrl(hrefRaw) ?: continue
            val title = stripHtml(linkMatches[i].groupValues[2]).trim()
            val snippet = snippetMatches.getOrNull(i)?.groupValues?.get(1)?.let { stripHtml(it) }.orEmpty()
            if (title.isNotBlank()) {
                val host = runCatching { URL(resolvedUrl).host.removePrefix("www.") }.getOrDefault("Web")
                out.add(
                    LiveWebResult(
                        title = title,
                        url = resolvedUrl,
                        snippet = snippet.take(360).ifBlank { title },
                        source = host
                    )
                )
            }
            if (out.size >= 6) break
        }
        return out
    }

    internal fun parseDuckDuckGoInstantJson(rawJson: String): List<LiveWebResult> {
        return try {
            val obj = JSONObject(rawJson)
            val out = mutableListOf<LiveWebResult>()
            val abstractText = obj.optString("AbstractText").trim()
            val abstractUrl = obj.optString("AbstractURL").trim()
            val heading = obj.optString("Heading").trim()
            if (abstractText.isNotBlank() && abstractUrl.startsWith("http")) {
                out.add(
                    LiveWebResult(
                        title = heading.ifBlank { "Summary" },
                        url = abstractUrl,
                        snippet = abstractText.take(400),
                        source = obj.optString("AbstractSource").ifBlank { "DuckDuckGo" }
                    )
                )
            }
            val topics = obj.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until minOf(topics.length(), 4)) {
                    val item = topics.optJSONObject(i) ?: continue
                    val text = item.optString("Text").trim()
                    val firstUrl = item.optString("FirstURL").trim()
                    if (text.isNotBlank() && firstUrl.startsWith("http")) {
                        out.add(
                            LiveWebResult(
                                title = text.substringBefore(" - ").take(90),
                                url = firstUrl,
                                snippet = text.take(320),
                                source = "DuckDuckGo"
                            )
                        )
                    }
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchWikipediaResults(query: String): List<LiveWebResult> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val endpoint = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=3&utf8=1&format=json"
            val json = httpGet(endpoint) ?: return emptyList()
            parseWikipediaJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia fetch failed: ${e.message}")
            emptyList()
        }
    }

    internal fun parseWikipediaJson(rawJson: String): List<LiveWebResult> {
        return try {
            val root = JSONObject(rawJson)
            val arr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val out = mutableListOf<LiveWebResult>()
            for (i in 0 until minOf(arr.length(), 3)) {
                val item = arr.optJSONObject(i) ?: continue
                val title = item.optString("title").trim()
                val snippet = stripHtml(item.optString("snippet"))
                if (title.isNotBlank()) {
                    val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                    out.add(
                        LiveWebResult(
                            title = "$title — Wikipedia",
                            url = "https://en.wikipedia.org/wiki/$slug",
                            snippet = snippet.take(320),
                            source = "Wikipedia"
                        )
                    )
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchUrlContent(urlString: String): LiveWebResult? {
        return try {
            val raw = httpGet(urlString, maxBytes = 180_000) ?: return null
            val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)
            val title = titleMatch?.let { stripHtml(it).trim() }?.takeIf { it.isNotBlank() } ?: urlString
            val bodyText = stripHtml(
                raw.replace(Regex("""<(script|style|noscript|svg)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            ).take(4000)
            if (bodyText.isBlank()) return null
            val host = runCatching { URL(urlString).host.removePrefix("www.") }.getOrDefault("Webpage")
            LiveWebResult(
                title = title,
                url = urlString,
                snippet = bodyText,
                source = host
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read URL $urlString: ${e.message}")
            null
        }
    }

    private fun resolveDuckDuckGoUrl(rawHref: String): String? {
        val normalized = if (rawHref.startsWith("//")) "https:$rawHref" else rawHref
        if (normalized.contains("uddg=")) {
            val encoded = normalized.substringAfter("uddg=").substringBefore("&")
            val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            if (decoded != null && (decoded.startsWith("http://") || decoded.startsWith("https://"))) {
                return decoded
            }
        }
        return if (normalized.startsWith("http://") || normalized.startsWith("https://")) normalized else null
    }

    private fun mergeDeduplicatedResults(primary: List<LiveWebResult>): List<LiveWebResult> {
        val seenUrls = hashSetOf<String>()
        val out = mutableListOf<LiveWebResult>()
        for (item in primary) {
            val key = item.url.trimEnd('/').lowercase()
            if (seenUrls.add(key)) {
                out.add(item)
            }
        }
        return out
    }

    private fun httpGet(urlString: String, maxBytes: Int = 250_000): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_500
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,application/json;q=0.9,*/*;q=0.8")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    val toWrite = minOf(read, maxBytes - total)
                    out.write(buffer, 0, toWrite)
                    total += toWrite
                    if (total >= maxBytes) break
                }
                out.toString("UTF-8")
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP GET failed for $urlString: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun stripHtml(raw: String): String {
        val noTags = raw.replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(noTags)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    }

    private companion object {
        const val TAG = "LiveWebSearchService"
        const val TOTAL_SEARCH_TIMEOUT_MS = 5_500L
        const val MAX_RESULTS = 7
        val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    }
}
