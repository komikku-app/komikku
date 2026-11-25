package eu.kanade.tachiyomi.extension.installer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentSanitizer
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.lang.use
import eu.kanade.tachiyomi.util.system.getParcelableExtraCompat
import eu.kanade.tachiyomi.util.system.getUriSize
import exh.log.xLogE
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PackageInstallerInstaller(private val service: Service) : Installer(service) {

    private val packageInstaller = service.packageManager.packageInstaller

    private val packageActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // KMK -->
            // Abandoning a committed session still reports it, after the queue has moved on
            val reportedSessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            if (reportedSessionId != -1 && reportedSessionId != activeSession?.second) return
            // KMK <--
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val userAction = intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT)
                        ?.run {
                            // Doesn't actually needed as the receiver is actually not exported
                            // But the warnings can't be suppressed without this
                            IntentSanitizer.Builder()
                                .allowAction(this.action!!)
                                .allowExtra(PackageInstaller.EXTRA_SESSION_ID) { id -> id == activeSession?.second }
                                .allowAnyComponent()
                                .allowPackage {
                                    // There is no way to check the actual installer name so allow all.
                                    true
                                }
                                .build()
                                .sanitizeByFiltering(this)
                        }
                    if (userAction == null) {
                        xLogE("Fatal error for $intent")
                        // KMK -->
                        activeSession?.let { (_, sessionId) ->
                            runCatching { packageInstaller.abandonSession(sessionId) }
                        }
                        // KMK <--
                        continueQueue(InstallStep.Error)
                        return
                    }
                    userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        service.startActivity(userAction)
                    } catch (e: ActivityNotFoundException) {
                        // Some devices have the system installer's confirmation screen disabled or removed
                        logcat(LogPriority.WARN, e) { "Nothing can confirm the install" }
                        activeSession?.let { (_, sessionId) ->
                            runCatching { packageInstaller.abandonSession(sessionId) }
                        }
                        continueQueue(InstallStep.Error)
                    }
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    continueQueue(InstallStep.Idle)
                }
                PackageInstaller.STATUS_SUCCESS -> continueQueue(InstallStep.Installed)
                else -> continueQueue(InstallStep.Error)
            }
        }
    }

    // KMK -->
    // Written wherever the queue runs and read by the receiver on the main thread
    @Volatile
    // KMK <--
    private var activeSession: Pair<Entry, Int>? = null

    // Always ready
    override var ready = true

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        activeSession = null
        try {
            val installParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            activeSession = entry to packageInstaller.createSession(installParams)
            val fileSize = service.getUriSize(entry.uri) ?: throw IllegalStateException()
            installParams.setSize(fileSize)

            val inputStream = service.contentResolver.openInputStream(entry.uri) ?: throw IllegalStateException()
            val session = packageInstaller.openSession(activeSession!!.second)
            val outputStream = session.openWrite(entry.downloadId.toString(), 0, fileSize)
            session.use {
                arrayOf(inputStream, outputStream).use {
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
                service.contentResolver.delete(entry.uri, null, null)

                val intentSender = PendingIntent.getBroadcast(
                    service,
                    activeSession!!.second,
                    Intent(INSTALL_ACTION).setPackage(service.packageName),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
                ).intentSender
                @SuppressLint("RequestInstallPackagesPolicy")
                session.commit(intentSender)
            }
        } catch (e: Exception) {
            xLogE("Failed to install extension ${entry.downloadId} ${entry.uri}", e)
            activeSession?.let { (_, sessionId) ->
                packageInstaller.abandonSession(sessionId)
            }
            continueQueue(InstallStep.Error)
        }
    }

    override fun cancelEntry(entry: Entry): Boolean {
        activeSession?.let { (activeEntry, sessionId) ->
            if (activeEntry == entry) {
                return try {
                    packageInstaller.abandonSession(sessionId)
                    false
                } catch (_: SecurityException) {
                    // Highly likely the session has succeeded
                    true
                }
            }
        }
        return true
    }

    override fun onDestroy() {
        service.unregisterReceiver(packageActionReceiver)
        super.onDestroy()
    }

    init {
        ContextCompat.registerReceiver(
            service,
            packageActionReceiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

private const val INSTALL_ACTION = "PackageInstallerInstaller.INSTALL_ACTION"
