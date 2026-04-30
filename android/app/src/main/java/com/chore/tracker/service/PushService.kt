package com.chore.tracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
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
        Log.d("PushService", "onNewToken: token received (length ${token.length})")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            repo.session.setFcmToken(token)
            if (repo.session.token() != null) {
                try {
                    repo.api.registerDeviceToken(DeviceTokenRequest(token))
                    Log.d("PushService", "onNewToken: registered with backend OK")
                } catch (t: Throwable) {
                    Log.w("PushService", "onNewToken: backend registration failed: ${t.message}")
                }
            } else {
                Log.d("PushService", "onNewToken: not logged in yet, queued for login")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(
            "PushService",
            "onMessageReceived: action=${message.data["action"]}, " +
                "type=${message.data["type"]}, notif=${message.notification?.title}",
        )
        // Silent refresh: backend fans out `data: { action: "refresh" }` on any
        // edit so other household devices stay in sync without polling. Trigger
        // a refresh and don't surface a notification regardless of foreground.
        if (message.data["action"] == "refresh") {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.refresh() }
            return
        }

        val isForegrounded = ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)

        // Comment pushes are data-only so we can attach an inline-reply +
        // quick-react action. Build the notification ourselves; tapping the
        // body still opens the app (which then navigates to the Activity tab
        // and refreshes).
        if (message.data["type"] == "comment") {
            // Refresh in the background so reactions/comments arrive in-app,
            // but always surface the notification too so the user can act
            // without switching apps.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.refresh() }
            showCommentNotification(
                completionId = message.data["completionId"].orEmpty(),
                taskName = message.data["taskName"].orEmpty(),
                actorName = message.data["actorName"] ?: "Someone",
                text = message.data["text"].orEmpty(),
            )
            return
        }

        // App-update push fired by the GitHub release webhook → worker. Skip if
        // the payload's versionCode isn't actually newer than what we're
        // running (race condition on a freshly-installed APK), then post a
        // sock-iconed notification whose tap opens MainActivity in
        // auto-update mode.
        if (message.data["type"] == "update") {
            val versionCode = message.data["versionCode"]?.toIntOrNull() ?: return
            val versionName = message.data["versionName"].orEmpty()
            if (versionCode <= com.chore.tracker.BuildConfig.VERSION_CODE) return
            showUpdateNotification(versionName, versionCode)
            return
        }

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
                .setSmallIcon(com.chore.tracker.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Comment-thread push with inline-reply and quick-react actions. */
    private fun showCommentNotification(
        completionId: String,
        taskName: String,
        actorName: String,
        text: String,
    ) {
        if (completionId.isEmpty()) return  // malformed payload; nothing actionable
        val channelId = "chore_updates"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Chore Updates", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        // Stable per-completion ID so multiple comments on the same chore
        // collapse onto one notification entry instead of stacking.
        val notifId = ("comment:$completionId").hashCode()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val tapIntent = PendingIntent.getActivity(
            this, notifId, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyInput = RemoteInput.Builder(CommentActionsReceiver.KEY_REPLY_TEXT)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            com.chore.tracker.R.drawable.ic_notification,
            "Reply",
            CommentActionsReceiver.replyPendingIntent(this, completionId, notifId),
        )
            .addRemoteInput(replyInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val reactAction = NotificationCompat.Action.Builder(
            com.chore.tracker.R.drawable.ic_notification,
            "👍",
            CommentActionsReceiver.reactPendingIntent(this, completionId, notifId, "👍"),
        )
            .setShowsUserInterface(false)
            .build()

        nm.notify(
            notifId,
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(com.chore.tracker.R.drawable.ic_notification)
                .setContentTitle("$actorName on \"$taskName\"")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tapIntent)
                .addAction(replyAction)
                .addAction(reactAction)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Notification fired when the GitHub release webhook tells the worker
     *  there's a newer build. Tap → MainActivity#EXTRA_AUTO_UPDATE which
     *  downloads + installs without further user navigation. */
    private fun showUpdateNotification(versionName: String, versionCode: Int) {
        val channelId = "chore_updates"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Chore Updates", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        // Stable ID so re-fired update pushes replace, not stack.
        val notifId = 9001
        val tapIntent = android.content.Intent(this, com.chore.tracker.MainActivity::class.java).apply {
            putExtra(com.chore.tracker.MainActivity.EXTRA_AUTO_UPDATE, true)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(
            notifId,
            NotificationCompat.Builder(this, channelId)
                .setSmallIcon(com.chore.tracker.R.drawable.ic_notification)
                .setContentTitle("App update available")
                .setContentText("Tap to update to v$versionName (build $versionCode).")
                .setContentIntent(pi)
                .addAction(
                    com.chore.tracker.R.drawable.ic_notification,
                    "Update now",
                    pi,
                )
                .setAutoCancel(true)
                .build(),
        )
    }
}
