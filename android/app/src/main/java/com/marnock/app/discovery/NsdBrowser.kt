package com.marnock.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.marnock.app.protocol.SERVICE_TYPE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiscoveredPeer(
    val name: String,
    val host: String,
    val port: Int,
    val deviceId: String,
    val serviceName: String = ""
)

class NsdBrowser(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolving = mutableSetOf<String>()
    private val serviceToDeviceId = mutableMapOf<String, String>()

    fun start() {
        stop()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val type = serviceInfo.serviceType.orEmpty().lowercase()
                // Accept marnock Bonjour/NSD advertisements only.
                if (!type.contains("marnock")) return
                val key = serviceInfo.serviceName
                if (!resolving.add(key)) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(key)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        resolving.remove(key)
                        val host = resolved.host?.hostAddress ?: return
                        val attrs = resolved.attributes
                        val deviceId = attrs["deviceId"]?.toString(Charsets.UTF_8)
                            ?: resolved.serviceName
                        val displayName = attrs["name"]?.toString(Charsets.UTF_8)
                            ?: resolved.serviceName
                        val peer = DiscoveredPeer(
                            name = displayName,
                            host = host,
                            port = resolved.port,
                            deviceId = deviceId,
                            serviceName = resolved.serviceName
                        )
                        serviceToDeviceId[resolved.serviceName] = deviceId
                        _peers.value = (_peers.value.filterNot { it.deviceId == peer.deviceId } + peer)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val deviceId = serviceToDeviceId.remove(serviceInfo.serviceName)
                _peers.value = if (deviceId != null) {
                    _peers.value.filterNot { it.deviceId == deviceId }
                } else {
                    _peers.value.filterNot { it.serviceName == serviceInfo.serviceName }
                }
            }
        }
        discoveryListener = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        discoveryListener?.let {
            runCatching { nsd.stopServiceDiscovery(it) }
        }
        discoveryListener = null
        serviceToDeviceId.clear()
        _peers.value = emptyList()
    }
}
