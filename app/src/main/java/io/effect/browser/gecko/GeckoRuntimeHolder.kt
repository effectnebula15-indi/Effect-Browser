package io.effect.browser.gecko

import android.content.Context
import android.util.Log
import io.effect.browser.domain.model.NetworkMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Owns the one [GeckoRuntime] this process is allowed to have.
 *
 * Gecko permits a single runtime per process, and its `network.proxy.*` preferences are
 * process-wide. [networkMode] is therefore fixed for the lifetime of the process and is decided
 * by which process we are in, not by which container is on screen.
 */
class GeckoRuntimeHolder(
    private val appContext: Context,
    val networkMode: NetworkMode,
) {

    @Volatile
    private var prepared = false

    /**
     * Whether this runtime may be asked to load a page.
     *
     * Direct-mode runtimes are usable immediately. A tor-mode runtime is not usable until it has
     * been pointed at a live SOCKS port, and the UI must not load anything before this is true.
     */
    private val _readyToLoad = MutableStateFlow(networkMode != NetworkMode.TOR)
    val readyToLoad: StateFlow<Boolean> = _readyToLoad.asStateFlow()

    val runtime: GeckoRuntime by lazy {
        // create(), not getDefault(): getDefault() would build a runtime with stock settings,
        // and in the tor process the settings are part of the isolation.
        GeckoRuntime.create(appContext, baseRuntimeSettings().build()).also {
            Log.i(TAG, "GeckoRuntime created for networkMode=$networkMode")
        }
    }

    /**
     * Brings the runtime to a state where loading a page is safe.
     *
     * In the tor process this installs the fail-closed proxy configuration. It must complete
     * before any session loads a URL; [io.effect.browser.ui.browser.BrowserViewModel] additionally
     * refuses to load until tor has bootstrapped.
     */
    suspend fun prepare() {
        if (prepared) return
        // Touch the lazy so the runtime exists before prefs are pushed at it.
        runtime
        if (networkMode == NetworkMode.TOR) {
            GeckoProxyPrefs.applyBlackhole()
            _readyToLoad.value = false
        }
        prepared = true
    }

    /** Applies tor's real SOCKS port. No-op outside the tor process. */
    suspend fun onTorSocksPortAvailable(port: Int) {
        require(networkMode == NetworkMode.TOR) {
            "Refusing to point a direct-mode runtime at the tor SOCKS port"
        }
        GeckoProxyPrefs.applySocks(port)
        _readyToLoad.value = true
        Log.i(TAG, "Gecko proxy now pointing at tor SOCKS port $port")
    }

    /**
     * Drops every trace of a container's browsing state.
     *
     * Keyed on the same contextId that isolated the data in the first place.
     */
    fun clearContainerStorage(contextId: String) {
        runtime.storageController.clearDataForSessionContext(contextId)
    }

    private companion object {
        const val TAG = "GeckoRuntimeHolder"
    }
}

/** Settings shared by both processes. Kept in one place so the two runtimes cannot drift. */
internal fun baseRuntimeSettings(): GeckoRuntimeSettings.Builder =
    GeckoRuntimeSettings.Builder()
        .javaScriptEnabled(true)
        .aboutConfigEnabled(false)
        .remoteDebuggingEnabled(false)
