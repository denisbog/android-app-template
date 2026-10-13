package com.example.template.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The drawer trigger: a **two-bar** glyph rather than a Material hamburger. Material's own icons (`Icons.Outlined.*`) are used for
 * everything else — see the sidebar for the full set.
 */
object DrawerIcons {

    val MenuIcon: ImageVector by lazy {
        ImageVector.Builder(
            name = "DrawerMenu",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // short bottom-left bar
                moveTo(13.19f, 17.958f)
                horizontalLineTo(3.074f)
                verticalLineTo(16.157f)
                horizontalLineTo(13.19f)
                verticalLineTo(17.958f)
                close()
                // full-width top bar
                moveTo(20.927f, 7.843f)
                horizontalLineTo(3.074f)
                verticalLineTo(6.042f)
                horizontalLineTo(20.927f)
                verticalLineTo(7.843f)
                close()
            }
        }.build()
    }
}
