package com.marnock.app.sync

import com.marnock.app.notifications.MirrorNotificationListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SyncBus {
    @Volatile
    var notificationListener: MirrorNotificationListener? = null

    /** When true, Android skips posting mirrored notifications to Mac. */
    @Volatile
    var quietHours: Boolean = false

    data class NotifPosted(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val actions: JsonArray
    )

    data class SmsIncoming(val address: String, val body: String, val date: Long)

    private val _notifPosted = MutableSharedFlow<NotifPosted>(extraBufferCapacity = 32)
    val notifPosted: SharedFlow<NotifPosted> = _notifPosted

    private val _notifRemoved = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val notifRemoved: SharedFlow<String> = _notifRemoved

    private val _smsIncoming = MutableSharedFlow<SmsIncoming>(extraBufferCapacity = 32)
    val smsIncoming: SharedFlow<SmsIncoming> = _smsIncoming

    fun emitNotificationPosted(
        key: String,
        packageName: String,
        title: String,
        text: String,
        actions: JsonArray
    ) {
        _notifPosted.tryEmit(NotifPosted(key, packageName, title, text, actions))
    }

    fun emitNotificationRemoved(key: String) {
        _notifRemoved.tryEmit(key)
    }

    fun emitSmsReceived(address: String, body: String, date: Long) {
        _smsIncoming.tryEmit(SmsIncoming(address, body, date))
    }

    fun notifPayload(n: NotifPosted) = buildJsonObject {
        put("key", n.key)
        put("packageName", n.packageName)
        put("title", n.title)
        put("text", n.text)
        put("ts", System.currentTimeMillis())
        put("actions", n.actions)
    }
}
