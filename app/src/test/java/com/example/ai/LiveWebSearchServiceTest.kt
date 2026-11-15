package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LiveWebSearchServiceTest {

    private val service = LiveWebSearchService()

    @Test
    fun shouldPreFetchLiveWeb_detectsLiveAndUrlPrompts() {
        assertTrue(service.shouldPreFetchLiveWeb("What is the latest news today?"))
        assertTrue(service.shouldPreFetchLiveWeb("Search the web for Kotlin 2.2 release notes"))
        assertTrue(service.shouldPreFetchLiveWeb("Summarize https://developer.android.com/ai"))
        assertFalse(service.shouldPreFetchLiveWeb("Write a haiku about mountains"))
    }

    @Test
    fun buildSearchQuery_stripsUrlsAndCommandPrefixes() {
        val cleaned = service.buildSearchQuery("Please search the web for SpaceX Starship launch updates https://example.com")
        assertEquals("SpaceX Starship launch updates", cleaned)
    }

    @Test
    fun parseGoogleNewsRss_extractsArticlesCleanly() {
        val sampleRss = """
            <rss version="2.0">
              <channel>
                <item>
                  <title>Android 16 Beta Released - TechDaily</title>
                  <link>https://example.com/android-16</link>
                  <pubDate>Sun, 15 Nov 2026 10:00:00 GMT</pubDate>
                  <description>&lt;p&gt;Google released the latest beta update today.&lt;/p&gt;</description>
                  <source url="https://example.com">TechDaily</source>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = service.parseGoogleNewsRss(sampleRss)
        assertEquals(1, parsed.size)
        assertEquals("Android 16 Beta Released - TechDaily", parsed[0].title)
        assertEquals("https://example.com/android-16", parsed[0].url)
        assertEquals("TechDaily", parsed[0].source)
        assertTrue(parsed[0].snippet.contains("latest beta update"))
    }

    @Test
    fun parseDuckDuckGoHtml_resolvesRedirectUrlsAndSnippets() {
        val sampleHtml = """
            <div class="result">
              <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fhome.html">Kotlin Docs</a>
              <a class="result__snippet">Official Kotlin programming language documentation.</a>
            </div>
        """.trimIndent()

        val parsed = service.parseDuckDuckGoHtml(sampleHtml)
        assertEquals(1, parsed.size)
        assertEquals("Kotlin Docs", parsed[0].title)
        assertEquals("https://kotlinlang.org/docs/home.html", parsed[0].url)
        assertEquals("kotlinlang.org", parsed[0].source)
    }

    @Test
    fun parseWikipediaJson_extractsArticles() {
        val sampleJson = """
            {
              "query": {
                "search": [
                  {
                    "title": "James Webb Space Telescope",
                    "snippet": "The <span class=\"searchmatch\">James Webb</span> Space Telescope is a space telescope."
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = service.parseWikipediaJson(sampleJson)
        assertEquals(1, parsed.size)
        assertEquals("James Webb Space Telescope — Wikipedia", parsed[0].title)
        assertEquals("https://en.wikipedia.org/wiki/James_Webb_Space_Telescope", parsed[0].url)
        assertTrue(parsed[0].snippet.contains("James Webb Space Telescope"))
    }
}
