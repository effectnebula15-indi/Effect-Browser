package io.effect.browser.ui.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.effect.browser.files.FilePickerCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Wires the system document picker to page file prompts for as long as this screen is composed.
 *
 * Renders nothing. The launchers have to be registered during composition (that is how the
 * Activity Result API works), so this exists purely to own them and to publish a handler the
 * ViewModel's prompt delegate can suspend on.
 */
@Composable
fun FilePickerHost(coordinator: FilePickerCoordinator) {
    val pending = remember { AtomicReference<CompletableDeferred<List<Uri>>?>(null) }

    fun deliver(uris: List<Uri>) {
        pending.getAndSet(null)?.complete(uris)
    }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        deliver(listOfNotNull(uri))
    }
    val pickMany = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        deliver(uris)
    }

    DisposableEffect(coordinator) {
        coordinator.setHandler { request ->
            val deferred = CompletableDeferred<List<Uri>>()
            // A second prompt while one is open would otherwise strand the first forever.
            pending.getAndSet(deferred)?.complete(emptyList())

            val types = request.mimeTypes
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(ANY_TYPE) }
                .toTypedArray()

            withContext(Dispatchers.Main) {
                if (request.allowMultiple) pickMany.launch(types) else pickOne.launch(types)
            }
            deferred.await()
        }

        onDispose {
            coordinator.setHandler(null)
            // Leaving the screen cancels the prompt rather than hanging the page.
            deliver(emptyList())
        }
    }
}

private const val ANY_TYPE = "*/*"
