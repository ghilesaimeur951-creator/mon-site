package com.cyberwatch.mobile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Feed(
    @SerialName("generated_at") val generatedAt: String = "",
    val items: List<Article> = emptyList()
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    val summary: String = "",
    val url: String,
    val source: String,
    @SerialName("published_at") val publishedAt: String = "",
    val category: String = "Actualité",
    val severity: String = "Info"
)
