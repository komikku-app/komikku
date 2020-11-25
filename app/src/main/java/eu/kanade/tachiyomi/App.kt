package eu.kanade.tachiyomi

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Environment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.ms_square.debugoverlay.DebugOverlay
import com.ms_square.debugoverlay.modules.FpsModule
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.debug.DebugToggles
import exh.log.CrashlyticsPrinter
import exh.log.EHDebugModeOverlay
import exh.log.EHLogLevel
import exh.log.EnhancedFilePrinter
import exh.syDebugVersion
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.injectLazy
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.io.File
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread
import kotlin.time.ExperimentalTime
import kotlin.time.days

open class App : Application(), LifecycleObserver {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        setupExhLogging() // EXH logging
        if (!BuildConfig.DEBUG) addAnalytics()

        workaroundAndroid7BrokenSSL()

        // Debug tool; see https://fbflipper.com/
        // SoLoader.init(this, false)
        // if (BuildConfig.DEBUG && FlipperUtils.shouldEnableFlipper(this)) {
        //     val client = AndroidFlipperClient.getInstance(this)
        //     client.addPlugin(InspectorFlipperPlugin(this, DescriptorMapping.withDefaults()))
        //     client.addPlugin(DatabasesFlipperPlugin(this))
        //     client.start()
        // }

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        Injekt = InjektScope(DefaultRegistrar())
        Injekt.importModule(AppModule(this))

        setupNotificationChannels()
        Realm.init(this)
        GlobalScope.launch { deleteOldMetadataRealm() } // Delete old metadata DB (EH)
        if ((BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "releaseTest") && DebugToggles.ENABLE_DEBUG_OVERLAY.enabled) {
            setupDebugOverlay()
        }

        LocaleHelper.updateConfiguration(this, resources.configuration)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleHelper.updateConfiguration(this, newConfig, true)
    }

    private fun workaroundAndroid7BrokenSSL() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.N ||
            Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1
        ) {
            try {
                SSLContext.getInstance("TLSv1.2")
            } catch (e: NoSuchAlgorithmException) {
                XLog.tag("Init").e("Could not install Android 7 broken SSL workaround!", e)
            }

            try {
                ProviderInstaller.installIfNeeded(applicationContext)
            } catch (e: GooglePlayServicesRepairableException) {
                XLog.tag("Init").e("Could not install Android 7 broken SSL workaround!", e)
            } catch (e: GooglePlayServicesNotAvailableException) {
                XLog.tag("Init").e("Could not install Android 7 broken SSL workaround!", e)
            }
        }
    }

    private fun addAnalytics() {
        firebaseAnalytics = Firebase.analytics
        if (syDebugVersion != "0") {
            firebaseAnalytics.setUserProperty("preview_version", syDebugVersion)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @Suppress("unused")
    fun onAppBackgrounded() {
        val preferences: PreferencesHelper by injectLazy()
        if (preferences.lockAppAfter().get() >= 0) {
            SecureActivityDelegate.locked = true
        }
    }

    protected open fun setupNotificationChannels() {
        Notifications.createChannels(this)
    }

    // EXH
    private fun deleteOldMetadataRealm() {
        val config = RealmConfiguration.Builder()
            .name("gallery-metadata.realm")
            .schemaVersion(3)
            .deleteRealmIfMigrationNeeded()
            .build()
        Realm.deleteRealm(config)

        // Delete old paper db files
        listOf(
            File(filesDir, "gallery-ex"),
            File(filesDir, "gallery-perveden"),
            File(filesDir, "gallery-nhentai")
        ).forEach {
            if (it.exists()) {
                thread {
                    it.deleteRecursively()
                }
            }
        }
    }

    // EXH
    private fun setupExhLogging() {
        EHLogLevel.init(this)

        val logLevel = when {
            EHLogLevel.shouldLog(EHLogLevel.EXTRA) -> LogLevel.ALL
            BuildConfig.DEBUG -> LogLevel.DEBUG
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
            "logs"
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        @OptIn(ExperimentalTime::class)
        printers += EnhancedFilePrinter
            .Builder(logFolder.absolutePath)
            .fileNameGenerator(
                object : DateFileNameGenerator() {
                    override fun generateFileName(logLevel: Int, timestamp: Long): String {
                        return super.generateFileName(
                            logLevel,
                            timestamp
                        ) + "-${BuildConfig.BUILD_TYPE}.log"
                    }
                }
            )
            .flattener { timeMillis, level, tag, message ->
                "${dateFormat.format(timeMillis)} ${LogLevel.getShortLevelName(level)}/$tag: $message"
            }
            .cleanStrategy(FileLastModifiedCleanStrategy(7.days.toLongMilliseconds()))
            .backupStrategy(NeverBackupStrategy())
            .build()

        // Install Crashlytics in prod
        if (!BuildConfig.DEBUG) {
            printers += CrashlyticsPrinter(LogLevel.ERROR)
        }

        XLog.init(
            logConfig,
            *printers.toTypedArray()
        )

        XLog.tag("Init").d("Application booting...")
        XLog.tag("Init").disableStackTrace().d(
            "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, ${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE})\n" +
                "Preview build: $syDebugVersion\n" +
                "Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) \n" +
                "Android build ID: ${Build.DISPLAY}\n" +
                "Device brand: ${Build.BRAND}\n" +
                "Device manufacturer: ${Build.MANUFACTURER}\n" +
                "Device name: ${Build.DEVICE}\n" +
                "Device model: ${Build.MODEL}\n" +
                "Device product name: ${Build.PRODUCT}"
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
            XLog.tag("Init").e("Failed to initialize debug overlay, app in background?", e)
        }
    }
}
