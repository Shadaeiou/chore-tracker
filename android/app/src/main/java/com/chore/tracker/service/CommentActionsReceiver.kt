package com.chore.tracker.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.chore.tracker.ChoreApp
import com.chore.tracker.data.CommentRequest
import com.chore.tracker.data.ReactionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the action buttons on comment-thread push notifications:
 *
 *   • [ACTION_REPLY] — read the inline RemoteInput text and POST a comment
 *   • [ACTION_REACT] — read the [EXTRA_EMOJI] and POST a reaction
 *
 * On either path, finish by canceling the originating notification so the
 * user sees their action take effect; the next FCM refresh fan-out updates
 * the in-app activity feed.
 */
class CommentActionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val completionId = intent.getStringExtra(EXTRA_COMPLETION_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val app = context.applicationContext as ChoreApp
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val results = RemoteInput.getResultsFromIntent(intent)
                        val text = results?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
                        if (!text.isNullOrEmpty()) {
                            runCatching {
                                app.repo.api.commentOnCompletion(completionId, CommentRequest(text))
                            }.onFailure {
                                Log.w("CommentActionsReceiver", "reply failed: ${it.message}")
                            }
                        }
                    }
                    ACTION_REACT -> {
                        val emoji = intent.getStringExtra(EXTRA_EMOJI) ?: return@launch
                        runCatching {
                            app.repo.api.reactToCompletion(completionId, ReactionRequest(emoji))
                        }.onFailure {
                            Log.w("CommentActionsReceiver", "react failed: ${it.message}")
                        }
                    }
                }
                if (notifId != 0) NotificationManagerCompat.from(context).cancel(notifId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.chore.tracker.ACTION_COMMENT_REPLY"
        const val ACTION_REACT = "com.chore.tracker.ACTION_COMMENT_REACT"
        const val EXTRA_COMPLETION_ID = "completionId"
        const val EXTRA_NOTIF_ID = "notifId"
        const val EXTRA_EMOJI = "emoji"
        const val KEY_REPLY_TEXT = "key_reply_text"

        /** Mutable PendingIntent that the system can fill RemoteInput results into. */
        fun replyPendingIntent(
            context: Context,
            completionId: String,
            notifId: Int,
        ): PendingIntent {
            val intent = Intent(context, CommentActionsReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_COMPLETION_ID, completionId)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            return PendingIntent.getBroadcast(
                context,
                ("reply:$completionId").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }

        fun reactPendingIntent(
            context: Context,
            completionId: String,
            notifId: Int,
            emoji: String,
        ): PendingIntent {
            val intent = Intent(context, CommentActionsReceiver::class.java).apply {
                action = ACTION_REACT
                putExtra(EXTRA_COMPLETION_ID, completionId)
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_EMOJI, emoji)
            }
            return PendingIntent.getBroadcast(
                context,
                ("react:$completionId:$emoji").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
