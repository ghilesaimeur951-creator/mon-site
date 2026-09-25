package com.cyberwatch.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationWorker.ensureChannel(this)
        NotificationWorker.schedule(this)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CyberWatchTheme {
                CyberWatchApp()
            }
        }
    }
}

@Composable
private fun CyberWatchTheme(content: @Composable () -> Unit) {
    val scheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF00E5A8),
            secondary = androidx.compose.ui.graphics.Color(0xFF7DD3FC)
        )
    } else {
        lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF006B53),
            secondary = androidx.compose.ui.graphics.Color(0xFF00658A)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun CyberWatchApp() {
    val repository = remember { FeedRepository() }
    val scope = rememberCoroutineScope()

    var feed by remember { mutableStateOf<Feed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tout") }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            try {
                feed = withContext(Dispatchers.IO) { repository.load() }
            } catch (e: Exception) {
                error = e.message ?: "Impossible de charger la veille."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val categories = listOf("Tout", "Alerte", "Vulnérabilité", "Menace", "Incident", "Actualité")
    val filtered = feed?.items.orEmpty().filter {
        val categoryMatches = category == "Tout" || it.category == category
        val needle = query.trim()
        val textMatches = needle.isBlank() ||
            it.title.contains(needle, ignoreCase = true) ||
            it.summary.contains(needle, ignoreCase = true) ||
            it.source.contains(needle, ignoreCase = true)
        categoryMatches && textMatches
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "CyberWatch",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Veille cybersécurité",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { refresh() }, enabled = !loading) {
                    Text("Actualiser")
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Rechercher : CVE, éditeur, ransomware…") }
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { item ->
                    FilterChip(
                        selected = item == category,
                        onClick = { category = item },
                        label = { Text(item) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                loading && feed == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && feed == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("La veille n'est pas disponible.", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(error ?: "")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { refresh() }) { Text("Réessayer") }
                    }
                }
                else -> {
                    val generated = feed?.generatedAt.orEmpty()
                    Text(
                        buildString {
                            append(filtered.size)
                            append(" infos")
                            if (generated.isNotBlank()) {
                                append(" · mise à jour ")
                                append(formatDate(generated))
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aucun résultat.")
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { article ->
                                ArticleCard(article)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = { uriHandler.openUri(article.url) }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    article.source,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    article.severity,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(7.dp))
            Text(
                article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (article.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    article.summary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(9.dp))
            Text(
                article.category + " · " + formatDate(article.publishedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(value: String): String {
    if (value.isBlank()) return "date inconnue"
    return try {
        val date = OffsetDateTime.parse(value)
        val formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.FRENCH)
        date.format(formatter)
    } catch (_: Exception) {
        value.take(16)
    }
}
