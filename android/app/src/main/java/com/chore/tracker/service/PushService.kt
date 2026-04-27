package com.chore.tracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.chore.tracker.ChoreApp
import com.chore.tracker.data.DeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushService : FirebaseMessagingService() {

    private val repo get() = (applicationContext as ChoreApp).repo

    override fun onNewToken(token: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repo.session.setFcmToken(token)
            // Register immediately if already logged in; otherwise login flow will flush it.
            if (repo.session.token() != null) {
                try { repo.api.registerDeviceToken(DeviceTokenRequest(token)) }
                catch (_: Throwable) {}
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val isForegrounded = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

        if (isForegrounded) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.refresh() }
            return
        }

        val title = message.notification?.title ?: "Chore update"
        val body = message.notification?.body ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "chore_updates"
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Chore Updates", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }
}
