package io.effect.browser.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A padlock, drawn inline.
 *
 * Defined here rather than pulled from `material-icons-core` so the app does not take a
 * dependency on the whole icon set to show one 14dp glyph.
 */
val TorGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "TorLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            // Shackle
            moveTo(12f, 2f)
            curveTo(9.24f, 2f, 7f, 4.24f, 7f, 7f)
            verticalLineTo(10f)
            horizontalLineTo(9f)
            verticalLineTo(7f)
            curveTo(9f, 5.34f, 10.34f, 4f, 12f, 4f)
            curveTo(13.66f, 4f, 15f, 5.34f, 15f, 7f)
            verticalLineTo(10f)
            horizontalLineTo(17f)
            verticalLineTo(7f)
            curveTo(17f, 4.24f, 14.76f, 2f, 12f, 2f)
            close()
            // Body
            moveTo(6f, 10f)
            horizontalLineTo(18f)
            curveTo(19.1f, 10f, 20f, 10.9f, 20f, 12f)
            verticalLineTo(20f)
            curveTo(20f, 21.1f, 19.1f, 22f, 18f, 22f)
            horizontalLineTo(6f)
            curveTo(4.9f, 22f, 4f, 21.1f, 4f, 20f)
            verticalLineTo(12f)
            curveTo(4f, 10.9f, 4.9f, 10f, 6f, 10f)
            close()
        }
    }.build()
}

private fun glyph(name: String, build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White), pathBuilder = build)
    }.build()

val BackGlyph: ImageVector by lazy {
    glyph("Back") {
        moveTo(15.4f, 4.6f)
        lineTo(13.98f, 3.2f)
        lineTo(5.18f, 12f)
        lineTo(13.98f, 20.8f)
        lineTo(15.4f, 19.4f)
        lineTo(8f, 12f)
        close()
    }
}

val ForwardGlyph: ImageVector by lazy {
    glyph("Forward") {
        moveTo(8.6f, 4.6f)
        lineTo(10.02f, 3.2f)
        lineTo(18.82f, 12f)
        lineTo(10.02f, 20.8f)
        lineTo(8.6f, 19.4f)
        lineTo(16f, 12f)
        close()
    }
}

val ReloadGlyph: ImageVector by lazy {
    glyph("Reload") {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
        curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
        curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
        horizontalLineTo(17.65f)
        curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
        curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
        curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
        curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
        lineTo(13f, 11f)
        horizontalLineTo(20f)
        verticalLineTo(4f)
        close()
    }
}

val StopGlyph: ImageVector by lazy {
    glyph("Stop") {
        moveTo(6f, 6f)
        horizontalLineTo(18f)
        verticalLineTo(18f)
        horizontalLineTo(6f)
        close()
    }
}

val StarGlyph: ImageVector by lazy {
    glyph("Star") {
        moveTo(12f, 17.27f)
        lineTo(18.18f, 21f)
        lineTo(16.54f, 13.97f)
        lineTo(22f, 9.24f)
        lineTo(14.81f, 8.63f)
        lineTo(12f, 2f)
        lineTo(9.19f, 8.63f)
        lineTo(2f, 9.24f)
        lineTo(7.46f, 13.97f)
        lineTo(5.82f, 21f)
        close()
    }
}

val TabsGlyph: ImageVector by lazy {
    glyph("Tabs") {
        moveTo(3f, 5f)
        horizontalLineTo(15f)
        verticalLineTo(17f)
        horizontalLineTo(3f)
        close()
        moveTo(17f, 7f)
        horizontalLineTo(21f)
        verticalLineTo(19f)
        horizontalLineTo(9f)
        verticalLineTo(19f)
        horizontalLineTo(17f)
        close()
    }
}

val CloseGlyph: ImageVector by lazy {
    glyph("Close") {
        moveTo(19f, 6.41f)
        lineTo(17.59f, 5f)
        lineTo(12f, 10.59f)
        lineTo(6.41f, 5f)
        lineTo(5f, 6.41f)
        lineTo(10.59f, 12f)
        lineTo(5f, 17.59f)
        lineTo(6.41f, 19f)
        lineTo(12f, 13.41f)
        lineTo(17.59f, 19f)
        lineTo(19f, 17.59f)
        lineTo(13.41f, 12f)
        close()
    }
}

val ListGlyph: ImageVector by lazy {
    glyph("List") {
        moveTo(4f, 6f)
        horizontalLineTo(20f)
        verticalLineTo(8f)
        horizontalLineTo(4f)
        close()
        moveTo(4f, 11f)
        horizontalLineTo(20f)
        verticalLineTo(13f)
        horizontalLineTo(4f)
        close()
        moveTo(4f, 16f)
        horizontalLineTo(20f)
        verticalLineTo(18f)
        horizontalLineTo(4f)
        close()
    }
}
