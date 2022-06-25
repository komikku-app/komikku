package exh.ui.base

import android.os.Bundle
import androidx.annotation.CallSuper
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withUIContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import nucleus.presenter.Presenter
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("DEPRECATION", "unused")
open class CoroutinePresenter<V>(
    private val scope: () -> CoroutineScope = ::MainScope,
) : Presenter<V>() {
    var presenterScope: CoroutineScope = scope()

    @CallSuper
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (!presenterScope.isActive) {
            presenterScope = scope()
        }
    }

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use launchInView, Flow.inView, Flow.mapView")
    override fun getView(): V? {
        return super.getView()
    }

    fun launchInView(block: (CoroutineScope, V) -> Unit) = presenterScope.launchUI {
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

    fun Flow<*>.launchUnderContext(context: CoroutineContext = EmptyCoroutineContext) = flowOn(context)
        .launch()

    fun Flow<*>.launch() = launchIn(presenterScope)

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        presenterScope.cancel()
    }
}
