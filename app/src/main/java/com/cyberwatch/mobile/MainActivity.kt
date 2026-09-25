package com.cyberwatch.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CyberMint = Color(0xFF38E8B0)
private val CyberSky = Color(0xFF74C8FF)
private val CyberNavy = Color(0xFF07111F)
private val CyberNavySurface = Color(0xFF0C1928)
private val CyberNavyRaised = Color(0xFF102235)
private val CyberText = Color(0xFFF4F8FC)
private val CyberMuted = Color(0xFF9FB1C4)
private val SeverityCritical = Color(0xFFFF6B78)
private val SeverityHigh = Color(0xFFFFB95E)
private val SeverityMedium = Color(0xFF74C8FF)
private val SeverityInfo = Color(0xFF8FA6BA)

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
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = CyberMint,
            onPrimary = CyberNavy,
            secondary = CyberSky,
            background = CyberNavy,
            onBackground = CyberText,
            surface = CyberNavySurface,
            onSurface = CyberText,
            surfaceVariant = CyberNavyRaised,
            onSurfaceVariant = CyberMuted,
            outline = Color(0xFF30465C),
            error = SeverityCritical
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006B53),
            onPrimary = Color.White,
            secondary = Color(0xFF00658A),
            background = Color(0xFFF5F8FA),
            onBackground = Color(0xFF10202E),
            surface = Color.White,
            onSurface = Color(0xFF10202E),
            surfaceVariant = Color(0xFFEAF0F4),
            onSurfaceVariant = Color(0xFF526474),
            outline = Color(0xFFB8C5CF),
            error = Color(0xFFB3261E)
        )
    }

    val typography = Typography(
        headlineLarge = TextStyle(
            fontSize = 31.sp,
            lineHeight = 35.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.7).sp
        ),
        headlineSmall = TextStyle(
            fontSize = 22.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold
        ),
        titleLarge = TextStyle(
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelLarge = TextStyle(
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content
    )
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
    val allItems = feed?.items.orEmpty()
    val filtered = allItems.filter {
        val categoryMatches = category == "Tout" || it.category == category
        val needle = query.trim()
        val textMatches = needle.isBlank() ||
            it.title.contains(needle, ignoreCase = true) ||
            it.summary.contains(needle, ignoreCase = true) ||
            it.source.contains(needle, ignoreCase = true)
        categoryMatches && textMatches
    }

    val criticalCount = allItems.count { it.severity.equals("Critique", ignoreCase = true) }
    val elevatedCount = allItems.count { it.severity.equals("Élevée", ignoreCase = true) }
    val sourceCount = allItems.map { it.source }.distinct().size

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.035f)
                        )
                    )
                )
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    HeroHeader(
                        loading = loading,
                        hasFeed = feed != null,
                        onRefresh = { refresh() }
                    )
                }

                if (feed != null) {
                    item {
                        StatsRow(
                            total = allItems.size,
                            critical = criticalCount,
                            elevated = elevatedCount,
                            sources = sourceCount
                        )
                    }
                }

                item {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClear = { query = "" }
                    )
                }

                item {
                    CategoryFilters(
                        categories = categories,
                        selected = category,
                        allItems = allItems,
                        onSelected = { category = it }
                    )
                }

                if (loading && feed != null) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        )
                    }
                }

                when {
                    loading && feed == null -> {
                        item { LoadingState() }
                    }

                    error != null && feed == null -> {
                        item {
                            ErrorState(
                                message = error ?: "Erreur réseau",
                                onRetry = { refresh() }
                            )
                        }
                    }

                    else -> {
                        item {
                            FeedSectionHeader(
                                count = filtered.size,
                                generatedAt = feed?.generatedAt.orEmpty(),
                                hasFilter = category != "Tout" || query.isNotBlank()
                            )
                        }

                        if (error != null) {
                            item {
                                InlineWarning("Actualisation impossible. Les dernières données restent affichées.")
                            }
                        }

                        if (filtered.isEmpty()) {
                            item { EmptyState(query = query, category = category) }
                        } else {
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
private fun HeroHeader(
    loading: Boolean,
    hasFeed: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(13.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "CyberWatch",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Threat intelligence · mobile",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualiser"
                    )
                }
            }

            Spacer(Modifier.height(15.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasFeed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when {
                        loading && !hasFeed -> "CONNEXION AUX SOURCES"
                        loading -> "ACTUALISATION EN COURS"
                        hasFeed -> "VEILLE ACTIVE"
                        else -> "VEILLE EN ATTENTE"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasFeed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.7.sp
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    total: Int,
    critical: Int,
    elevated: Int,
    sources: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = total.toString(),
            label = "signaux",
            accent = MaterialTheme.colorScheme.primary
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = (critical + elevated).toString(),
            label = "prioritaires",
            accent = if (critical > 0) SeverityCritical else SeverityHigh
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = sources.toString(),
            label = "sources",
            accent = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        placeholder = {
            Text(
                "CVE, éditeur, ransomware…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Effacer la recherche"
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun CategoryFilters(
    categories: List<String>,
    selected: String,
    allItems: List<Article>,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { item ->
            val count = if (item == "Tout") allItems.size else allItems.count { it.category == item }
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = {
                    Text(
                        if (count > 0) "$item  $count" else item,
                        maxLines = 1
                    )
                },
                shape = RoundedCornerShape(13.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = item == selected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                )
            )
        }
    }
}

@Composable
private fun FeedSectionHeader(
    count: Int,
    generatedAt: String,
    hasFilter: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (hasFilter) "Résultats" else "Derniers signaux",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "$count élément" + if (count > 1) "s" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (generatedAt.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Text(
                    "MAJ " + formatDate(generatedAt),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article) {
    val uriHandler = LocalUriHandler.current
    val severityColor = severityColor(article.severity)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
        ),
        onClick = { uriHandler.openUri(article.url) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceMark(article.source)

                Spacer(Modifier.width(10.dp))

                Text(
                    article.source,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                SeverityBadge(
                    severity = article.severity,
                    color = severityColor
                )
            }

            Spacer(Modifier.height(13.dp))

            Text(
                article.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (article.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    article.summary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(11.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(severityColor)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    article.category.uppercase(Locale.FRENCH),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.45.sp
                )

                Spacer(Modifier.width(9.dp))

                Text(
                    "•",
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.width(9.dp))

                Text(
                    formatDate(article.publishedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = "Ouvrir la source",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceMark(source: String) {
    val initials = source
        .replace(Regex("[^A-Za-z0-9À-ÿ ]"), " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase(Locale.FRENCH) }
        .ifBlank { "CW" }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SeverityBadge(
    severity: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Text(
            severity.uppercase(Locale.FRENCH),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            letterSpacing = 0.35.sp
        )
    }
}

@Composable
private fun LoadingState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                "Synchronisation de la veille",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Connexion aux sources cyber et préparation du flux.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Text(
                "Connexion indisponible",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(7.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Réessayer")
            }
        }
    }
}

@Composable
private fun InlineWarning(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SeverityHigh.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SeverityHigh.copy(alpha = 0.22f))
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            color = SeverityHigh
        )
    }
}

@Composable
private fun EmptyState(
    query: String,
    category: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Aucun signal trouvé",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    query.isNotBlank() -> "Essaie un autre mot-clé ou retire un filtre."
                    category != "Tout" -> "Aucune entrée ne correspond à cette catégorie."
                    else -> "Le flux est vide pour le moment."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun severityColor(severity: String): Color = when (severity.lowercase(Locale.FRENCH)) {
    "critique" -> SeverityCritical
    "élevée", "elevee", "élevé", "eleve" -> SeverityHigh
    "moyenne", "moyen" -> SeverityMedium
    else -> SeverityInfo
}

private fun formatDate(value: String): String {
    if (value.isBlank()) return "date inconnue"
    return try {
        val date = OffsetDateTime.parse(value)
        val formatter = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.FRENCH)
        date.format(formatter)
    } catch (_: Exception) {
        value.take(16)
    }
}
