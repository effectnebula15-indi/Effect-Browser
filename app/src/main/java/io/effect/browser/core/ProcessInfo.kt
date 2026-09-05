package io.effect.browser.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import io.effect.browser.domain.model.NetworkMode
import java.io.File

/**
 * Which of the app's two processes this code is running in.
 *
 * The whole container-level network isolation rests on this: a process hosts exactly one
 * `GeckoRuntime`, and a runtime has exactly one proxy configuration.
 */
object ProcessInfo {

    /** Must match `android:process` on the tor activity in AndroidManifest.xml. */
    const val TOR_PROCESS_SUFFIX = ":tor"

    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return android.app.Application.getProcessName()
        }
        // API 26/27: /proc is cheap and always right; ActivityManager is the fallback for the
        // rare device where /proc reads are restricted.
        readCmdline()?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pid = Process.myPid()
        return am?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
            ?: context.packageName
    }

    /** cmdline is NUL-padded; the process name is everything before the first NUL. */
    private fun readCmdline(): String? = runCatching {
        File("/proc/self/cmdline").readText().substringBefore(Char.MIN_VALUE).trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun networkModeOf(context: Context): NetworkMode =
        if (currentProcessName(context).endsWith(TOR_PROCESS_SUFFIX)) {
            NetworkMode.TOR
        } else {
            NetworkMode.DIRECT
        }
}
