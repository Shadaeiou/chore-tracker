package com.chore.tracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chore.tracker.data.DataStoreSession
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session
import com.chore.tracker.ui.AvatarCache

class ChoreApp : Application() {
    lateinit var session: Session
        private set
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        session = DataStoreSession(applicationContext)
        repo = Repo(session)
        AvatarCache.configure(repo.api)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    "chore_updates",
                    "Chore Updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
