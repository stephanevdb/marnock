package com.marnock.app.protocol

object MessageTypes {
    const val PAIR_HELLO = "pair.hello"
    const val PAIR_COMPLETE = "pair.complete"
    const val PING = "ping"
    const val PONG = "pong"
    const val SESSION_FRAME = "session.frame"
    const val CLIPBOARD_SET = "clipboard.set"
    const val CLIPBOARD_CHANGED = "clipboard.changed"
    const val NOTIFICATION_POSTED = "notification.posted"
    const val NOTIFICATION_REMOVED = "notification.removed"
    const val NOTIFICATION_ACTION = "notification.action"
    const val SMS_THREADS = "sms.threads"
    const val SMS_MESSAGES = "sms.messages"
    const val SMS_SEND = "sms.send"
    const val SMS_RECEIVED = "sms.received"
    const val SMS_THREADS_REQUEST = "sms.threads.request"
    const val SMS_MESSAGES_REQUEST = "sms.messages.request"
    const val CALL_STATE = "call.state"
    const val CALL_HISTORY = "call.history"
    const val CALL_HISTORY_REQUEST = "call.history.request"
    const val CALL_DIAL = "call.dial"
    const val CALL_ANSWER = "call.answer"
    const val CALL_REJECT = "call.reject"
    const val RELAY_REGISTER = "relay.register"
    const val RELAY_FORWARD = "relay.forward"

    // File transfer
    const val FILE_OFFER = "file.offer"
    const val FILE_ACCEPT = "file.accept"
    const val FILE_CHUNK = "file.chunk"
    const val FILE_COMPLETE = "file.complete"
    const val FILE_CANCEL = "file.cancel"

    // Media / find / status
    const val MEDIA_COMMAND = "media.command"
    const val MEDIA_STATE = "media.state"
    const val FIND_RING = "find.ring"
    const val FIND_STOP = "find.stop"
    const val DEVICE_STATUS = "device.status"

    // Links / wifi / photos / prefs
    const val LINK_OPEN = "link.open"
    const val WIFI_INFO = "wifi.info"
    const val WIFI_REQUEST = "wifi.request"
    const val PHOTOS_LIST_REQUEST = "photos.list.request"
    const val PHOTOS_LIST = "photos.list"
    const val PHOTOS_GET = "photos.get"
    const val PREFS_QUIET = "prefs.quiet"
}

const val SERVICE_TYPE = "_marnock._tcp."
