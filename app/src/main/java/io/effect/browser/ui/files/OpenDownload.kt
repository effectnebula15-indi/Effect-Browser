package io.effect.browser.ui.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast

/**
 * Hands a finished download to whatever app on the device can open it.
 *
 * Grants read permission on the URI for the duration of the launch: on API 26–28 the file lives
 * behind this app's FileProvider, and the receiving app has no access to it otherwise.
 */
fun openDownloadedFile(context: Context, uri: Uri) {
    val mimeType = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(uri.toString().substringAfterLast('.', ""))
        ?: "*/*"

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Launched from a non-Activity context in some call paths.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для этого файла", Toast.LENGTH_SHORT).show()
    } catch (t: Throwable) {
        Log.e("OpenDownload", "Could not open $uri", t)
        Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
    }
}
