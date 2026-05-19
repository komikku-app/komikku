package eu.kanade.tachiyomi.di

import android.app.Application
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class SYPreferenceModule(val application: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory {
            DelegateSourcePreferences(
                preferenceStore = get(),
            )
        }

        addSingletonFactory {
            ExhPreferences(get())
        }
    }
}
