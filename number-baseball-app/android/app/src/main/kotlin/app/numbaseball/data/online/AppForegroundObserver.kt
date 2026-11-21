package app.numbaseball.data.online

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Forwards process start/stop (ProcessLifecycleOwner) to the online client, which keeps a short background grace. */
class AppForegroundObserver(private val client: OnlineClient) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = client.setAppInForeground(true)
    override fun onStop(owner: LifecycleOwner) = client.setAppInForeground(false)
}
