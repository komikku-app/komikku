package eu.kanade.tachiyomi.di

import android.app.Application
import exh.pref.DelegateSourcePreferences
import tachiyomi.domain.UnsortedPreferences
import uy.kohesive.injekt.api.InjektRegistrar

class SYPreferenceModule(val application: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory {
            DelegateSourcePreferences(
                preferenceStore = get(),
            )
        }

        addSingletonFactory {
            UnsortedPreferences(get())
        }
    }
}
