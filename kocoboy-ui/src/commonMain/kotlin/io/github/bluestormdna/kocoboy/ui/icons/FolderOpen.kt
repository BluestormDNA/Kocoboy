package io.github.bluestormdna.kocoboy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FolderOpen: ImageVector
    get() {
        if (_FolderOpen != null) {
            return _FolderOpen!!
        }
        _FolderOpen = ImageVector.Builder(
            name = "FolderOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(160f, 800f)
                quadTo(127f, 800f, 103.5f, 776.5f)
                quadTo(80f, 753f, 80f, 720f)
                lineTo(80f, 240f)
                quadTo(80f, 207f, 103.5f, 183.5f)
                quadTo(127f, 160f, 160f, 160f)
                lineTo(400f, 160f)
                lineTo(480f, 240f)
                lineTo(800f, 240f)
                quadTo(833f, 240f, 856.5f, 263.5f)
                quadTo(880f, 287f, 880f, 320f)
                lineTo(447f, 320f)
                lineTo(367f, 240f)
                lineTo(160f, 240f)
                quadTo(160f, 240f, 160f, 240f)
                quadTo(160f, 240f, 160f, 240f)
                lineTo(160f, 720f)
                quadTo(160f, 720f, 160f, 720f)
                quadTo(160f, 720f, 160f, 720f)
                lineTo(256f, 400f)
                lineTo(940f, 400f)
                lineTo(837f, 743f)
                quadTo(829f, 769f, 807.5f, 784.5f)
                quadTo(786f, 800f, 760f, 800f)
                lineTo(160f, 800f)
                close()
                moveTo(244f, 720f)
                lineTo(760f, 720f)
                lineTo(832f, 480f)
                lineTo(316f, 480f)
                lineTo(244f, 720f)
                close()
                moveTo(244f, 720f)
                lineTo(316f, 480f)
                lineTo(316f, 480f)
                lineTo(244f, 720f)
                close()
                moveTo(160f, 320f)
                lineTo(160f, 240f)
                quadTo(160f, 240f, 160f, 240f)
                quadTo(160f, 240f, 160f, 240f)
                lineTo(160f, 240f)
                lineTo(160f, 320f)
                close()
            }
        }.build()

        return _FolderOpen!!
    }

@Suppress("ObjectPropertyName")
private var _FolderOpen: ImageVector? = null
