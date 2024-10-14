package eu.kanade.tachiyomi.di

import android.content.Context
import logcat.LogPriority
import logcat.logcat
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektScope

object InjektKoinBridge {
    private val modules = mutableMapOf<InjektModule, Module>()
    fun getModule(injektModule: InjektModule) = modules.getOrPut(injektModule) { Module() }

    fun startKoin(context: Context) {
        startKoin {
            logger(
                object : Logger() {
                    override fun display(level: Level, msg: MESSAGE) {
                        logcat(
                            when (level) {
                                Level.DEBUG -> LogPriority.DEBUG
                                Level.INFO -> LogPriority.INFO
                                Level.WARNING -> LogPriority.WARN
                                Level.ERROR -> LogPriority.ERROR
                                Level.NONE -> LogPriority.VERBOSE
                            },
                        ) { msg }
                    }
                },
            )
            androidContext(context)
            modules(modules.values.toList())
        }
    }
}

interface InjektModule {
    fun InjektRegistrar.registerInjectables()
}

inline fun <reified T> InjektModule.addSingleton(instance: T) {
    val module = InjektKoinBridge.getModule(this)
    module.single<T> { instance }
}

inline fun <reified T> InjektModule.addSingletonFactory(crossinline instance: Scope.() -> T) {
    val module = InjektKoinBridge.getModule(this)
    module.single<T> { instance() }
}

inline fun <reified T> InjektModule.addFactory(crossinline instance: Scope.() -> T) {
    val module = InjektKoinBridge.getModule(this)
    module.factory<T> { instance() }
}

fun InjektScope.importModule(injektModule: InjektModule) {
    with(injektModule) { registrar.registerInjectables() }
}
