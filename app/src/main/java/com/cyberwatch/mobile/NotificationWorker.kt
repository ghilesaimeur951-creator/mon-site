package com.cyberwatch.mobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            ensureChannel(applicationContext)
            val feed = FeedRepository().load()
            if (feed.items.isEmpty()) return@withContext Result.success()

            val prefs = applicationContext.getSharedPreferences("cyberwatch", Context.MODE_PRIVATE)
            val previous = prefs.getString("last_seen_id", null)
            val newest = feed.items.first().id

            if (previous == null) {
                prefs.edit().putString("last_seen_id", newest).apply()
                return@withContext Result.success()
            }

            val fresh = feed.items.takeWhile { it.id != previous }.take(3)
            fresh.reversed().forEachIndexed { index, article ->
                showNotification(applicationContext, article, index)
            }

            prefs.edit().putString("last_seen_id", newest).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val CHANNEL_ID = "cyberwatch_alerts"
        private const val WORK_NAME = "cyberwatch_feed_watch"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Alertes cybersécurité",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Nouvelles informations détectées par CyberWatch"
                    }
                )
            }
        }

        private fun showNotification(context: Context, article: Article, index: Int) {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(article.source + " · " + article.severity)
                .setContentText(article.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(article.title))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context)
                .notify(article.id.hashCode() + index, notification)
        }
    }
}
