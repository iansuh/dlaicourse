package app.numbaseball.data.online

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Forwards process start/stop (ProcessLifecycleOwner) to the online client (short background grace) and the audio. */
class AppForegroundObserver(private val listeners: List<(Boolean) -> Unit>) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = listeners.forEach { it(true) }
    override fun onStop(owner: LifecycleOwner) = listeners.forEach { it(false) }
}
