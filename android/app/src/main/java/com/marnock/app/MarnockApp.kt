package com.marnock.app

import android.app.Application
import com.marnock.app.data.AppSettings
import com.marnock.app.sync.SyncAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MarnockApp : Application() {
    lateinit var settings: AppSettings
        private set

    lateinit var agent: SyncAgent
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var agentReady = false

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        agent = SyncAgent(this, appScope, settings)
    }

    fun ensureAgent(scope: CoroutineScope = appScope) {
        if (!agentReady) {
            agent = SyncAgent(this, scope, settings)
            agent.start()
            agentReady = true
        }
    }
}
