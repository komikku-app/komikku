package exh.ui.base

import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import nucleus.presenter.Presenter

@Suppress("DEPRECATION")
open class CoroutinePresenter<V> : Presenter<V>() {
    val scope = CoroutineScope(Job() + Dispatchers.Main)

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Use launchInView")
    override fun getView(): V? {
        return super.getView()
    }

    fun launchInView(block: (CoroutineScope, V) -> Unit) = scope.launch(Dispatchers.Main) {
        view?.let { block.invoke(this, it) }
    }

    fun <F> Flow<F>.onEachView(block: (V, F) -> Unit) = onEach {
        view?.let { view -> block(view, it) }
    }

    fun <F, P> Flow<F>.mapView(block: (V, F) -> P): Flow<P> = mapNotNull {
        view?.let { view -> block(view, it) }
    }

    @CallSuper
    override fun destroy() {
        super.destroy()
        scope.cancel()
    }
}
