package com.marnock.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.marnock.app.sync.SyncBus

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        messages.forEach { msg ->
            SyncBus.emitSmsReceived(
                address = msg.displayOriginatingAddress.orEmpty(),
                body = msg.messageBody.orEmpty(),
                date = msg.timestampMillis
            )
        }
    }
}
