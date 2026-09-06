package io.effect.browser.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.effect.browser.di.ServiceLocator
import io.effect.browser.domain.model.NetworkMode
import io.effect.browser.ui.browser.BrowserEffect
import io.effect.browser.ui.browser.BrowserScreen
import io.effect.browser.ui.browser.BrowserViewModel
import io.effect.browser.ui.theme.EffectBrowserTheme
import kotlinx.coroutines.launch

/**
 * Shared browser UI.
 *
 * Subclassed rather than parameterised because the two subclasses are declared in different
 * processes in the manifest — that declaration is what gives tor containers their own
 * `GeckoRuntime`, and therefore their own proxy configuration.
 */
abstract class BrowserActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels {
        BrowserViewModel.factory(
            appContext = applicationContext,
            networkMode = ServiceLocator.networkMode,
            containerRepository = ServiceLocator.containerRepository,
            tabRepository = ServiceLocator.tabRepository,
            bookmarkRepository = ServiceLocator.bookmarkRepository,
            sessionPool = ServiceLocator.sessionPool,
            filePicker = ServiceLocator.filePicker,
            fileDownloader = ServiceLocator.fileDownloader,
            runtimeHolder = ServiceLocator.runtimeHolder,
            torStatus = ServiceLocator.torStatus,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is BrowserEffect.SwitchProcess -> switchProcess(effect)
                    }
                }
            }
        }

        setContent {
            EffectBrowserTheme {
                BrowserScreen(
                    viewModel = viewModel,
                    filePicker = ServiceLocator.filePicker,
                    downloadEvents = ServiceLocator.fileDownloader.events,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val containerId = intent.getLongExtra(EXTRA_CONTAINER_ID, NO_CONTAINER)
        if (containerId != NO_CONTAINER) {
            viewModel.requestContainer(containerId)
        }
    }

    /** Hands the container off to the activity that lives in the process able to render it. */
    private fun switchProcess(effect: BrowserEffect.SwitchProcess) {
        val target = when (effect.target) {
            NetworkMode.DIRECT -> MainActivity::class.java
            NetworkMode.TOR -> TorBrowserActivity::class.java
        }
        startActivity(
            Intent(this, target).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_CONTAINER_ID, effect.containerId)
            },
        )
    }

    companion object {
        const val EXTRA_CONTAINER_ID = "io.effect.browser.extra.CONTAINER_ID"
        private const val NO_CONTAINER = -1L
    }
}

/** Runs in the default process. Serves every [NetworkMode.DIRECT] container. */
class MainActivity : BrowserActivity()

/**
 * Runs in the `:tor` process (see AndroidManifest.xml). Serves every [NetworkMode.TOR] container.
 *
 * Its `GeckoRuntime` is created with SOCKS proxy preferences pointed at the embedded tor daemon
 * and `network.proxy.failover_direct = false`, so a request in this process cannot fall back to
 * a direct connection.
 */
class TorBrowserActivity : BrowserActivity()
