package io.effect.browser.tor

import android.content.Context
import android.util.Log
import io.effect.browser.core.ProcessInfo
import io.effect.browser.domain.model.NetworkMode
import io.matthewnelson.kmp.tor.resource.noexec.tor.ResourceLoaderTorNoExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the embedded tor daemon.
 *
 * Deliberately constructed **only in the `:tor` process**. Tor's SOCKS port is bound on loopback
 * by the process that starts it; keeping the daemon in the same process as the tor-mode
 * `GeckoRuntime` means the port can be handed straight to Gecko's preferences with no IPC.
 *
 * Tor is provided by [kmp-tor](https://github.com/05nelsonm/kmp-tor) 2.x — the maintained
 * successor to the long-abandoned `TorOnionProxyLibrary-Android`, and the reason this app does
 * not depend on Orbot being installed.
 */
class TorController(
    appContext: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    init {
        require(ProcessInfo.networkModeOf(appContext) == NetworkMode.TOR) {
            "TorController must only be created in the ${ProcessInfo.TOR_PROCESS_SUFFIX} process"
        }
    }

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private val started = AtomicBoolean(false)

    @Volatile
    private var bootstrapPercent: Int = 0

    @Volatile
    private var socksPort: Int? = null

    private val environment: TorRuntime.Environment by lazy {
        TorServiceConfig.Builder {
            // Browsing is over when the task is gone; do not keep a daemon alive behind the user.
            stopServiceOnTaskRemoved = true
        }.newEnvironment { resourceDir ->
            // Loads tor in-process through JNI rather than spawning it as a child process, so
            // the APK does not have to opt into extracting its native libraries.
            ResourceLoaderTorNoExec.getOrCreate(resourceDir)
        }
    }

    private val runtime: TorRuntime by lazy {
        TorRuntime.Builder(environment) {
            config { _ ->
                // auto() lets tor pick a free loopback port and report it back, instead of us
                // guessing a fixed one and colliding with whatever else is on the device.
                TorOption.SocksPort.configure { auto() }
            }
            observerStatic(RuntimeEvent.STATE) { state -> onState(state) }
            observerStatic(RuntimeEvent.LISTENERS) { listeners ->
                onSocksPort(listeners.socks.firstOrNull()?.port?.value)
            }
            observerStatic(RuntimeEvent.ERROR) { error -> onError(error) }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        _status.value = TorStatus.Starting(bootstrapPercent = 0)
        scope.launch {
            runCatching { runtime.startDaemonAsync() }
                .onFailure { onError(it) }
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        scope.launch {
            runCatching { runtime.stopDaemonAsync() }
            socksPort = null
            bootstrapPercent = 0
            _status.value = TorStatus.Stopped
        }
    }

    private fun onState(state: TorState) {
        bootstrapPercent = state.daemon.bootstrap.toInt()
        if (state.daemon.isOff) {
            socksPort = null
            _status.value = TorStatus.Stopped
            return
        }
        publish()
    }

    private fun onSocksPort(port: Int?) {
        socksPort = port
        publish()
    }

    private fun onError(error: Throwable) {
        Log.e(TAG, "tor failed", error)
        _status.value = TorStatus.Failed(error.message ?: error::class.java.simpleName)
    }

    /**
     * Ready requires both signals: a bootstrapped daemon *and* a bound SOCKS port. Publishing
     * Ready on bootstrap alone would let the UI start loading against a port that is not up yet.
     */
    private fun publish() {
        val port = socksPort
        _status.value = when {
            port != null && bootstrapPercent >= 100 -> TorStatus.Ready(port)
            else -> TorStatus.Starting(bootstrapPercent)
        }
    }

    private companion object {
        const val TAG = "TorController"
    }
}
