package com.marnock.app.sms

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SmsRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val changes: SharedFlow<Unit> = _changes

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _changes.tryEmit(Unit)
        }
    }

    fun startWatching() {
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
    }

    fun stopWatching() {
        resolver.unregisterContentObserver(observer)
    }

    fun threads(limit: Int = 50): List<JsonThread> {
        val out = mutableListOf<JsonThread>()
        val uri = Telephony.Sms.Conversations.CONTENT_URI
        resolver.query(
            uri,
            arrayOf(
                Telephony.Sms.Conversations.THREAD_ID,
                Telephony.Sms.Conversations.SNIPPET,
                Telephony.Sms.Conversations.MESSAGE_COUNT
            ),
            null,
            null,
            "${Telephony.Sms.DEFAULT_SORT_ORDER}"
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                val threadId = c.getLong(0)
                val snippet = c.getString(1).orEmpty()
                val address = latestAddress(threadId)
                val date = latestDate(threadId)
                out += JsonThread(
                    threadId = threadId.toString(),
                    address = address,
                    contactName = resolveContactName(address),
                    snippet = snippet,
                    date = date,
                    unread = 0
                )
                n++
            }
        }
        return out.sortedByDescending { it.date }
    }

    fun messages(threadId: String, limit: Int = 100): List<JsonMessage> {
        val out = mutableListOf<JsonMessage>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                val typeInt = c.getInt(5)
                out += JsonMessage(
                    id = c.getLong(0).toString(),
                    threadId = c.getLong(1).toString(),
                    address = c.getString(2).orEmpty(),
                    body = c.getString(3).orEmpty(),
                    date = c.getLong(4),
                    type = when (typeInt) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> "inbox"
                        Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
                        Telephony.Sms.MESSAGE_TYPE_DRAFT -> "draft"
                        else -> "other"
                    }
                )
                n++
            }
        }
        return out.reversed()
    }

    fun send(address: String, body: String) {
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(body)
        if (parts.size == 1) {
            sms.sendTextMessage(address, null, body, null, null)
        } else {
            sms.sendMultipartTextMessage(address, null, parts, null, null)
        }
    }

    private fun latestAddress(threadId: Long): String {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use {
            if (it.moveToFirst()) return it.getString(0).orEmpty()
        }
        return ""
    }

    private fun latestDate(threadId: Long): Long {
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return 0L
    }

    private fun resolveContactName(address: String): String? {
        if (address.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return try {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: SecurityException) {
            null
        }
    }

    data class JsonThread(
        val threadId: String,
        val address: String,
        val contactName: String?,
        val snippet: String,
        val date: Long,
        val unread: Int
    ) {
        fun toJson() = buildJsonObject {
            put("threadId", threadId)
            put("address", address)
            put("contactName", contactName ?: "")
            put("snippet", snippet)
            put("date", date)
            put("unread", unread)
        }
    }

    data class JsonMessage(
        val id: String,
        val threadId: String,
        val address: String,
        val body: String,
        val date: Long,
        val type: String
    ) {
        fun toJson() = buildJsonObject {
            put("id", id)
            put("threadId", threadId)
            put("address", address)
            put("body", body)
            put("date", date)
            put("type", type)
        }
    }
}
