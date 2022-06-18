package eu.kanade.tachiyomi

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.util.DebugLogger
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.ms_square.debugoverlay.DebugOverlay
import com.ms_square.debugoverlay.modules.FpsModule
import eu.kanade.domain.DomainModule
import eu.kanade.domain.SYDomainModule
import eu.kanade.tachiyomi.data.coil.DomainMangaKeyer
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverKeyer
import eu.kanade.tachiyomi.data.coil.MangaKeyer
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.notification
import exh.debug.DebugToggles
import exh.log.CrashlyticsPrinter
import exh.log.EHDebugModeOverlay
import exh.log.EHLogLevel
import exh.log.EnhancedFilePrinter
import exh.log.XLogLogcatLogger
import exh.log.xLogD
import exh.log.xLogE
import exh.syDebugVersion
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import logcat.LogcatLogger
import org.conscrypt.Conscrypt
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.days

class App : Application(), DefaultLifecycleObserver, ImageLoaderFactory {

    private val preferences: PreferencesHelper by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    @SuppressLint("LaunchActivityFromNotification")
    override fun onCreate() {
        super<Application>.onCreate()
        // if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        setupExhLogging() // EXH logging
        LogcatLogger.install(XLogLogcatLogger()) // SY Redirect Logcat to XLog
        if (!BuildConfig.DEBUG) addAnalytics()

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Avoid potential crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        Injekt.importModule(AppModule(this))
        Injekt.importModule(DomainModule())
        // SY -->
        Injekt.importModule(SYDomainModule())
        // SY <--

        setupNotificationChannels()
        if ((BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "releaseTest") && DebugToggles.ENABLE_DEBUG_OVERLAY.enabled) {
            setupDebugOverlay()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Show notification to disable Incognito Mode when it's enabled
        preferences.incognitoMode().asFlow()
            .onEach { enabled ->
                val notificationManager = NotificationManagerCompat.from(this)
                if (enabled) {
                    disableIncognitoReceiver.register()
                    val notification = notification(Notifications.CHANNEL_INCOGNITO_MODE) {
                        setContentTitle(getString(R.string.pref_incognito_mode))
                        setContentText(getString(R.string.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        setContentIntent(pendingIntent)
                    }
                    notificationManager.notify(Notifications.ID_INCOGNITO_MODE, notification)
                } else {
                    disableIncognitoReceiver.unregister()
                    notificationManager.cancel(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        preferences.themeMode()
            .asImmediateFlow {
                AppCompatDelegate.setDefaultNightMode(
                    when (it) {
                        PreferenceValues.ThemeMode.light -> AppCompatDelegate.MODE_NIGHT_NO
                        PreferenceValues.ThemeMode.dark -> AppCompatDelegate.MODE_NIGHT_YES
                        PreferenceValues.ThemeMode.system -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    },
                )
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        /*if (!LogcatLogger.isInstalled && preferences.verboseLogging()) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
        }*/
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this).apply {
            val callFactoryInit = { Injekt.get<NetworkHelper>().client }
            val diskCacheInit = { CoilDiskCache.get(this@App) }
            components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(TachiyomiImageDecoder.Factory())
                add(MangaCoverFetcher.Factory(lazy(callFactoryInit), lazy(diskCacheInit)))
                add(MangaCoverFetcher.DomainMangaFactory(lazy(callFactoryInit), lazy(diskCacheInit)))
                add(MangaCoverFetcher.MangaCoverFactory(lazy(callFactoryInit), lazy(diskCacheInit)))
                add(MangaKeyer())
                add(DomainMangaKeyer())
                add(MangaCoverKeyer())
            }
            callFactory(callFactoryInit)
            diskCache(diskCacheInit)
            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(getSystemService<ActivityManager>()!!.isLowRamDevice)
            if (preferences.verboseLogging()) logger(DebugLogger())
        }.build()
    }

    private fun addAnalytics() {
        if (syDebugVersion != "0") {
            Firebase.analytics.setUserProperty("preview_version", syDebugVersion)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!AuthenticatorUtil.isAuthenticating && preferences.lockAppAfter().get() >= 0) {
            SecureActivityDelegate.locked = true
        }
    }

    override fun getPackageName(): String {
        // This causes freezes in Android 6/7 for some reason
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Override the value passed as X-Requested-With in WebView requests
                val stackTrace = Looper.getMainLooper().thread.stackTrace
                val chromiumElement = stackTrace.find {
                    it.className.equals(
                        "org.chromium.base.BuildInfo",
                        ignoreCase = true,
                    )
                }
                if (chromiumElement?.methodName.equals("getAll", ignoreCase = true)) {
                    return WebViewUtil.SPOOF_PACKAGE_NAME
                }
            } catch (e: Exception) {
            }
        }
        return super.getPackageName()
    }

    protected open fun setupNotificationChannels() {
        try {
            Notifications.createChannels(this)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
        }
    }

    // EXH
    private fun setupExhLogging() {
        EHLogLevel.init(this)

        val logLevel = when {
            EHLogLevel.shouldLog(EHLogLevel.EXTREME) -> LogLevel.ALL
            EHLogLevel.shouldLog(EHLogLevel.EXTRA) || BuildConfig.DEBUG -> LogLevel.DEBUG
            else -> LogLevel.WARN
        }

        val logConfig = LogConfiguration.Builder()
            .logLevel(logLevel)
            .disableStackTrace()
            .disableBorder()
            .build()

        val printers = mutableListOf<Printer>(AndroidPrinter())

        val logFolder = File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                getString(R.string.app_name),
            "logs",
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        printers += EnhancedFilePrinter
            .Builder(logFolder.absolutePath) {
                fileNameGenerator = object : DateFileNameGenerator() {
                    override fun generateFileName(logLevel: Int, timestamp: Long): String {
                        return super.generateFileName(
                            logLevel,
                            timestamp,
                        ) + "-${BuildConfig.BUILD_TYPE}.log"
                    }
                }
                flattener { timeMillis, level, tag, message ->
                    "${dateFormat.format(timeMillis)} ${LogLevel.getShortLevelName(level)}/$tag: $message"
                }
                cleanStrategy = FileLastModifiedCleanStrategy(7.days.inWholeMilliseconds)
                backupStrategy = NeverBackupStrategy()
            }

        // Install Crashlytics in prod
        if (!BuildConfig.DEBUG) {
            printers += CrashlyticsPrinter(LogLevel.ERROR)
        }

        XLog.init(
            logConfig,
            *printers.toTypedArray(),
        )

        xLogD("Application booting...")
        xLogD(
            """
                App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE})
                Preview build: $syDebugVersion
                Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) 
                Android build ID: ${Build.DISPLAY}
                Device brand: ${Build.BRAND}
                Device manufacturer: ${Build.MANUFACTURER}
                Device name: ${Build.DEVICE}
                Device model: ${Build.MODEL}
                Device product name: ${Build.PRODUCT}
            """.trimIndent(),
        )
    }

    // EXH
    private fun setupDebugOverlay() {
        try {
            DebugOverlay.Builder(this)
                .modules(FpsModule(), EHDebugModeOverlay(this))
                .bgColor(Color.parseColor("#7F000000"))
                .notification(false)
                .allowSystemLayer(false)
                .build()
                .install()
        } catch (e: IllegalStateException) {
            // Crashes if app is in background
            xLogE("Failed to initialize debug overlay, app in background?", e)
        }
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            preferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                registerReceiver(this, IntentFilter(ACTION_DISABLE_INCOGNITO_MODE))
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }
}

private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"

/**
 * Direct copy of Coil's internal SingletonDiskCache so that [MangaCoverFetcher] can access it.
 */
internal object CoilDiskCache {

    private const val FOLDER_NAME = "image_cache"
    private var instance: DiskCache? = null

    @Synchronized
    fun get(context: Context): DiskCache {
        return instance ?: run {
            val safeCacheDir = context.cacheDir.apply { mkdirs() }
            // Create the singleton disk cache instance.
            DiskCache.Builder()
                .directory(safeCacheDir.resolve(FOLDER_NAME))
                .build()
                .also { instance = it }
        }
    }
}
