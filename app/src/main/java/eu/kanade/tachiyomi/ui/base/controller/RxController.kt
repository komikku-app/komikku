package eu.kanade.tachiyomi.ui.base.controller

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import rx.Observable
import rx.Subscription
import rx.subscriptions.CompositeSubscription

abstract class RxController<VB : ViewBinding>(bundle: Bundle? = null) : BaseController<VB>(bundle) {

    private var untilDestroySubscriptions = CompositeSubscription()
    private var untilDetachSubscriptions = CompositeSubscription()

    @CallSuper
    override fun onViewCreated(view: View) {
        if (untilDestroySubscriptions.isUnsubscribed) {
            untilDestroySubscriptions = CompositeSubscription()
        }
    }

    @CallSuper
    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        untilDestroySubscriptions.unsubscribe()
    }

    @CallSuper
    override fun onAttach(view: View) {
        super.onAttach(view)
        if (untilDetachSubscriptions.isUnsubscribed) {
            untilDetachSubscriptions = CompositeSubscription()
        }
    }

    @CallSuper
    override fun onDetach(view: View) {
        super.onDetach(view)
        untilDetachSubscriptions.unsubscribe()
    }

    fun <T> Observable<T>.subscribeUntilDestroy(onNext: (T) -> Unit): Subscription {
        return subscribe(onNext).also { untilDestroySubscriptions.add(it) }
    }

    fun <T> Observable<T>.subscribeUntilDetach(onNext: (T) -> Unit): Subscription {
        return subscribe(onNext).also { untilDetachSubscriptions.add(it) }
    }
}
