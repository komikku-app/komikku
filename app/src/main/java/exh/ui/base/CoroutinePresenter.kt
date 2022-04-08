package exh.ui.base

import androidx.annotation.CallSuper
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import nucleus.presenter.Presenter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("DEPRECATION", "unused")
open class CoroutinePresenter<V>(
    scope: CoroutineScope = MainScope(),
) : Presenter<V>(), CoroutineScope by scope {
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use launchInView, Flow.inView, Flow.mapView")
    override fun getView(): V? {
        return super.getView()
    }

    fun launchInView(block: (CoroutineScope, V) -> Unit) = launchUI {
        view?.let { block.invoke(this, it) }
    }

    inline fun <F> Flow<F>.inView(crossinline block: (V, F) -> Unit) = onEach {
        withUIContext {
            view?.let { view -> block(view, it) }
        }
    }

    inline fun <F, P> Flow<F>.mapView(crossinline block: (V, F) -> P): Flow<P> {
        return mapNotNull {
            withUIContext {
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
