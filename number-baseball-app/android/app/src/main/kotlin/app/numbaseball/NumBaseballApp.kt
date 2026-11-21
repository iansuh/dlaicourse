package app.numbaseball

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import app.numbaseball.data.online.AppForegroundObserver

open class NumBaseballApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        observeProcessLifecycle()
    }

    /** Tests override this to supply fakes (see TestNumBaseballApp). */
    protected open fun createContainer(): AppContainer = DefaultAppContainer(this, AppConfig.fromBuildConfig())

    protected open fun observeProcessLifecycle() {
        val observer = AppForegroundObserver(listOf(container.onlineClient::setAppInForeground, container.audio::setAppInForeground))
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }
}
