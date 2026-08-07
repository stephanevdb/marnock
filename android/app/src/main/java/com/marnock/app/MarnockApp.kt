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
        // Always bind SyncAgent to application scope so FGS teardown cannot cancel clipboard/sync jobs.
        agent = SyncAgent(this, appScope, settings)
        agent.start()
        agentReady = true
    }

    fun ensureAgent(@Suppress("UNUSED_PARAMETER") scope: CoroutineScope = appScope) {
        if (!agentReady) {
            agent = SyncAgent(this, appScope, settings)
            agent.start()
            agentReady = true
        }
    }
}
