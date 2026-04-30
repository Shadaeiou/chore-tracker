package com.chore.tracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chore.tracker.BuildConfig
import com.chore.tracker.MainActivity
import com.chore.tracker.data.Updater
import java.util.concurrent.TimeUnit

/**
 * Periodic background check for new APKs published to GitHub Releases.
 *
 * When a newer build is found, posts a notification whose tap action opens
 * MainActivity with [MainActivity.EXTRA_AUTO_UPDATE]=true; MainActivity then
 * triggers download + install with no further taps. The system install
 * confirmation dialog is the only thing the user has to OK.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val updater = Updater(applicationContext)
        val info = runCatching { updater.checkForUpdate(BuildConfig.VERSION_CODE) }
            .getOrNull() ?: return Result.success()  // up to date, or transient API hiccup
        postUpdateNotification(applicationContext, info.versionName, info.versionCode)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "update-check"
        const val NOTIF_ID = 9001  // arbitrary stable ID so re-posts replace, not stack

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.HOURS)  // don't pile on right after install
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun postUpdateNotification(context: Context, versionName: String, versionCode: Int) {
            val channelId = "chore_updates"
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Chore Updates", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_AUTO_UPDATE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context, NOTIF_ID, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            nm.notify(
                NOTIF_ID,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(com.chore.tracker.R.drawable.ic_notification)
                    .setContentTitle("App update available")
                    .setContentText("Tap to update to v$versionName (build $versionCode).")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .addAction(
                        com.chore.tracker.R.drawable.ic_notification,
                        "Update now",
                        pi,
                    )
                    .build(),
            )
        }
    }
}
