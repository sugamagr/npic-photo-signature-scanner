package com.npic.photoandsignscanner.data.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.npic.photoandsignscanner.BuildConfig
import java.io.File

/**
 * m2508: installs a downloaded APK via the PackageInstaller Session API.
 *
 * Session API is the current (Android 13+) recommended path because:
 *   1. It survives the "restricted settings" gate on Android 13+ that blocks
 *      accessibility / notification-listener grants for sideloaded apps
 *   2. It supports split APKs (future-proofing if we ever ship ABI splits)
 *   3. It surfaces install-time errors via [PackageInstaller.EXTRA_STATUS] instead
 *      of a silent Activity result, so the ViewModel can render "Samsung Auto
 *      Blocker rejected the install" specifically instead of a generic failure
 *
 * The intent-based [Intent.ACTION_INSTALL_PACKAGE] path is deprecated in API 29 and
 * would land the app in that restricted-settings state on modern devices — we do
 * NOT use it as a fallback.
 */
class UpdateInstaller(
    private val context: Context,
) {

    /**
     * Streams [apkFile] into a fresh PackageInstaller session, commits it, and
     * routes the install status broadcast into [onStatus]. The caller registers
     * the receiver with [registerStatusReceiver] before calling install so no
     * status events are lost.
     */
    fun install(apkFile: File): InstallOutcome {
        if (!canRequestInstalls()) {
            return InstallOutcome.NeedsPermission(unknownSourcesIntent())
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (throwable: Throwable) {
            Log.e(TAG, "createSession failed", throwable)
            return InstallOutcome.Failed("Couldn't start the installer. ${throwable.message ?: ""}")
        }

        return runCatching {
            installer.openSession(sessionId).use { session ->
                session.openWrite("npic-update.apk", 0, apkFile.length()).use { output ->
                    apkFile.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val statusIntent = Intent(ACTION_INSTALL_STATUS).apply {
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PENDING_INTENT_FLAGS,
                )
                session.commit(pendingIntent.intentSender)
            }
            InstallOutcome.Committed
        }.getOrElse { throwable ->
            Log.e(TAG, "commit failed", throwable)
            runCatching { installer.abandonSession(sessionId) }
            InstallOutcome.Failed("Install failed. ${throwable.message ?: ""}")
        }
    }

    /**
     * Register a receiver for [ACTION_INSTALL_STATUS]. The caller must unregister
     * when the update flow ends. The [callback] fires with the raw PackageInstaller
     * status code + the human-readable status message.
     *
     * m2509 B5: [activityForConfirm] is used to launch the system's install-confirm
     * dialog on `STATUS_PENDING_USER_ACTION`. Starting an Activity from an
     * applicationContext + `FLAG_ACTIVITY_NEW_TASK` works on stock AOSP but is
     * fragile on Samsung One UI / Xiaomi MIUI where background-launch policies can
     * silently swallow the intent — using the live Activity reference avoids the
     * whole class of "install UI never appeared" reports.
     */
    fun registerStatusReceiver(
        activityForConfirm: Activity?,
        callback: (status: Int, message: String?) -> Unit,
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                        }
                        confirm?.let { confirmIntent ->
                            val launcher = activityForConfirm ?: context
                            if (activityForConfirm == null) {
                                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { launcher.startActivity(confirmIntent) }
                        }
                    }
                    else -> callback(status, message)
                }
            }
        }
        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        return receiver
    }

    fun unregister(receiver: BroadcastReceiver) {
        runCatching { context.unregisterReceiver(receiver) }
    }

    /**
     * m2509 B4: returns true when this app has any in-flight PackageInstaller session
     * still open. Called from ViewModel init to detect a "user backgrounded the app
     * mid-install, process was killed, they came back" scenario — otherwise the
     * ViewModel state defaults to Idle while the OS still holds an open session,
     * hiding the update sheet on a device that legitimately has a pending install.
     */
    fun hasPendingSession(): Boolean {
        return runCatching {
            context.packageManager.packageInstaller.mySessions.isNotEmpty()
        }.getOrDefault(false)
    }

    /**
     * m2509 B4: abandons every open session this app owns. Used on next launch when
     * we detect a stale session from a killed install flow — leaving abandoned
     * sessions around wastes cache and can confuse future commits.
     */
    fun abandonPendingSessions() {
        runCatching {
            val installer = context.packageManager.packageInstaller
            for (session in installer.mySessions) {
                runCatching { installer.abandonSession(session.sessionId) }
            }
        }
    }

    /**
     * Returns true when the OS lets us call PackageInstaller.commit. On API 26+ the
     * user must individually toggle "Install unknown apps" for this app — the
     * PackageInstaller call would return STATUS_FAILURE_INVALID otherwise, but
     * checking up front lets the UI route straight to the settings screen without
     * burning a session.
     */
    fun canRequestInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    /**
     * Intent that lands the user on `Settings → Apps → Special access → Install
     * unknown apps → NPIC Photo Scanner`. On grant, the OS returns them here.
     */
    fun unknownSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Intent that lands the user on Samsung's Auto Blocker settings (One UI 6.1+).
     * When install returns STATUS_FAILURE_BLOCKED on a Samsung device we launch
     * this so the teacher can flip the toggle without hunting through menus.
     */
    fun autoBlockerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** True when the manufacturer is Samsung and the OS is Android 14+ (One UI 6.x+). */
    fun isLikelyAutoBlockerDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    sealed interface InstallOutcome {
        /** Session was committed; status arrives via the receiver. */
        data object Committed : InstallOutcome
        /** REQUEST_INSTALL_PACKAGES not granted yet; launch [intent] to prompt. */
        data class NeedsPermission(val intent: Intent) : InstallOutcome
        data class Failed(val message: String) : InstallOutcome
    }

    companion object {
        private const val TAG = "UpdateInstaller"

        // m2508: private per-app action so a random installed app can't spoof our
        // install status broadcasts. Combined with setPackage(context.packageName)
        // and RECEIVER_NOT_EXPORTED, the broadcast never leaves this process.
        private const val ACTION_INSTALL_STATUS =
            "${BuildConfig.APPLICATION_ID}.action.INSTALL_STATUS"

        private val PENDING_INTENT_FLAGS: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
    }
}
