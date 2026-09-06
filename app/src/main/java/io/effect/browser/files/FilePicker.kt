package io.effect.browser.files

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference

/** What a page asked for when it opened an `<input type="file">`. */
data class FilePickerRequest(
    /** MIME types from the `accept` attribute. Empty means "anything". */
    val mimeTypes: List<String>,
    val allowMultiple: Boolean,
)

/**
 * Bridges a page's file prompt to the Android document picker.
 *
 * GeckoView hands the prompt to a delegate living in the ViewModel, but only an Activity can
 * launch a picker and receive its result. This sits between them: the UI installs a handler
 * while it is on screen, and the delegate suspends on [pick] until the user chooses.
 */
class FilePickerCoordinator {

    fun interface Handler {
        suspend fun pick(request: FilePickerRequest): List<Uri>
    }

    private val handler = AtomicReference<Handler?>(null)

    fun setHandler(value: Handler?) {
        handler.set(value)
    }

    /**
     * Returns the chosen files, or an empty list if the user cancelled — or if no UI is
     * currently attached to show a picker with.
     */
    suspend fun pick(request: FilePickerRequest): List<Uri> =
        handler.get()?.pick(request).orEmpty()
}
