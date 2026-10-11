package dev.pulse.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cuboctahedron: 12 vertices (all permutations of (+/-1, +/-1, 0)), 8 triangles and 6 squares.
 *
 * Why this solid: it's convex (so back-to-front face sorting is *exact*, and translucency needs
 * no order-independent transparency), has only 14 faces (a few hundred vertices per frame on the
 * CPU, ~0.1 ms), and its squares echo the rounded glass cards while its triangles catch light.
 */
internal object Cuboctahedron {

    data class V3(val x: Double, val y: Double, val z: Double) {
        operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        operator fun div(d: Double) = V3(x / d, y / d, z / d)
        fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        fun normalized(): V3 = sqrt(dot(this)).let { V3(x / it, y / it, z / it) }
    }

    class Face(val vertices: List<V3>, val isSquare: Boolean)

    val faces: List<Face> = buildList {
        // 6 squares, one per axis direction, vertices in cyclic order around the face.
        for (axis in 0..2) for (s in listOf(1.0, -1.0)) {
            val ring = listOf(1.0 to 0.0, 0.0 to 1.0, -1.0 to 0.0, 0.0 to -1.0)
            add(Face(ring.map { (b, c) -> place(axis, s, b, c) }, isSquare = true))
        }
        // 8 triangles, one per octant.
        for (sx in listOf(1.0, -1.0)) for (sy in listOf(1.0, -1.0)) for (sz in listOf(1.0, -1.0)) {
            add(Face(listOf(V3(sx, sy, 0.0), V3(sx, 0.0, sz), V3(0.0, sy, sz)), isSquare = false))
        }
    }

    /** Vertex on the face where coordinate [axis] == [s], with the other two coordinates (b, c). */
    private fun place(axis: Int, s: Double, b: Double, c: Double): V3 = when (axis) {
        0 -> V3(s, b, c)
        1 -> V3(c, s, b)
        else -> V3(b, c, s)
    }

    private val light = V3(-0.45, 0.65, 0.75).normalized()

    /**
     * Draws the solid rotated by [yawDeg] / [pitchDeg] with a simple perspective camera.
     * Faces are painted far-to-near; back faces stay faint so the solid reads as tinted glass.
     */
    fun DrawScope.drawCuboctahedron(
        yawDeg: Float,
        pitchDeg: Float,
        squareTint: Color,
        triangleTint: Color,
        edge: Color,
        scale: Float = 0.30f,
        center: Offset = this.center,
    ) {
        val yaw = yawDeg * PI_180
        val pitch = pitchDeg * PI_180
        fun rotate(v: V3): V3 {
            val x1 = v.x * cos(yaw) + v.z * sin(yaw)
            val z1 = -v.x * sin(yaw) + v.z * cos(yaw)
            val y2 = v.y * cos(pitch) - z1 * sin(pitch)
            val z2 = v.y * sin(pitch) + z1 * cos(pitch)
            return V3(x1, y2, z2)
        }
        val s = size.minDimension * scale
        val camera = 4.5
        fun project(v: V3): Offset {
            val k = camera / (camera - v.z)
            return Offset(center.x + (v.x * s * k).toFloat(), center.y - (v.y * s * k).toFloat())
        }

        val rotated = faces.map { f -> f to f.vertices.map(::rotate) }
            .sortedBy { (_, vs) -> vs.sumOf { it.z } / vs.size }

        for ((face, vs) in rotated) {
            val normal = (vs.reduce { a, b -> a + b } / vs.size.toDouble()).normalized()
            val front = normal.z > 0
            val lambert = normal.dot(light).coerceAtLeast(0.0).toFloat()
            val tint = if (face.isSquare) squareTint else triangleTint
            val pts = vs.map(::project)
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                close()
            }
            val alpha = if (front) 0.18f + 0.42f * lambert else 0.07f
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(tint.copy(alpha = (alpha + 0.15f).coerceAtMost(1f)), tint.copy(alpha = alpha * 0.5f)),
                    start = pts[0], end = pts[pts.size / 2],
                ),
            )
            drawPath(path, edge.copy(alpha = if (front) 0.35f + 0.4f * lambert else 0.12f), style = Stroke(width = (s / 90f).coerceAtLeast(1f)))
            if (front && lambert > 0.85f) {
                val c = Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), c, s * 0.35f), s * 0.35f, c)
            }
        }
    }

    private const val PI_180 = kotlin.math.PI / 180.0
}
