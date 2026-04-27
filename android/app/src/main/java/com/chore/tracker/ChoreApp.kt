package com.chore.tracker

import android.app.Application
import com.chore.tracker.data.Repo
import com.chore.tracker.data.Session

class ChoreApp : Application() {
    lateinit var session: Session
        private set
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(applicationContext)
        repo = Repo(session)
    }
}
