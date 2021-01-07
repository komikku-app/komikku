package exh.ui.base

import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nucleus.presenter.Presenter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("DEPRECATION", "unused")
open class CoroutinePresenter<V>(
    scope: CoroutineScope = CoroutineScope(Job() + Dispatchers.Main)
) : Presenter<V>(), CoroutineScope by scope {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use launchInView, Flow.inView, Flow.mapView")
    override fun getView(): V? {
        return super.getView()
    }

    fun launchInView(block: (CoroutineScope, V) -> Unit) = launch(Dispatchers.Main) {
        view?.let { block.invoke(this, it) }
    }

    inline fun <F> Flow<F>.inView(crossinline block: (V, F) -> Unit) = onEach {
        withContext(Dispatchers.Main) {
            view?.let { view -> block(view, it) }
        }
    }

    inline fun <F, P> Flow<F>.mapView(crossinline block: (V, F) -> P): Flow<P> {
        return mapNotNull {
            withContext(Dispatchers.Main) {
                view?.let { view -> block(view, it) }
            }
        }
    }

    fun Flow<*>.launchUnderContext(context: CoroutineContext = EmptyCoroutineContext) =
        launch(context) { this@launchUnderContext.collect() }

    fun Flow<*>.launch() = launchIn(this@CoroutinePresenter)

    @CallSuper
    override fun destroy() {
        super.destroy()
        cancel()
    }
}
