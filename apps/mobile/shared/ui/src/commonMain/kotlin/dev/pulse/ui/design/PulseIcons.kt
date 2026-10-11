package dev.pulse.ui.design

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A tiny line-icon set (24x24, 1.8 stroke): no Material icon dependency, consistent weight. */
object PulseIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .path(
            stroke = SolidColor(androidx.compose.ui.graphics.Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    val Phone = icon("phone") {
        moveTo(5f, 4f); lineTo(9f, 4f); lineTo(10.5f, 8.5f); lineTo(8.2f, 10f)
        curveTo(9.3f, 12.4f, 11.6f, 14.7f, 14f, 15.8f); lineTo(15.5f, 13.5f); lineTo(20f, 15f); lineTo(20f, 19f)
        curveTo(20f, 19.6f, 19.6f, 20f, 19f, 20f); curveTo(11f, 20f, 4f, 13f, 4f, 5f); curveTo(4f, 4.4f, 4.4f, 4f, 5f, 4f); close()
    }
    val Navigate = icon("navigate") { moveTo(12f, 3f); lineTo(19f, 20f); lineTo(12f, 16f); lineTo(5f, 20f); close() }
    val Bookmark = icon("bookmark") { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16.5f); lineTo(7f, 20f); close() }
    val Share = icon("share") {
        moveTo(12f, 15f); lineTo(12f, 4f); moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        moveTo(6f, 12f); lineTo(6f, 19f); lineTo(18f, 19f); lineTo(18f, 12f)
    }
    val Ticket = icon("ticket") {
        moveTo(4f, 7f); lineTo(20f, 7f); lineTo(20f, 10f); curveTo(18.5f, 10f, 18.5f, 14f, 20f, 14f); lineTo(20f, 17f)
        lineTo(4f, 17f); lineTo(4f, 14f); curveTo(5.5f, 14f, 5.5f, 10f, 4f, 10f); close(); moveTo(14f, 7f); lineTo(14f, 17f)
    }
    val Play = icon("play") { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    val Mic = icon("mic") {
        moveTo(12f, 3f); curveTo(10.3f, 3f, 9f, 4.3f, 9f, 6f); lineTo(9f, 11f); curveTo(9f, 12.7f, 10.3f, 14f, 12f, 14f)
        curveTo(13.7f, 14f, 15f, 12.7f, 15f, 11f); lineTo(15f, 6f); curveTo(15f, 4.3f, 13.7f, 3f, 12f, 3f); close()
        moveTo(6f, 11f); curveTo(6f, 14.3f, 8.7f, 17f, 12f, 17f); curveTo(15.3f, 17f, 18f, 14.3f, 18f, 11f); moveTo(12f, 17f); lineTo(12f, 21f)
    }
    val Camera = icon("camera") {
        moveTo(4f, 8f); lineTo(8f, 8f); lineTo(9.5f, 5.5f); lineTo(14.5f, 5.5f); lineTo(16f, 8f); lineTo(20f, 8f); lineTo(20f, 19f); lineTo(4f, 19f); close()
        moveTo(15.5f, 13f); curveTo(15.5f, 14.9f, 13.9f, 16.5f, 12f, 16.5f); curveTo(10.1f, 16.5f, 8.5f, 14.9f, 8.5f, 13f)
        curveTo(8.5f, 11.1f, 10.1f, 9.5f, 12f, 9.5f); curveTo(13.9f, 9.5f, 15.5f, 11.1f, 15.5f, 13f); close()
    }
    val Spark = icon("spark") {
        moveTo(12f, 3f); lineTo(13.8f, 10.2f); lineTo(21f, 12f); lineTo(13.8f, 13.8f); lineTo(12f, 21f); lineTo(10.2f, 13.8f); lineTo(3f, 12f); lineTo(10.2f, 10.2f); close()
    }
    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
}
