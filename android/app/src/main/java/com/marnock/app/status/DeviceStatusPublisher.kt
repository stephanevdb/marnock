package com.marnock.app.status

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import com.marnock.app.wifi.WifiInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceStatusPublisher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val wifi: WifiInfoProvider,
    private val send: (Envelope) -> Unit
) {
    fun start() {
        scope.launch {
            while (isActive) {
                send(snapshot())
                delay(15_000)
            }
        }
    }

    fun snapshot(): Envelope {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
            || stickyCharging()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val wifiUp = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ssid = if (wifiUp) wifi.currentSsid() else ""
        val hotspot = wifi.isHotspotActive()

        return Envelope(
            MessageTypes.DEVICE_STATUS,
            payload = buildJsonObject {
                put("battery", pct)
                put("charging", charging)
                put("wifiSsid", ssid)
                put("cellular", cell)
                put("hotspotActive", hotspot)
            }
        )
    }

    private fun stickyCharging(): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
