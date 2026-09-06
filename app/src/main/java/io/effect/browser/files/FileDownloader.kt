package io.effect.browser.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** What happened to a download, for the UI to report. */
sealed interface DownloadEvent {
    val fileName: String

    data class Started(override val fileName: String) : DownloadEvent
    data class Completed(override val fileName: String, val uri: Uri, val bytes: Long) : DownloadEvent
    data class Failed(override val fileName: String, val reason: String) : DownloadEvent
}

/**
 * Saves files that Gecko hands over via `ContentDelegate.onExternalResponse`.
 *
 * ## Why the response body and not the URL
 *
 * Gecko has already fetched the response — through the tor SOCKS proxy, when this runs in the
 * tor process — and gives us the open stream. Re-requesting [WebResponse.uri] ourselves with an
 * ordinary HTTP client would send that request outside Gecko's proxy configuration entirely,
 * which for a tor container means downloading the file in the clear from the user's real IP.
 * So this only ever drains the stream it was given; it must stay that way.
 */
class FileDownloader(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun enqueue(response: WebResponse) {
        val fileName = DownloadNaming.fileNameFor(
            url = response.uri,
            contentDisposition = response.header("Content-Disposition"),
            mimeType = response.header("Content-Type"),
        )
        scope.launch { save(response, fileName) }
    }

    private suspend fun save(response: WebResponse, fileName: String) {
        _events.emit(DownloadEvent.Started(fileName))

        val body = response.body
        if (body == null) {
            _events.emit(DownloadEvent.Failed(fileName, "empty response"))
            return
        }

        val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim()

        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                // The stream can stall on a slow circuit; without a timeout a dead download
                // would hold the coroutine forever.
                response.setReadTimeoutMillis(READ_TIMEOUT_MS)
                body.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveToMediaStore(stream, fileName, mimeType)
                    } else {
                        saveToAppDirectory(stream, fileName)
                    }
                }
            }
        }

        outcome.fold(
            onSuccess = { (uri, bytes) -> _events.emit(DownloadEvent.Completed(fileName, uri, bytes)) },
            onFailure = { error ->
                Log.e(TAG, "Download failed: $fileName", error)
                _events.emit(
                    DownloadEvent.Failed(fileName, error.message ?: error::class.java.simpleName),
                )
            },
        )
    }

    /**
     * API 29+: hand the file to MediaStore so it lands in the system Downloads collection and
     * shows up in the user's file manager. Needs no storage permission.
     */
    private fun saveToMediaStore(
        stream: InputStream,
        fileName: String,
        mimeType: String?,
    ): Pair<Uri, Long> {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            if (!mimeType.isNullOrBlank()) put(MediaStore.Downloads.MIME_TYPE, mimeType)
            // Hides the half-written file from other apps until the copy finishes.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, pending)
            ?: error("MediaStore refused to create an entry for $fileName")

        val bytes = try {
            resolver.openOutputStream(uri)?.use { out -> stream.copyToCounting(out) }
                ?: error("Could not open $uri for writing")
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }

        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return uri to bytes
    }

    /**
     * API 26–28: MediaStore.Downloads does not exist yet, and writing to the public Downloads
     * folder would need WRITE_EXTERNAL_STORAGE. The app's own external directory needs no
     * permission at all, and FileProvider still makes the result openable by other apps.
     */
    private fun saveToAppDirectory(stream: InputStream, fileName: String): Pair<Uri, Long> {
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, Environment.DIRECTORY_DOWNLOADS).apply { mkdirs() }

        val target = uniqueFile(directory, fileName)
        val bytes = target.outputStream().use { out -> stream.copyToCounting(out) }

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", target)
        return uri to bytes
    }

    /** Avoids clobbering an existing download: report.pdf, report (1).pdf, ... */
    private fun uniqueFile(directory: File, fileName: String): File {
        directory.mkdirs()
        val candidate = File(directory, fileName)
        if (!candidate.exists()) return candidate

        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        val suffix = if (extension.isEmpty()) "" else ".$extension"

        var index = 1
        while (true) {
            val next = File(directory, "$base ($index)$suffix")
            if (!next.exists()) return next
            index++
        }
    }

    private fun InputStream.copyToCounting(out: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        out.flush()
        return total
    }

    /** Response headers are case-insensitive; the map Gecko gives us is not. */
    private fun WebResponse.header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private companion object {
        const val TAG = "FileDownloader"
        const val READ_TIMEOUT_MS = 60_000L
    }
}
