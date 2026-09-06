package io.effect.browser.di

import android.content.Context
import android.util.Log
import io.effect.browser.core.ProcessInfo
import io.effect.browser.data.db.EffectDatabase
import io.effect.browser.data.repository.RoomBookmarkRepository
import io.effect.browser.data.repository.RoomContainerRepository
import io.effect.browser.data.repository.RoomTabRepository
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.files.FileDownloader
import io.effect.browser.files.FilePickerCoordinator
import io.effect.browser.gecko.GeckoRuntimeHolder
import io.effect.browser.gecko.GeckoSessionPool
import io.effect.browser.tor.TorController
import io.effect.browser.tor.TorStartup
import io.effect.browser.tor.TorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph.
 *
 * No DI framework on purpose: the graph is a dozen objects, and Hilt/Dagger would add an
 * annotation processor and several hundred KB to an APK that already carries GeckoView and a
 * tor binary.
 *
 * Note that this graph is built **once per process**, and the two processes get different
 * graphs — the tor process is the only one with a [TorController].
 */
object ServiceLocator {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var networkMode: NetworkMode
        private set

    val database: EffectDatabase by lazy { EffectDatabase.get(appContext) }

    val runtimeHolder: GeckoRuntimeHolder by lazy {
        GeckoRuntimeHolder(appContext, networkMode)
    }

    val sessionPool: GeckoSessionPool by lazy { GeckoSessionPool(runtimeHolder) }

    val filePicker: FilePickerCoordinator by lazy { FilePickerCoordinator() }

    /**
     * Application-scoped on purpose: a download must survive the tab that started it being
     * closed, and viewModelScope would cancel it mid-copy.
     */
    val fileDownloader: FileDownloader by lazy { FileDownloader(appContext, scope) }

    val containerRepository: RoomContainerRepository by lazy {
        RoomContainerRepository(
            dao = database.containerDao(),
            onContainerDeleted = { contextId -> runtimeHolder.clearContainerStorage(contextId) },
        )
    }

    val bookmarkRepository: RoomBookmarkRepository by lazy {
        RoomBookmarkRepository(database.bookmarkDao())
    }

    val tabRepository: RoomTabRepository by lazy {
        RoomTabRepository(database.tabDao())
    }

    /** Non-null only in the tor process. */
    var torController: TorController? = null
        private set

    private val _torStatus = MutableStateFlow<TorStatus>(TorStatus.Stopped)

    /** In the direct process this stays [TorStatus.Stopped] forever and nothing consults it. */
    val torStatus: StateFlow<TorStatus> = _torStatus.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        networkMode = ProcessInfo.networkModeOf(appContext)
        Log.i(TAG, "Initialising graph for process=${ProcessInfo.currentProcessName(appContext)} mode=$networkMode")

        scope.launch {
            runCatching { runtimeHolder.prepare() }
                .onFailure { Log.e(TAG, "Failed to prepare GeckoRuntime", it) }
        }

        // Seed from one process only. Both graphs share the database, so seeding from both
        // would race and could produce two starter containers.
        if (networkMode == NetworkMode.DIRECT) {
            scope.launch { containerRepository.ensureSeeded() }
        }

        if (networkMode == NetworkMode.TOR) {
            startTor()
        }
    }

    private fun startTor() {
        // Must happen before TorController touches TorServiceConfig; see TorStartup.
        TorStartup.ensureInitialized(appContext)

        val controller = TorController(appContext)
        torController = controller
        controller.start()

        scope.launch {
            controller.status.collect { status ->
                _torStatus.value = status
                if (status is TorStatus.Ready) {
                    runCatching { runtimeHolder.onTorSocksPortAvailable(status.socksPort) }
                        .onFailure { Log.e(TAG, "Could not apply tor proxy prefs", it) }
                }
            }
        }
    }

    private const val TAG = "ServiceLocator"
}
