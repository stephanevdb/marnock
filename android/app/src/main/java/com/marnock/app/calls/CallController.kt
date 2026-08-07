package com.marnock.app.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CallController(private val context: Context) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    private val _state = MutableSharedFlow<CallState>(replay = 1, extraBufferCapacity = 8)
    val state: SharedFlow<CallState> = _state

    @Volatile private var lastNumber: String? = null
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
            ) return
            telephony.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        publish(state)
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    lastNumber = phoneNumber
                    publish(state)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
        publish(telephony.callState)
    }

    private fun publish(state: Int) {
        val mapped = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
            else -> "idle"
        }
        _state.tryEmit(
            CallState(
                state = mapped,
                number = lastNumber,
                name = null,
                ts = System.currentTimeMillis()
            )
        )
    }

    fun history(limit: Int = 50): List<HistoryEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val out = mutableListOf<HistoryEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            ),
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                val type = when (c.getInt(5)) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    CallLog.Calls.REJECTED_TYPE -> "rejected"
                    else -> "other"
                }
                out += HistoryEntry(
                    id = c.getLong(0).toString(),
                    number = c.getString(1).orEmpty(),
                    name = c.getString(2),
                    date = c.getLong(3),
                    duration = c.getLong(4),
                    type = type
                )
                n++
            }
        }
        return out
    }

    fun dial(number: String) {
        lastNumber = number
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        _state.tryEmit(CallState("dialing", number, null, System.currentTimeMillis()))
    }

    fun answer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                telecom.acceptRingingCall()
            } catch (_: Exception) {
            }
        }
    }

    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                telecom.endCall()
            } catch (_: Exception) {
            }
        }
    }

    data class CallState(val state: String, val number: String?, val name: String?, val ts: Long) {
        fun toJson() = buildJsonObject {
            put("state", state)
            put("number", number ?: "")
            put("name", name ?: "")
            put("ts", ts)
        }
    }

    data class HistoryEntry(
        val id: String,
        val number: String,
        val name: String?,
        val date: Long,
        val duration: Long,
        val type: String
    ) {
        fun toJson() = buildJsonObject {
            put("id", id)
            put("number", number)
            put("name", name ?: "")
            put("date", date)
            put("duration", duration)
            put("type", type)
        }
    }
}
