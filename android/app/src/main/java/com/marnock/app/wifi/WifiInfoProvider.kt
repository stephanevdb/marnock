package com.marnock.app.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.marnock.app.protocol.Envelope
import com.marnock.app.protocol.MessageTypes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WifiInfoProvider(private val context: Context) {
    fun currentSsid(): String {
        sanitize(ssidFromTransportInfo())
            ?.let { return it }
        sanitize(ssidFromWifiManager())
            ?.let { return it }
        return ""
    }

    fun isHotspotActive(): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wm) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun hasWifiPermission(): Boolean {
        val nearby = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return nearby || location
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                (Build.VERSION.SDK_INT >= 28 && lm.isLocationEnabled)
        } catch (_: Exception) {
            true
        }
    }

    /** Password is almost never available to third-party apps — return empty honestly. */
    fun infoEnvelope(): Envelope {
        val ssid = currentSsid()
        Log.i(
            "WifiInfoProvider",
            "ssid='$ssid' perm=${hasWifiPermission()} locOn=${isLocationEnabled()} " +
                "transport=${ssidFromTransportInfo()} legacy=${ssidFromWifiManager()}"
        )
        val note = when {
            ssid.isNotEmpty() ->
                "Password is not exposed by Android to third-party apps"
            !hasWifiPermission() ->
                "Grant Nearby devices and Location permission in the Marnock app"
            !isLocationEnabled() ->
                "Turn on Location (system setting) — Android requires it to read the Wi‑Fi name"
            else ->
                "Connected SSID still unavailable from this device"
        }
        return Envelope(
            MessageTypes.WIFI_INFO,
            payload = buildJsonObject {
                put("ssid", ssid)
                put("password", "")
                put("hasPassword", false)
                put("hotspotActive", isHotspotActive())
                put("note", note)
            }
        )
    }

    private fun ssidFromTransportInfo(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val info = caps.transportInfo as? WifiInfo
                info?.ssid
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ssidFromWifiManager(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.connectionInfo?.ssid
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitize(raw: String?): String? {
        val s = raw?.trim()?.trim('"').orEmpty()
        if (s.isEmpty() || s == "<unknown ssid>" || s.equals("unknown ssid", ignoreCase = true)) {
            return null
        }
        return s
    }
}
