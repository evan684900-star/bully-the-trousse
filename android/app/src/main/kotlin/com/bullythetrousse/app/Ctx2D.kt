package com.bullythetrousse.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Une petite API "canvas 2D" (celle du navigateur) posée sur un [DrawScope].
 *
 * Le monde Ville du site, c'est ~2 000 lignes de `ctx.fillStyle = ...;
 * ctx.fillRect(...)`. Les réécrire en appels Compose un par un multiplierait
 * les occasions de se tromper (angles en degrés contre radians, pivot de
 * rotation implicite au centre, cercle complet qui devient vide avec
 * `arcTo`...). Cette classe reprend le modèle du canvas — un état courant
 * (couleurs, épaisseur, police, alpha) et une pile save()/restore() — pour
 * que le portage reste lisible ligne à ligne face au JS.
 *
 * Les transformations passent directement par le canvas sous-jacent, donc
 * tous les appels Compose faits entre save() et restore() (le sprite de la
 * trousse par exemple) les subissent aussi.
 */
internal class Ctx2D(val ds: DrawScope, private val texts: TextMeasurer?) {
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Baseline { TOP, MIDDLE, ALPHABETIC, BOTTOM }

    /** `ctx.font` : taille en px du repère courant, graisse, famille. */
    data class Font(val size: Float, val weight: FontWeight = FontWeight.Normal, val family: FontFamily = FontFamily.SansSerif)

    private class State(
        val fill: Brush, val stroke: Brush, val lineWidth: Float, val alpha: Float, val lineCap: StrokeCap,
        val dash: PathEffect?, val blend: BlendMode, val font: Font, val align: Align, val baseline: Baseline,
        val shadow: Shadow?,
    )

    var fill: Brush = SolidColor(Color.Black)
    var stroke: Brush = SolidColor(Color.Black)
    var lineWidth = 1f
    /** `globalAlpha`. */
    var alpha = 1f
    var lineCap = StrokeCap.Butt
    var dash: PathEffect? = null
    /** `globalCompositeOperation` ("lighter" = [BlendMode.Plus]). */
    var blend = BlendMode.SrcOver
    var font = Font(10f)
    var textAlign = Align.LEFT
    var textBaseline = Baseline.ALPHABETIC
    /** Ombre portée du texte seulement (shadowColor/shadowBlur). */
    var textShadow: Shadow? = null

    private val stack = ArrayList<State>()
    private var path = Path()
    private val canvas get() = ds.drawContext.canvas

    /* ---- Couleurs ---- */
    fun fill(color: Color) { fill = SolidColor(color) }
    fun stroke(color: Color) { stroke = SolidColor(color) }

    fun linearGradient(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Pair<Float, Color>): Brush =
        Brush.linearGradient(colorStops = stops, start = Offset(x0, y0), end = Offset(x1, y1))

    /**
     * `createRadialGradient(x, y, r0, x, y, r1)` à centre commun : Compose
     * n'a pas de rayon intérieur, on le rend par un arrêt de couleur à r0/r1.
     */
    fun radialGradient(x: Float, y: Float, r0: Float, r1: Float, vararg stops: Pair<Float, Color>): Brush {
        val radius = maxOf(r1, 0.01f)
        val inner = (r0 / radius).coerceIn(0f, 1f)
        val mapped = stops.map { (at, c) -> (inner + (1 - inner) * at) to c }.toMutableList()
        if (inner > 0f) mapped.add(0, 0f to stops.first().second)
        return Brush.radialGradient(colorStops = mapped.toTypedArray(), center = Offset(x, y), radius = radius)
    }

    /* ---- Pile d'état ---- */
    fun save() {
        stack += State(fill, stroke, lineWidth, alpha, lineCap, dash, blend, font, textAlign, textBaseline, textShadow)
        canvas.save()
    }

    fun restore() {
        canvas.restore()
        val s = stack.removeLastOrNull() ?: return
        fill = s.fill; stroke = s.stroke; lineWidth = s.lineWidth; alpha = s.alpha; lineCap = s.lineCap
        dash = s.dash; blend = s.blend; font = s.font; textAlign = s.align; textBaseline = s.baseline; textShadow = s.shadow
    }

    inline fun saved(block: () -> Unit) {
        save()
        try { block() } finally { restore() }
    }

    fun translate(x: Float, y: Float) = canvas.translate(x, y)
    fun rotate(radians: Float) = canvas.rotate(radians * 180f / PI.toFloat())
    fun scale(sx: Float, sy: Float) = canvas.scale(sx, sy)

    /* ---- Rectangles ---- */
    fun fillRect(x: Float, y: Float, w: Float, h: Float) {
        val (l, r) = if (w < 0) (x + w) to x else x to (x + w)
        val (t, b) = if (h < 0) (y + h) to y else y to (y + h)
        if (r - l <= 0f || b - t <= 0f) return
        ds.drawRect(fill, Offset(l, t), Size(r - l, b - t), alpha, Fill, blendMode = blend)
    }

    fun strokeRect(x: Float, y: Float, w: Float, h: Float) {
        ds.drawRect(stroke, Offset(x, y), Size(w, h), alpha, strokeStyle(), blendMode = blend)
    }

    /** `rr()` du site suivi de fill() : rectangle aux coins arrondis. */
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        if (w <= 0f || h <= 0f) return
        val rr = min(r, min(w / 2, h / 2))
        ds.drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(rr, rr), alpha = alpha, style = Fill, blendMode = blend)
    }

    fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        if (w <= 0f || h <= 0f) return
        val rr = min(r, min(w / 2, h / 2))
        ds.drawRoundRect(stroke, Offset(x, y), Size(w, h), CornerRadius(rr, rr), alpha = alpha, style = strokeStyle(), blendMode = blend)
    }

    /* ---- Cercles / ellipses immédiats ---- */
    fun fillCircle(x: Float, y: Float, r: Float) {
        if (r <= 0f) return
        ds.drawCircle(fill, r, Offset(x, y), alpha, Fill, blendMode = blend)
    }

    fun strokeCircle(x: Float, y: Float, r: Float) {
        if (r <= 0f) return
        ds.drawCircle(stroke, r, Offset(x, y), alpha, strokeStyle(), blendMode = blend)
    }

    /** `ellipse(x, y, rx, ry, rot, 0, 2π)` puis fill(). */
    fun fillEllipse(x: Float, y: Float, rx: Float, ry: Float, rotation: Float = 0f) {
        if (rx <= 0f || ry <= 0f) return
        if (rotation == 0f) {
            ds.drawOval(fill, Offset(x - rx, y - ry), Size(rx * 2, ry * 2), alpha, Fill, blendMode = blend)
        } else {
            beginPath(); ellipse(x, y, rx, ry, rotation); fill()
        }
    }

    fun strokeEllipse(x: Float, y: Float, rx: Float, ry: Float, rotation: Float = 0f) {
        beginPath(); ellipse(x, y, rx, ry, rotation); stroke()
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        ds.drawLine(stroke, Offset(x0, y0), Offset(x1, y1), lineWidth, lineCap, dash, alpha, blendMode = blend)
    }

    /* ---- Chemins ---- */
    fun beginPath() { path = Path() }
    fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    fun lineTo(x: Float, y: Float) {
        if (path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
    }
    fun quadraticCurveTo(cx: Float, cy: Float, x: Float, y: Float) = path.quadraticBezierTo(cx, cy, x, y)
    fun bezierCurveTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) = path.cubicTo(c1x, c1y, c2x, c2y, x, y)
    fun closePath() = path.close()
    fun rect(x: Float, y: Float, w: Float, h: Float) = path.addRect(Rect(x, y, x + w, y + h))
    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val rr = min(r, min(w / 2, h / 2))
        path.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(rr, rr)))
    }

    /**
     * `arc(x, y, r, a0, a1, ccw)` : angles en radians, sens horaire (y vers
     * le bas), relié au point courant par un segment comme sur le canvas. Un
     * tour complet devient un ovale : `arcTo` d'Android ramène 360° à 0°.
     */
    fun arc(x: Float, y: Float, r: Float, a0: Float, a1: Float, ccw: Boolean = false) {
        if (r <= 0f) return
        val full = 2 * PI.toFloat()
        var sweep = a1 - a0
        if (!ccw) {
            if (sweep >= full) { path.addOval(Rect(x - r, y - r, x + r, y + r)); return }
            while (sweep < 0) sweep += full
        } else {
            if (-sweep >= full) { path.addOval(Rect(x - r, y - r, x + r, y + r)); return }
            while (sweep > 0) sweep -= full
        }
        path.arcTo(Rect(x - r, y - r, x + r, y + r), a0 * 180f / PI.toFloat(), sweep * 180f / PI.toFloat(), false)
    }

    /** Ellipse complète (éventuellement tournée), ajoutée au chemin courant. */
    fun ellipse(x: Float, y: Float, rx: Float, ry: Float, rotation: Float = 0f) {
        if (rotation == 0f) {
            path.addOval(Rect(x - rx, y - ry, x + rx, y + ry))
            return
        }
        val c = cos(rotation)
        val s = sin(rotation)
        val steps = 28
        for (i in 0..steps) {
            val a = i * 2 * PI.toFloat() / steps
            val ex = cos(a) * rx
            val ey = sin(a) * ry
            val px = x + ex * c - ey * s
            val py = y + ex * s + ey * c
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }

    fun fill() = ds.drawPath(path, fill, alpha, Fill, blendMode = blend)
    fun stroke() = ds.drawPath(path, stroke, alpha, strokeStyle(), blendMode = blend)
    fun clip() = canvas.clipPath(path)
    fun clipRect(x: Float, y: Float, w: Float, h: Float) = canvas.clipRect(x, y, x + w, y + h)

    private fun strokeStyle() = Stroke(width = lineWidth, cap = lineCap, pathEffect = dash)

    /* ---- Texte ---- */
    private fun layout(text: String): TextLayoutResult {
        // La taille est exprimée dans le repère courant : on la convertit en
        // sp sans que la densité ni l'échelle de police du téléphone ne la
        // modifient (le canvas se charge lui-même de la mise à l'échelle).
        val size = (font.size / (ds.density * ds.fontScale)).sp
        val measurer = checkNotNull(texts) { "Ctx2D sans TextMeasurer : pas de texte possible" }
        return measurer.measure(
            text,
            style = TextStyle(fontSize = size, fontWeight = font.weight, fontFamily = font.family),
            overflow = TextOverflow.Visible,
            softWrap = false,
            maxLines = 1,
        )
    }

    fun measureText(text: String): Float = layout(text).size.width.toFloat()

    fun fillText(text: String, x: Float, y: Float) = drawTextAt(text, x, y, null)

    fun strokeText(text: String, x: Float, y: Float) = drawTextAt(text, x, y, strokeStyle())

    private fun drawTextAt(text: String, x: Float, y: Float, style: Stroke?) {
        if (text.isEmpty()) return
        val l = layout(text)
        val w = l.size.width.toFloat()
        val h = l.size.height.toFloat()
        val dx = when (textAlign) { Align.LEFT -> 0f; Align.CENTER -> -w / 2; Align.RIGHT -> -w }
        val dy = when (textBaseline) {
            Baseline.TOP -> 0f
            Baseline.MIDDLE -> -h / 2
            Baseline.ALPHABETIC -> -l.firstBaseline
            Baseline.BOTTOM -> -h
        }
        ds.drawText(
            l,
            brush = if (style != null) stroke else fill,
            topLeft = Offset(x + dx, y + dy),
            alpha = alpha,
            shadow = textShadow,
            drawStyle = style ?: Fill,
            blendMode = blend,
        )
    }

    companion object {
        /** "#rrggbb" → Color. */
        fun hex(value: String, alpha: Float = 1f): Color {
            val n = value.removePrefix("#").toLong(16)
            return Color(((n shr 16) and 255).toInt(), ((n shr 8) and 255).toInt(), (n and 255).toInt(), (alpha * 255).toInt().coerceIn(0, 255))
        }

        fun rgba(r: Int, g: Int, b: Int, a: Float = 1f): Color = Color(r, g, b, (a.coerceIn(0f, 1f) * 255).toInt())

        /** 0xRRGGBB + alpha. */
        fun rgb(color: Int, a: Float = 1f): Color = rgba((color shr 16) and 255, (color shr 8) and 255, color and 255, a)
    }
}

/**
 * Dessine [block] dans le repère du site : une unité = un dp, soit à peu
 * près un pixel CSS. Le site dessine en pixels CSS et Compose en pixels
 * physiques, 2,5 à 3 fois plus nombreux sur un téléphone : sans ce repère,
 * tout ce qui est porté tel quel du JS (tailles, vitesses, décalages) sort
 * 2,5 à 3 fois trop petit. `size` (et donc `center`) est ramené en dp
 * pendant [block], pour que les fonctions qui s'en servent restent justes.
 */
internal fun DrawScope.inCssPixels(block: DrawScope.() -> Unit) {
    val k = density
    val physical = drawContext.size
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        drawContext.size = Size(physical.width / k, physical.height / k)
        try {
            block()
        } finally {
            drawContext.size = physical
        }
    }
}
