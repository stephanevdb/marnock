package com.marnock.app.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Framing {
    fun encode(jsonUtf8: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(4 + jsonUtf8.size).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(jsonUtf8.size)
        buf.put(jsonUtf8)
        return buf.array()
    }

    fun decode(frame: ByteArray): ByteArray {
        if (frame.size < 4) return frame
        val n = ByteBuffer.wrap(frame, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        if (n in 0 until frame.size && n + 4 == frame.size) {
            return frame.copyOfRange(4, frame.size)
        }
        // Also accept raw JSON text frames without length prefix.
        return frame
    }
}
