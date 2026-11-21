package app.numbaseball.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.numbaseball.AppContainer
import app.numbaseball.LocalAppContainer

/** A ViewModel scoped to the current navigation entry, built from the app's [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: AppContainer.() -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { container.create() } })
}

/** Window height in dp (follows split-screen and pop-up window resizing). */
@Composable
fun rememberWindowHeightDp(): Int {
    val height = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { height.toDp().value.toInt() }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
