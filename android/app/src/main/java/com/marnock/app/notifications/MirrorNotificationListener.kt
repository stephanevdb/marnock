package com.marnock.app.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.marnock.app.sync.SyncBus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MirrorNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        SyncBus.notificationListener = this
    }

    override fun onListenerDisconnected() {
        if (SyncBus.notificationListener === this) {
            SyncBus.notificationListener = null
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val actions = buildJsonArray {
            n.actions?.forEachIndexed { index, action ->
                add(
                    buildJsonObject {
                        put("id", action.actionIntent?.creatorPackage + ":" + index)
                        put("title", action.title?.toString().orEmpty())
                        put("allowsReply", action.remoteInputs?.isNotEmpty() == true)
                    }
                )
            }
        }
        SyncBus.emitNotificationPosted(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            actions = actions
        )
        // Keep a short-lived cache for action invocation
        active[sbn.key] = sbn
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        active.remove(sbn.key)
        SyncBus.emitNotificationRemoved(sbn.key)
    }

    fun performAction(key: String, actionId: String, replyText: String?) {
        val sbn = active[key] ?: return
        val actions = sbn.notification.actions ?: return
        val index = actionId.substringAfterLast(":").toIntOrNull() ?: return
        val action = actions.getOrNull(index) ?: return
        if (!replyText.isNullOrEmpty() && action.remoteInputs != null) {
            val intent = Intent()
            val results = Bundle()
            action.remoteInputs?.forEach { ri ->
                results.putCharSequence(ri.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, results)
            try {
                action.actionIntent.send(this, 0, intent)
            } catch (_: Exception) {
            }
        } else {
            try {
                action.actionIntent.send()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private val active = LinkedHashMap<String, StatusBarNotification>()
    }
}
