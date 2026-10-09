package com.garetto.jukebox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Glyph {
    Play, Pause, Next, Prev, Shuffle, Loop, LoopOne, Back, Down,
    Sliders, Search, Close, Plus, Moon, Note, Heart, HeartFilled, More, Check,
}

/** Hand-drawn icons, so the app needs no icon library. */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color = Jx.Ink, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        val sw = s * 0.09f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun pt(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, pt(x1, y1), pt(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        }

        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
            val a = pt(ax, ay)
            val b = pt(bx, by)
            val c = pt(cx, cy)
            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
                close()
            }
            drawPath(path, tint)
            drawPath(path, tint, style = Stroke(width = sw * 0.9f, join = StrokeJoin.Round))
        }

        when (glyph) {
            Glyph.Play -> tri(0.32f, 0.22f, 0.32f, 0.78f, 0.8f, 0.5f)

            Glyph.Pause -> {
                val bar = Size(s * 0.17f, s * 0.6f)
                val r = CornerRadius(s * 0.05f, s * 0.05f)
                drawRoundRect(tint, pt(0.25f, 0.2f), bar, r)
                drawRoundRect(tint, pt(0.58f, 0.2f), bar, r)
            }

            Glyph.Next -> {
                tri(0.22f, 0.26f, 0.22f, 0.74f, 0.62f, 0.5f)
                line(0.78f, 0.24f, 0.78f, 0.76f)
            }

            Glyph.Prev -> {
                tri(0.78f, 0.26f, 0.78f, 0.74f, 0.38f, 0.5f)
                line(0.22f, 0.24f, 0.22f, 0.76f)
            }

            Glyph.Shuffle -> {
                val a = Path().apply {
                    val p0 = pt(0.12f, 0.3f)
                    val c1 = pt(0.45f, 0.3f)
                    val c2 = pt(0.5f, 0.7f)
                    val p1 = pt(0.84f, 0.7f)
                    moveTo(p0.x, p0.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                }
                val b = Path().apply {
                    val p0 = pt(0.12f, 0.7f)
                    val c1 = pt(0.45f, 0.7f)
                    val c2 = pt(0.5f, 0.3f)
                    val p1 = pt(0.84f, 0.3f)
                    moveTo(p0.x, p0.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
                }
                drawPath(a, tint, style = stroke)
                drawPath(b, tint, style = stroke)
                line(0.74f, 0.2f, 0.86f, 0.3f)
                line(0.74f, 0.4f, 0.86f, 0.3f)
                line(0.74f, 0.6f, 0.86f, 0.7f)
                line(0.74f, 0.8f, 0.86f, 0.7f)
            }

            Glyph.Loop, Glyph.LoopOne -> {
                val top = Path().apply {
                    val p0 = pt(0.16f, 0.5f)
                    val c = pt(0.16f, 0.3f)
                    val p1 = pt(0.36f, 0.3f)
                    val p2 = pt(0.8f, 0.3f)
                    moveTo(p0.x, p0.y)
                    quadraticBezierTo(c.x, c.y, p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                }
                val bottom = Path().apply {
                    val p0 = pt(0.84f, 0.5f)
                    val c = pt(0.84f, 0.7f)
                    val p1 = pt(0.64f, 0.7f)
                    val p2 = pt(0.2f, 0.7f)
                    moveTo(p0.x, p0.y)
                    quadraticBezierTo(c.x, c.y, p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                }
                drawPath(top, tint, style = stroke)
                drawPath(bottom, tint, style = stroke)
                line(0.7f, 0.2f, 0.82f, 0.3f)
                line(0.7f, 0.4f, 0.82f, 0.3f)
                line(0.3f, 0.6f, 0.18f, 0.7f)
                line(0.3f, 0.8f, 0.18f, 0.7f)
                if (glyph == Glyph.LoopOne) {
                    drawCircle(tint, radius = s * 0.07f, center = pt(0.5f, 0.5f))
                }
            }

            Glyph.Back -> {
                line(0.6f, 0.2f, 0.32f, 0.5f)
                line(0.32f, 0.5f, 0.6f, 0.8f)
            }

            Glyph.Down -> {
                line(0.2f, 0.38f, 0.5f, 0.66f)
                line(0.5f, 0.66f, 0.8f, 0.38f)
            }

            Glyph.Sliders -> {
                line(0.25f, 0.15f, 0.25f, 0.85f)
                line(0.5f, 0.15f, 0.5f, 0.85f)
                line(0.75f, 0.15f, 0.75f, 0.85f)
                drawCircle(tint, radius = s * 0.1f, center = pt(0.25f, 0.62f))
                drawCircle(tint, radius = s * 0.1f, center = pt(0.5f, 0.32f))
                drawCircle(tint, radius = s * 0.1f, center = pt(0.75f, 0.55f))
            }

            Glyph.Search -> {
                drawCircle(tint, radius = s * 0.25f, center = pt(0.44f, 0.44f), style = stroke)
                line(0.63f, 0.63f, 0.84f, 0.84f)
            }

            Glyph.Close -> {
                line(0.25f, 0.25f, 0.75f, 0.75f)
                line(0.75f, 0.25f, 0.25f, 0.75f)
            }

            Glyph.Plus -> {
                line(0.5f, 0.2f, 0.5f, 0.8f)
                line(0.2f, 0.5f, 0.8f, 0.5f)
            }

            Glyph.Moon -> {
                val full = Path().apply {
                    val c = pt(0.48f, 0.52f)
                    addOval(Rect(center = c, radius = s * 0.34f))
                }
                val bite = Path().apply {
                    val c = pt(0.66f, 0.38f)
                    addOval(Rect(center = c, radius = s * 0.3f))
                }
                drawPath(Path.combine(PathOperation.Difference, full, bite), tint)
            }

            Glyph.Heart, Glyph.HeartFilled -> {
                val heart = Path().apply {
                    fun m(x: Float, y: Float) = pt(x, y).let { moveTo(it.x, it.y) }
                    fun c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
                        val a = pt(x1, y1)
                        val b = pt(x2, y2)
                        val d = pt(x3, y3)
                        cubicTo(a.x, a.y, b.x, b.y, d.x, d.y)
                    }
                    m(0.5f, 0.84f)
                    c(0.04f, 0.56f, 0.12f, 0.18f, 0.34f, 0.18f)
                    c(0.44f, 0.18f, 0.5f, 0.26f, 0.5f, 0.33f)
                    c(0.5f, 0.26f, 0.56f, 0.18f, 0.66f, 0.18f)
                    c(0.88f, 0.18f, 0.96f, 0.56f, 0.5f, 0.84f)
                    close()
                }
                if (glyph == Glyph.HeartFilled) drawPath(heart, tint)
                drawPath(heart, tint, style = stroke)
            }

            Glyph.More -> {
                drawCircle(tint, radius = s * 0.075f, center = pt(0.5f, 0.22f))
                drawCircle(tint, radius = s * 0.075f, center = pt(0.5f, 0.5f))
                drawCircle(tint, radius = s * 0.075f, center = pt(0.5f, 0.78f))
            }

            Glyph.Check -> {
                line(0.2f, 0.52f, 0.42f, 0.74f)
                line(0.42f, 0.74f, 0.8f, 0.28f)
            }

            Glyph.Note -> {
                drawOval(tint, topLeft = pt(0.2f, 0.6f), size = Size(s * 0.3f, s * 0.24f))
                line(0.46f, 0.7f, 0.46f, 0.2f)
                drawLine(tint, pt(0.46f, 0.2f), pt(0.76f, 0.3f), strokeWidth = sw * 1.6f, cap = StrokeCap.Round)
            }
        }
    }
}
