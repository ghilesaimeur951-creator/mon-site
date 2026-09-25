package com.cyberwatch.mobile

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class FeedRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): Feed {
        val request = Request.Builder()
            .url(FEED_URL)
            .header("User-Agent", "CyberWatch-Android/0.1")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP " + response.code)
            val body = response.body?.string() ?: throw IOException("Réponse vide")
            return json.decodeFromString<Feed>(body)
        }
    }

    companion object {
        const val FEED_URL =
            "https://raw.githubusercontent.com/ghilesaimeur951-creator/mon-site/main/data/feed.json"
    }
}
