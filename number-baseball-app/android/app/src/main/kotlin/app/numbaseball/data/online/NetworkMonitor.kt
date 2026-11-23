package app.numbaseball.data.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the device has a validated internet connection (02 / 04 offline states). */
interface NetworkMonitor {
    val online: StateFlow<Boolean>
}

class AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(current())
    override val online: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                state.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    }

    private fun current(): Boolean {
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
