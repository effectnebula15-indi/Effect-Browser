package io.effect.browser.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.domain.repository.BookmarkRepository
import io.effect.browser.domain.repository.ContainerRepository
import io.effect.browser.domain.repository.TabRepository
import io.effect.browser.domain.url.AddressResolver
import io.effect.browser.domain.url.SearchEngine
import io.effect.browser.files.FileDownloader
import io.effect.browser.files.FilePickerCoordinator
import io.effect.browser.files.FilePickerRequest
import io.effect.browser.gecko.GeckoRuntimeHolder
import io.effect.browser.gecko.GeckoSessionPool
import io.effect.browser.tor.TorStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebResponse

class BrowserViewModel(
    private val appContext: Context,
    private val networkMode: NetworkMode,
    private val containerRepository: ContainerRepository,
    private val tabRepository: TabRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val sessionPool: GeckoSessionPool,
    private val filePicker: FilePickerCoordinator,
    private val fileDownloader: FileDownloader,
    runtimeHolder: GeckoRuntimeHolder,
    torStatus: StateFlow<TorStatus>,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState(networkMode = networkMode))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BrowserEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<BrowserEffect> = _effects.asSharedFlow()

    /** Sessions whose delegates are already attached, so we wire each one exactly once. */
    private val wiredSessions = mutableSetOf<Long>()

    /**
     * Container requested by the activity that launched us, before the container list has loaded.
     * Set when the user crosses from the other process by tapping a chip.
     */
    @Volatile
    private var pendingContainerId: Long? = null

    /**
     * The live "is this URL bookmarked" subscription.
     *
     * Held so navigating replaces it. Without this, every location change would start another
     * never-completing collector on viewModelScope and they would pile up for the session.
     */
    private var bookmarkWatch: Job? = null

    init {
        viewModelScope.launch {
            containerRepository.observeAll().collect { containers ->
                _state.update { current ->
                    val requested = pendingContainerId
                        ?.let { id -> containers.firstOrNull { it.id == id } }
                        ?.takeIf { it.networkMode == networkMode }
                        ?.also { pendingContainerId = null }

                    val active = requested
                        ?: current.activeContainer
                            ?.let { previous -> containers.firstOrNull { it.id == previous.id } }
                        ?: containers.firstOrNull { it.networkMode == networkMode }

                    current.copy(
                        containers = containers,
                        activeContainer = active,
                        activeTabId = if (requested != null) null else current.activeTabId,
                    )
                }
                ensureTabForActiveContainer()
            }
        }

        viewModelScope.launch {
            tabRepository.observeForNetworkMode(networkMode).collect { tabs ->
                _state.update { it.copy(tabs = tabs) }
                ensureTabForActiveContainer()
            }
        }

        viewModelScope.launch {
            torStatus.collect { status -> _state.update { it.copy(torStatus = status) } }
        }

        viewModelScope.launch {
            runtimeHolder.readyToLoad.collect { ready -> _state.update { it.copy(readyToLoad = ready) } }
        }
    }

    // ---------------------------------------------------------------- containers

    /**
     * Asks for a specific container, which may not have loaded yet.
     *
     * Used when the other process hands us a container id through an Intent.
     */
    fun requestContainer(containerId: Long) {
        val match = _state.value.containers.firstOrNull { it.id == containerId }
        if (match != null && match.networkMode == networkMode) {
            onContainerSelected(match)
        } else {
            pendingContainerId = containerId
        }
    }

    fun onContainerSelected(container: Container) {
        if (container.networkMode != networkMode) {
            // Its tabs can only be rendered by the other process's runtime.
            _effects.tryEmit(BrowserEffect.SwitchProcess(container.id, container.networkMode))
            return
        }
        _state.update { it.copy(activeContainer = container, activeTabId = null) }
        ensureTabForActiveContainer()
    }

    fun createContainer(name: String, colorArgb: Int, mode: NetworkMode) {
        viewModelScope.launch { containerRepository.create(name, colorArgb, mode) }
    }

    fun updateContainer(id: Long, name: String, colorArgb: Int, mode: NetworkMode) {
        viewModelScope.launch {
            containerRepository.rename(id, name, colorArgb)
            containerRepository.setNetworkMode(id, mode)
        }
    }

    fun deleteContainer(id: Long) {
        viewModelScope.launch {
            state.value.tabs.filter { it.containerId == id }.forEach { sessionPool.release(it.id) }
            containerRepository.delete(id)
        }
    }

    // ---------------------------------------------------------------- tabs

    private fun ensureTabForActiveContainer() {
        val current = _state.value
        val container = current.activeContainer ?: return
        val containerTabs = current.tabs.filter { it.containerId == container.id }

        if (containerTabs.isEmpty()) {
            viewModelScope.launch { tabRepository.open(container.id, homeUrl()) }
            return
        }
        if (current.activeTabId == null || containerTabs.none { it.id == current.activeTabId }) {
            selectTab(containerTabs.first().id)
        }
    }

    fun selectTab(tabId: Long) {
        _state.update { it.copy(activeTabId = tabId) }
        viewModelScope.launch { tabRepository.touch(tabId) }
        val tab = _state.value.tabs.firstOrNull { it.id == tabId } ?: return
        _state.update { it.copy(addressText = tab.url, currentUrl = tab.url, pageTitle = tab.title) }
        refreshBookmarkState(tab.url)
    }

    fun openNewTab() {
        val container = _state.value.activeContainer ?: return
        viewModelScope.launch {
            val tab = tabRepository.open(container.id, homeUrl())
            _state.update { it.copy(activeTabId = tab.id, addressText = "", currentUrl = tab.url) }
        }
    }

    fun closeTab(tabId: Long) {
        sessionPool.release(tabId)
        wiredSessions.remove(tabId)
        viewModelScope.launch { tabRepository.close(tabId) }
    }

    /**
     * Returns the live session for [tabId], creating and wiring it on first use.
     *
     * The session is built with the container's contextId, which is what keeps this tab's
     * cookies out of every other container's jar.
     */
    fun sessionFor(tabId: Long, container: Container): GeckoSession {
        val session = sessionPool.acquire(tabId, container)
        if (wiredSessions.add(tabId)) {
            attachDelegates(tabId, session)
            val url = _state.value.tabs.firstOrNull { it.id == tabId }?.url ?: homeUrl()
            loadIfPermitted(session, url)
        }
        return session
    }

    // ---------------------------------------------------------------- navigation

    fun onAddressChanged(text: String) {
        _state.update { it.copy(addressText = text) }
    }

    fun onAddressSubmitted() {
        val current = _state.value
        val engine = current.activeContainer
            ?.let { SearchEngine.defaultFor(it.networkMode) }
            ?: SearchEngine.defaultFor(networkMode)

        val url = AddressResolver.resolve(current.addressText, engine) ?: return
        val tabId = current.activeTabId ?: return
        val session = sessionPool.peek(tabId) ?: return
        loadIfPermitted(session, url)
    }

    fun goBack() = withActiveSession { it.goBack() }
    fun goForward() = withActiveSession { it.goForward() }
    fun reload() = withActiveSession { it.reload() }
    fun stopLoading() = withActiveSession { it.stop() }

    fun navigateTo(url: String) {
        val tabId = _state.value.activeTabId ?: return
        val session = sessionPool.peek(tabId) ?: return
        loadIfPermitted(session, url)
    }

    /**
     * The single choke point for "put traffic on the wire".
     *
     * Everything that navigates goes through here so the tor gate cannot be bypassed by adding
     * a new call site later.
     */
    private fun loadIfPermitted(session: GeckoSession, url: String) {
        if (!_state.value.canNavigate) return
        session.loadUri(url)
    }

    private inline fun withActiveSession(block: (GeckoSession) -> Unit) {
        val tabId = _state.value.activeTabId ?: return
        sessionPool.peek(tabId)?.let(block)
    }

    /** Loads any tab that was waiting on tor to come up. */
    fun onNavigationUnblocked() {
        val current = _state.value
        if (!current.canNavigate) return
        val tabId = current.activeTabId ?: return
        val session = sessionPool.peek(tabId) ?: return
        val tab = current.tabs.firstOrNull { it.id == tabId } ?: return
        if (current.currentUrl.isEmpty() || current.progress == 0) {
            session.loadUri(tab.url)
        }
    }

    // ---------------------------------------------------------------- bookmarks

    fun toggleBookmark() {
        val current = _state.value
        val url = current.currentUrl.ifEmpty { return }
        viewModelScope.launch {
            if (current.isBookmarked) {
                bookmarkRepository.removeByUrl(url)
            } else {
                bookmarkRepository.add(current.pageTitle, url)
            }
            refreshBookmarkState(url)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    fun observeBookmarks() = bookmarkRepository.observeAll()

    private fun refreshBookmarkState(url: String) {
        bookmarkWatch?.cancel()
        bookmarkWatch = viewModelScope.launch {
            bookmarkRepository.observeIsBookmarked(url).collect { bookmarked ->
                _state.update { it.copy(isBookmarked = bookmarked) }
            }
        }
    }

    // ---------------------------------------------------------------- gecko delegates

    private fun attachDelegates(tabId: Long, session: GeckoSession) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!isActive(tabId)) return
                _state.update { it.copy(isLoading = true, progress = 0, currentUrl = url) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!isActive(tabId)) return
                _state.update { it.copy(isLoading = false, progress = 100) }
                persist(tabId)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                if (!isActive(tabId)) return
                _state.update { it.copy(progress = progress) }
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!isActive(tabId) || url == null) return
                _state.update { it.copy(currentUrl = url, addressText = url) }
                refreshBookmarkState(url)
                persist(tabId)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (!isActive(tabId)) return
                _state.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (!isActive(tabId)) return
                _state.update { it.copy(canGoForward = canGoForward) }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!isActive(tabId)) return
                _state.update { it.copy(pageTitle = title.orEmpty()) }
                persist(tabId)
            }

            /**
             * Gecko decided this response is a file rather than a page — a download link, a PDF,
             * a generated blob. It has already fetched it (through the tor proxy, in the tor
             * process) and hands us the open stream to drain.
             */
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                fileDownloader.enqueue(response)
            }
        }

        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt,
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                viewModelScope.launch {
                    val chosen = filePicker.pick(
                        FilePickerRequest(
                            mimeTypes = prompt.mimeTypes?.toList().orEmpty(),
                            // FOLDER has no equivalent in the document picker; letting the user
                            // pick several files is closer to the intent than refusing outright.
                            allowMultiple = prompt.type != GeckoSession.PromptDelegate.FilePrompt.Type.SINGLE,
                        ),
                    )
                    result.complete(
                        if (chosen.isEmpty()) {
                            prompt.dismiss()
                        } else {
                            prompt.confirm(appContext, chosen.toTypedArray())
                        },
                    )
                }
                return result
            }
        }
    }

    private fun isActive(tabId: Long): Boolean = _state.value.activeTabId == tabId

    private fun persist(tabId: Long) {
        val current = _state.value
        viewModelScope.launch {
            tabRepository.updateLocation(tabId, current.currentUrl, current.pageTitle)
        }
    }

    override fun onCleared() {
        sessionPool.releaseAll()
        wiredSessions.clear()
        super.onCleared()
    }

    /** Landing page for a new tab in this process. Google for direct, DuckDuckGo over tor. */
    private fun homeUrl(): String = SearchEngine.homeUrlFor(networkMode)

    companion object {

        fun factory(
            appContext: Context,
            networkMode: NetworkMode,
            containerRepository: ContainerRepository,
            tabRepository: TabRepository,
            bookmarkRepository: BookmarkRepository,
            sessionPool: GeckoSessionPool,
            filePicker: FilePickerCoordinator,
            fileDownloader: FileDownloader,
            runtimeHolder: GeckoRuntimeHolder,
            torStatus: StateFlow<TorStatus>,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = BrowserViewModel(
                appContext = appContext,
                networkMode = networkMode,
                containerRepository = containerRepository,
                tabRepository = tabRepository,
                bookmarkRepository = bookmarkRepository,
                sessionPool = sessionPool,
                filePicker = filePicker,
                fileDownloader = fileDownloader,
                runtimeHolder = runtimeHolder,
                torStatus = torStatus,
            ) as T
        }
    }
}
