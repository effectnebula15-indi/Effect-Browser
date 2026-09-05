package io.effect.browser.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.effect.browser.domain.model.Container
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.ui.components.BookmarksSheet
import io.effect.browser.ui.components.BrowserBottomBar
import io.effect.browser.ui.components.ContainerChips
import io.effect.browser.ui.components.ContainerEditorSheet
import io.effect.browser.ui.components.TabsSheet
import io.effect.browser.ui.components.TorStatusBanner

@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bookmarks by remember { viewModel.observeBookmarks() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var editingContainer by remember { mutableStateOf<Container?>(null) }
    var showContainerEditor by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }

    // Tor can finish bootstrapping long after the tab was opened; pick up where we left off.
    LaunchedEffect(state.canNavigate) {
        if (state.canNavigate) viewModel.onNavigationUnblocked()
    }

    val keyboard = LocalSoftwareKeyboardController.current
    val activeContainer = state.activeContainer
    val containerTabs = state.tabs.filter { it.containerId == activeContainer?.id }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {

            if (state.networkMode == NetworkMode.TOR) {
                TorStatusBanner(
                    status = state.torStatus,
                    modifier = Modifier.statusBarsPadding(),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val tabId = state.activeTabId
                if (tabId != null && activeContainer != null) {
                    val session = remember(tabId, activeContainer.id) {
                        viewModel.sessionFor(tabId, activeContainer)
                    }
                    GeckoViewHost(session = session, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        text = "No container selected",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }

            // Everything below here is deliberately at the bottom of the screen: address bar,
            // container ribbon and controls all sit within thumb reach.
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                OutlinedTextField(
                    value = state.addressText,
                    onValueChange = viewModel::onAddressChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text(addressHint(state)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            viewModel.onAddressSubmitted()
                            keyboard?.hide()
                        },
                    ),
                )

                ContainerChips(
                    containers = state.containers,
                    activeContainerId = activeContainer?.id,
                    onSelect = viewModel::onContainerSelected,
                    onLongPress = { container ->
                        editingContainer = container
                        showContainerEditor = true
                    },
                    onAddContainer = {
                        editingContainer = null
                        showContainerEditor = true
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                BrowserBottomBar(
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    isLoading = state.isLoading,
                    isBookmarked = state.isBookmarked,
                    tabCount = containerTabs.size,
                    onBack = viewModel::goBack,
                    onForward = viewModel::goForward,
                    onReloadOrStop = {
                        if (state.isLoading) viewModel.stopLoading() else viewModel.reload()
                    },
                    onToggleBookmark = viewModel::toggleBookmark,
                    onShowTabs = { showTabs = true },
                    onShowBookmarks = { showBookmarks = true },
                )
            }
        }
    }

    if (showContainerEditor) {
        val editing = editingContainer
        ContainerEditorSheet(
            existing = editing,
            onDismiss = { showContainerEditor = false },
            onSave = { name, color, mode ->
                if (editing == null) {
                    viewModel.createContainer(name, color, mode)
                } else {
                    viewModel.updateContainer(editing.id, name, color, mode)
                }
                showContainerEditor = false
            },
            onDelete = editing?.let { container ->
                {
                    viewModel.deleteContainer(container.id)
                    showContainerEditor = false
                }
            },
        )
    }

    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            onOpen = { bookmark ->
                viewModel.navigateTo(bookmark.url)
                showBookmarks = false
            },
            onDelete = { viewModel.deleteBookmark(it.id) },
            onDismiss = { showBookmarks = false },
        )
    }

    if (showTabs) {
        TabsSheet(
            tabs = containerTabs,
            activeTabId = state.activeTabId,
            onSelect = { tab ->
                viewModel.selectTab(tab.id)
                showTabs = false
            },
            onClose = { viewModel.closeTab(it.id) },
            onNewTab = {
                viewModel.openNewTab()
                showTabs = false
            },
            onDismiss = { showTabs = false },
        )
    }
}

private fun addressHint(state: BrowserUiState): String = when {
    !state.canNavigate && state.networkMode == NetworkMode.TOR -> "Waiting for Tor…"
    state.networkMode == NetworkMode.TOR -> "Search DuckDuckGo or enter address"
    else -> "Search Google or enter address"
}
