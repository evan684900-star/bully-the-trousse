package com.bullythetrousse.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import com.bullythetrousse.app.Ctx2D.Companion.hex
import com.bullythetrousse.app.Ctx2D.Companion.rgba
import com.bullythetrousse.core.TrousseVariant
import com.bullythetrousse.core.VILLE_TS
import com.bullythetrousse.core.VilleEngine
import com.bullythetrousse.core.VilleEngine.Companion.easeInOut
import com.bullythetrousse.core.VilleLevels
import com.bullythetrousse.core.VilleMode
import com.bullythetrousse.core.VilleNpc
import com.bullythetrousse.core.VilleTransKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Le rendu du monde Ville : portage des fonctions de dessin de ville.js
 * (joueur, autres trousses, bulles, invites, obscurité, transitions,
 * arrivée, escaliers) — le décor de chaque niveau est dans
 * VilleLevelArt.kt. Tout se dessine dans le repère VIRTUEL du moteur
 * (≈ 760 × 540), l'écran se chargeant de la mise à l'échelle.
 */

/** Ce qu'il faut pour peindre la trousse du joueur. */
internal class VilleSprites(
    val image: ImageBitmap,
    val skinId: String,
    val skinFilter: ColorFilter?,
    val cosmetic: String,
    val filterQuality: FilterQuality,
)

private const val TS = VILLE_TS.toFloat()

/** Inversion des couleurs : la trousse "inversée" de la 1re rencontre. */
private val INVERT_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * Une image du monde Ville, en cours de dessin. Regroupe ce que toutes les
 * fonctions de dessin partagent (moteur, canvas, repère, ciel de l'instant).
 */
internal class VilleScene(
    val e: VilleEngine,
    val c: Ctx2D,
    val sprites: VilleSprites,
) {
    val gy = e.groundY.toFloat()
    val vw = e.viewWidth.toFloat()
    val vh = e.viewHeight.toFloat()
    val cam = e.camX.toFloat()
    val t = e.time.toFloat()
    val sky = VilleSky.now()
    /** Calculé une fois par image : l'évènement du calendrier ne bouge pas en 16 ms. */
    val outage = e.isOutage()

    fun inView(x: Float, w: Float) = x + w > cam - 50 && x - w < cam + vw + 50

    /* ============ SCÈNE ============ */
    /** Tout sauf les transitions et l'effet "glitch" (appliqués par l'écran). */
    fun renderScene() {
        when (e.mode) {
            VilleMode.PLAY -> renderPlay()
            VilleMode.ARRIVAL -> drawArrival()
            VilleMode.STAIRS -> drawStairs()
            VilleMode.CREDITS -> { c.fill(Color.Black); c.fillRect(0f, 0f, vw, vh) }
        }
    }

    private fun renderPlay() {
        when (e.level.id) {
            "city" -> drawCityBack()
            "reception" -> drawReception()
            "darkroom" -> drawDarkroom()
            "glitch" -> drawGlitchWorld()
        }
        if (e.level.id == "city") drawCars(front = false)
        if (e.level.id == "glitch") drawFightMid()
        e.npcs.forEach { drawNpc(it) }
        drawPlayer()
        drawParticles()
        when (e.level.id) {
            "city" -> { drawCityFront(); drawCars(front = true) }
            "reception" -> drawReceptionFront()
            "glitch" -> drawGlitchFront()
        }
        when (e.level.id) {
            "reception" -> drawReceptionOverlay()
            "darkroom" -> drawDarkroomOverlay()
            "glitch" -> {
                c.fill(rgba(0, 0, 0, 0.06f))
                var y = 0f
                while (y < vh) { c.fillRect(0f, y, vw, 1f); y += 4f }
            }
        }
        val near = e.near
        if (near != null && !e.player.frozen) {
            promptPill((near.x - e.camX).toFloat(), gy - near.promptAlt.toFloat(), near.label())
        }
        e.npcs.forEach { n ->
            if (n.say.isNotEmpty()) {
                bubble((n.x - e.camX).toFloat(), gy - n.alt.toFloat() - n.size.toFloat() * 0.45f, n.say, n.big, n.dark)
            }
        }
        if (e.player.say.isNotEmpty()) {
            val (px, py) = e.playerScreen()
            bubble(px.toFloat(), py.toFloat() - TS * 0.35f, e.player.say)
        }
        if (e.level.id == "glitch") drawFightHud()
    }

    /* ============ TROUSSES ============ */
    /** `drawTrousseV()` : le skin du joueur, inversé, ou le sprite de base. */
    fun drawTrousseV(x: Float, y: Float, size: Float, rot: Float, dir: Int, variant: TrousseVariant) {
        c.saved {
            c.translate(x, y)
            if (dir < 0) c.scale(-1f, 1f)
            val s = sprites
            when (variant) {
                TrousseVariant.PLAYER -> c.ds.drawTrousseSprite(
                    s.image, s.skinId, 0f, 0f, size, rot, s.skinFilter, c.alpha, s.filterQuality, s.cosmetic,
                )
                TrousseVariant.BASE -> c.ds.drawTrousseSprite(
                    s.image, "classique", 0f, 0f, size, rot, null, c.alpha, s.filterQuality,
                )
                TrousseVariant.INVERTED -> {
                    val canvas = c.ds.drawContext.canvas
                    canvas.saveLayer(
                        Rect(-size, -size, size, size),
                        Paint().apply { colorFilter = INVERT_FILTER; alpha = c.alpha },
                    )
                    c.ds.drawTrousseSprite(s.image, s.skinId, 0f, 0f, size, rot, s.skinFilter, 1f, s.filterQuality, s.cosmetic)
                    canvas.restore()
                }
            }
        }
    }

    /** Épée tenue sur le côté de la trousse. [angle] : 0 = lame vers le haut. */
    fun drawSword(x: Float, y: Float, dir: Int, angle: Float, s: Float = 1f) {
        c.saved {
            c.translate(x, y)
            c.scale(dir * s, s)
            c.rotate(angle)
            c.fill(hex("#6b3f1f")); c.fillRect(-3f, -2f, 6f, 14f)
            c.fill(hex("#ffd23f")); c.fillRect(-11f, -5f, 22f, 5f); c.fillRect(-3f, 11f, 6f, 4f)
            c.fill = c.linearGradient(-4f, 0f, 4f, 0f, 0f to hex("#9aa3b3"), 0.5f to hex("#f4f7fb"), 1f to hex("#8b94a4"))
            c.beginPath(); c.moveTo(-4f, -5f); c.lineTo(-4f, -60f); c.lineTo(0f, -70f); c.lineTo(4f, -60f); c.lineTo(4f, -5f); c.closePath(); c.fill()
            c.fill(rgba(120, 255, 170, 0.55f)); c.fillRect(-1f, -58f, 2f, 50f)
        }
    }

    private fun drawPlayer() {
        val p = e.player
        if (!p.visible || p.alpha <= 0.01) return
        if (p.hurt > 0 && floor(p.hurt * 14).toInt() % 2 != 0) return
        val size = TS * p.scale.toFloat()
        val moving = p.ground && abs(p.vx) > 40
        val bob = if (moving) abs(sin(p.walk)).toFloat() * 4 else 0f
        val tilt = if (p.ground) {
            (sin(p.walk) * 0.07 * min(1.0, abs(p.vx) / VilleEngine.SPEED)).toFloat()
        } else {
            (-p.va / 4000).toFloat().coerceIn(-0.18f, 0.18f)
        }
        val sx = (p.x - e.camX).toFloat()
        val groundAt = gy - p.alt.toFloat()
        val cy = groundAt - size * 0.19f - bob
        c.saved {
            c.alpha = p.alpha.toFloat() * 0.3f
            c.fill(Color.Black)
            c.fillEllipse(sx, groundAt + 1, size * 0.34f, 4f)
            c.alpha = p.alpha.toFloat()
            drawTrousseV(sx, cy, size, p.rot.toFloat() + tilt, p.dir, if (p.invert) TrousseVariant.INVERTED else TrousseVariant.PLAYER)
            if (p.sword) {
                var angle = 0.35f
                if (p.swing > 0) {
                    val q = 1 - p.swing.toFloat() / 0.22f
                    angle = -0.9f + q * 2.6f
                }
                drawSword(sx + p.dir * size * 0.4f, cy + 4, p.dir, angle)
                if (p.swing > 0) {
                    c.stroke(rgba(200, 255, 220, 0.5f)); c.lineWidth = 6f
                    val a0 = if (p.dir > 0) -PI.toFloat() / 2 - 0.9f else -PI.toFloat() / 2 + 0.9f
                    c.beginPath()
                    c.arc(sx + p.dir * size * 0.4f, cy + 4, 62f, min(a0, a0 + p.dir * 2.4f), max(a0, a0 + p.dir * 2.4f))
                    c.stroke()
                }
            }
        }
        if (p.bang > 0.01) {
            c.saved {
                c.alpha = p.bang.toFloat()
                c.textAlign = Ctx2D.Align.CENTER
                c.font = Ctx2D.Font(54f, FontWeight.Black)
                c.fill(hex("#ffcf3f")); c.stroke(rgba(40, 25, 0, 0.85f)); c.lineWidth = 5f
                c.strokeText("!", sx, cy - 42)
                c.fillText("!", sx, cy - 42)
            }
        }
    }

    private fun drawNpc(n: VilleNpc) {
        if (n.alpha <= 0.01) return
        val size = n.size.toFloat()
        val x = (n.x - e.camX).toFloat()
        val y = gy - n.alt.toFloat() - size * 0.19f
        c.saved {
            c.alpha = n.alpha.toFloat()
            // Halo (shadowBlur côté site) : un dégradé radial derrière la trousse.
            n.glow?.let { argb ->
                val glow = Color(argb.toInt())
                c.fill = c.radialGradient(x, y, size * 0.2f, size * 0.62f + 26, 0f to glow, 1f to glow.copy(alpha = 0f))
                c.fillCircle(x, y, size * 0.62f + 26)
            }
            drawTrousseV(x, y, size, n.rot.toFloat(), n.dir, n.variant)
            if (n.flash > 0) {
                val canvas = c.ds.drawContext.canvas
                canvas.saveLayer(
                    Rect(x - size, y - size, x + size, y + size),
                    Paint().apply { blendMode = BlendMode.Plus; alpha = (n.flash * n.alpha).toFloat().coerceIn(0f, 1f) },
                )
                val a = c.alpha
                c.alpha = 1f
                drawTrousseV(x, y, size, n.rot.toFloat(), n.dir, n.variant)
                c.alpha = a
                canvas.restore()
            }
        }
    }

    private fun drawParticles() {
        for (p in e.particles) {
            c.fill(Ctx2D.rgb(p.color, (p.life / p.maxLife).toFloat().coerceIn(0f, 1f)))
            val x = (p.x - e.camX).toFloat()
            val y = gy - p.alt.toFloat()
            val size = p.size.toFloat()
            if (p.square) c.fillRect(x - size / 2, y - size / 2, size, size) else c.fillCircle(x, y, max(0.5f, size))
        }
    }

    /* ============ BULLES, INVITES, OBSCURITÉ ============ */
    private fun wrapLines(text: String, maxW: Float): List<String> {
        val lines = ArrayList<String>()
        var line = ""
        for (w in text.split(" ")) {
            val candidate = if (line.isNotEmpty()) "$line $w" else w
            if (c.measureText(candidate) > maxW && line.isNotEmpty()) {
                lines += line
                line = w
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    fun bubble(x: Float, y: Float, text: String, big: Boolean = false, dark: Boolean = false) {
        val size = if (big) 30f else 16f
        c.saved {
            c.font = Ctx2D.Font(size, FontWeight.ExtraBold)
            val lines = wrapLines(text, if (big) 520f else 250f)
            val lh = size * 1.3f
            val w = (lines.maxOfOrNull { c.measureText(it) } ?: 0f) + 24
            val h = lines.size * lh + 16
            val bx = (x - w / 2).coerceIn(8f, max(8f, vw - w - 8))
            val by = y - h - 14
            c.fill(if (dark) rgba(10, 10, 16, 0.92f) else rgba(255, 255, 255, 0.96f))
            c.stroke(if (dark) hex("#b3202a") else rgba(0, 0, 0, 0.75f))
            c.lineWidth = 2.5f
            c.beginPath(); c.roundRect(bx, by, w, h, 12f); c.fill(); c.stroke()
            c.beginPath(); c.moveTo(x - 8, by + h - 1); c.lineTo(x, by + h + 12); c.lineTo(x + 8, by + h - 1); c.closePath(); c.fill()
            c.fill(if (dark) hex("#ff5a5a") else hex("#161a24"))
            c.textAlign = Ctx2D.Align.CENTER
            c.textBaseline = Ctx2D.Baseline.TOP
            lines.forEachIndexed { i, l -> c.fillText(l, bx + w / 2, by + 8 + i * lh) }
        }
    }

    /** L'invite "E  Prendre un vol" au-dessus d'un objet. */
    fun promptPill(x: Float, y: Float, label: String) {
        c.saved {
            c.font = Ctx2D.Font(14f, FontWeight.ExtraBold)
            val w = c.measureText(label) + 50
            val bx = (x - w / 2).coerceIn(6f, max(6f, vw - w - 6))
            val by = y - 30
            c.alpha = 0.85f + 0.15f * sin(t * 5)
            c.fill(rgba(20, 24, 36, 0.9f)); c.stroke(rgba(255, 210, 63, 0.9f)); c.lineWidth = 2f
            c.beginPath(); c.roundRect(bx, by, w, 30f, 15f); c.fill(); c.stroke()
            c.fill(hex("#ffd23f")); c.fillRoundRect(bx + 6, by + 5, 22f, 20f, 5f)
            c.fill(hex("#161a24")); c.textAlign = Ctx2D.Align.CENTER; c.textBaseline = Ctx2D.Baseline.MIDDLE
            c.fillText("E", bx + 17, by + 15.5f)
            c.fill(Color.White); c.textAlign = Ctx2D.Align.LEFT
            c.fillText(label, bx + 36, by + 15.5f)
        }
    }

    class Hole(val x: Float, val y: Float, val r: Float, val a: Float = 1f)

    /** Obscurité avec des "trous" de lumière (pièce noire, coupure de courant). */
    fun darkness(alpha: Float, holes: List<Hole>) {
        val canvas = c.ds.drawContext.canvas
        canvas.saveLayer(Rect(-10f, -10f, vw + 10, vh + 10), Paint())
        c.saved {
            c.fill(rgba(2, 3, 8, alpha)); c.fillRect(-10f, -10f, vw + 20, vh + 20)
            c.blend = BlendMode.DstOut
            for (hl in holes) {
                c.fill = c.radialGradient(
                    hl.x, hl.y, 0f, hl.r,
                    0f to rgba(0, 0, 0, hl.a), 0.6f to rgba(0, 0, 0, hl.a * 0.7f), 1f to rgba(0, 0, 0, 0f),
                )
                c.fillCircle(hl.x, hl.y, hl.r)
            }
        }
        canvas.restore()
    }

    /* ============ TRANSITIONS ============ */
    /** Quantité d'effet "glitch" que la transition en cours ajoute (0 = aucune). */
    fun transitionGlitch(): Float {
        val tr = e.transition ?: return 0f
        if (tr.kind != VilleTransKind.GLITCH && tr.kind != VilleTransKind.GLITCH_QUICK) return 0f
        val p = tr.progress.toFloat()
        val m = tr.kind.mid.toFloat()
        return if (p < m) min(1.4f, p / m * 1.6f) else 0f
    }

    /** Flou de la transition "blur" (px du repère virtuel). */
    fun transitionBlur(): Float {
        val tr = e.transition ?: return 0f
        if (tr.kind != VilleTransKind.BLUR) return 0f
        val p = tr.progress.toFloat()
        return if (p < 0.62f) c01f(p / 0.45f) * 14 else 0f
    }

    fun drawTransition() {
        val tr = e.transition ?: return
        val p = tr.progress.toFloat()
        when (tr.kind) {
            VilleTransKind.IRIS, VilleTransKind.IRIS_HOLD -> {
                val k = if (tr.kind == VilleTransKind.IRIS_HOLD) {
                    1 - easeInOut(p.toDouble()).toFloat()
                } else if (p < 0.5f) {
                    1 - easeInOut(p * 2.0).toFloat()
                } else {
                    easeInOut((p - 0.5) * 2).toFloat()
                }
                val cx = tr.centerX?.toFloat() ?: (vw / 2)
                val cy = tr.centerY?.toFloat() ?: (vh / 2)
                val maxR = hypot(max(cx, vw - cx), max(cy, vh - cy)) + 10
                val veil = Path().apply { addRect(Rect(-20f, -20f, vw + 20, vh + 20)) }
                val hole = Path().apply { addOval(Rect(Offset(cx, cy), max(0.001f, maxR * k))) }
                c.ds.drawPath(Path().apply { op(veil, hole, PathOperation.Difference) }, Color.Black)
            }
            VilleTransKind.FADE -> {
                c.fill(rgba(0, 0, 0, if (p < 0.5f) p * 2 else (1 - p) * 2)); c.fillRect(-20f, -20f, vw + 40, vh + 40)
            }
            VilleTransKind.GLITCH, VilleTransKind.GLITCH_QUICK -> {
                val m = tr.kind.mid.toFloat()
                val a = if (p < m) c01f((p / m - 0.6f) / 0.4f) else 1 - c01f((p - m) / (1 - m))
                c.fill(rgba(0, 0, 0, a)); c.fillRect(-20f, -20f, vw + 40, vh + 40)
            }
            VilleTransKind.BLUR -> {
                val a = if (p < 0.62f) c01f((p - 0.3f) / 0.28f) else 1 - c01f((p - 0.7f) / 0.3f)
                c.fill(rgba(0, 0, 0, a)); c.fillRect(-20f, -20f, vw + 40, vh + 40)
            }
        }
    }

    /* ============ CINÉMATIQUE D'ARRIVÉE ============ */
    private fun drawArrival() {
        val w = vw
        val h = vh
        val at = e.arrival.t.toFloat()
        val clouds = VilleEngine.ARRIVAL_CLOUDS.toFloat()
        val descent = VilleEngine.ARRIVAL_DESCENT.toFloat()
        val dp = easeInOut(c01f((at - clouds) / descent).toDouble()).toFloat()
        val horizon = lerpf(h * 1.25f, h * 0.6f, dp)
        val scroll = at * 700
        c.drawVilleSky(w, h, horizon, sky, camX = scroll * 0.2f)
        val lit = if (outage) 0f else sky.night
        if (horizon < h + 40) {
            c.drawSkyline(w, horizon, scroll, 0.05f, 71, SkylineStyle(hazeColor(sky, 0.75f), lit * 0.8f, 40f, 150f, bw = 60f))
            // l'aéroport (tour de contrôle + piste) se rapproche à la fin
            val ax = w * 1.3f - dp * w * 0.75f
            c.fill(hazeColor(sky, 0.55f))
            c.fillRect(ax, horizon - 120, 16f, 120f)
            c.fillRect(ax - 16, horizon - 142, 48f, 26f)
            c.fill(rgba(160, 220, 255, 0.7f)); c.fillRect(ax - 12, horizon - 138, 40f, 12f)
            val near = horizon + (h - horizon) * 0.25f
            c.drawSkyline(
                w, near, scroll, 0.22f, 83,
                SkylineStyle(hazeColor(sky, 0.5f), lit, 80f, 260f, bw = 110f, dayWindows = if (sky.night < 0.5f) rgba(255, 255, 255, 0.12f) else null),
            )
            c.fill(hazeColor(sky, 0.32f))
            c.fillRect(0f, near, w, h)
            // piste avec balisage lumineux
            c.fill(hex("#3a3d44"))
            c.fillRect(ax - 400, near - 6, 900f, 10f)
            for (i in 0 until 18) {
                c.fill(if (floor(at * 4 + i).toInt() % 6 == 0) Color.White else hex("#ffcf6a"))
                c.fillRect(ax - 400 + i * 50, near - 3, 5f, 3f)
            }
        }
        // autres avions qui vont dans l'autre sens
        if (at > clouds) {
            for (i in 0 until 3) {
                val span = w + 700
                val px = ((w + 300 - (at * (330 + i * 90) + scroll * 0.25f) - i * 420) % span + span) % span - 300
                val py = horizon - 170 - i * 70
                if (py > h) continue
                c.drawAirliner(px, py, 0.16f + i * 0.04f, dir = -1, silhouette = hazeColor(sky, 0.85f - i * 0.1f), lights = sky.night > 0.4f)
            }
        }
        // notre avion, avec la trousse encastrée sur le flanc
        val bob = sin(at * 1.3f) * 6
        val px = w * 0.42f
        val py = lerpf(h * 0.34f, h * 0.44f, dp) + bob
        val rot = if (at > clouds) 0.05f * sin(PI.toFloat() * dp) else 0f
        val s = 0.72f
        c.drawAirliner(px, py, s, dir = 1, rot = rot, lights = true)
        c.saved {
            c.translate(px, py); c.rotate(rot)
            drawTrousseV(70 * s, -2f, 60 * s / 0.9f, -0.1f + sin(at * 9) * 0.02f, 1, TrousseVariant.PLAYER)
        }
        // buildings au premier plan, DEVANT l'avion et la trousse
        if (horizon < h * 1.1f) {
            val fs = scroll * 1.6f
            for (i in floor(fs / 520).toInt() - 1..floor((fs + w) / 520).toInt() + 1) {
                val bx = i * 520 + sr(i * 3.1) * 200 - fs
                val bw = 150 + sr(i * 7.7) * 120
                val bh = (h - horizon) * 0.9f + sr(i * 5.5) * h * 0.35f
                val top = h - bh * c01f(dp * 1.4f)
                c.fill(hazeColor(sky, 0.22f))
                c.fillRect(bx, top, bw, h - top + 10)
                var r = 0
                while (r * 22 < h - top) {
                    var k = 0
                    while (k * 20 < bw - 14) {
                        val q = sr(i * 13 + r * 3.7 + k * 1.9)
                        if (lit > 0.1f && q < lit * 0.45f) {
                            c.fill(rgba(255, 210, 130, 0.9f)); c.fillRect(bx + 10 + k * 20, top + 12 + r * 22, 9f, 12f)
                        } else if (lit <= 0.5f && q < 0.55f) {
                            c.fill(rgba(170, 210, 240, 0.25f)); c.fillRect(bx + 10 + k * 20, top + 12 + r * 22, 9f, 12f)
                        }
                        k++
                    }
                    r++
                }
            }
        }
        // traversée des nuages : ils défilent de droite à gauche au premier plan
        if (at < clouds + 0.4f) {
            val fade = c01f((clouds + 0.4f - at) / 0.7f)
            c.saved {
                c.alpha = fade
                c.fill(if (sky.night > 0.5f) hex("#cfd5e6") else hex("#eef3f8"))
                for (k in 0 until 16) {
                    val cx = w + 200 + k * 260 - at * 1250 + sr(k * 4.1) * 100
                    val cy = h * (0.15f + sr(k * 2.7) * 0.75f)
                    c.cloudPuff(cx, cy, 110 + sr(k * 8.3) * 90)
                }
            }
            if (at < 0.55f) { c.fill(rgba(238, 243, 248, 1 - at / 0.55f)); c.fillRect(0f, 0f, w, h) }
        }
    }

    /* ============ ESCALIERS ============ */
    private fun drawStairs() {
        val w = vw
        val h = vh
        val st = e.stairs
        c.fill(hex("#3c3f46")); c.fillRect(0f, 0f, w, h)
        val flightH = 170f
        val scroll = st.climb.toFloat() * 95
        val cx = w / 2
        for (f in -2 until 8) {
            val baseY = h * 0.78f - f * flightH + (scroll % (flightH * 2)) - flightH
            val right = (f + floor(scroll / (flightH * 2)).toInt() * 2) % 2 == 0
            c.fill(hex("#5a5e67"))
            for (s in 0 until 10) {
                val sx = if (right) cx - 180 + s * 36 else cx + 180 - s * 36
                val sy = baseY - s * (flightH / 10)
                c.fillRect(if (right) sx else sx - 36, sy - flightH / 10, 36f, flightH / 10 + 1)
            }
            c.stroke(hex("#9aa0aa")); c.lineWidth = 4f
            c.line(cx - 180, baseY - (if (right) 40f else flightH + 40), cx + 180, baseY - (if (right) flightH + 40 else 40f))
            c.fill(rgba(255, 255, 255, 0.14f))
            c.font = Ctx2D.Font(64f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
            c.fillText(max(1, f + 2 + floor(scroll / flightH).toInt()).toString(), if (right) cx - 280 else cx + 280, baseY - 60)
        }
        c.textAlign = Ctx2D.Align.LEFT
        val bob = if (st.pause > 0) 0f else abs(sin(st.t * 9)).toFloat() * 5
        val dir = if (floor(scroll / flightH).toInt() % 2 == 0) 1 else -1
        drawTrousseV(
            cx + sin(st.t * 0.8).toFloat() * 30, h * 0.62f - bob, TS * 1.2f, dir * -0.3f, dir,
            if (e.player.invert) TrousseVariant.INVERTED else TrousseVariant.PLAYER,
        )
        if (st.pause > 0) bubble(cx, h * 0.52f, "pfff...")
        // QTE
        if (!st.done && st.ringT >= 0) {
            val qx = w / 2
            val qy = h * 0.27f
            c.saved {
                c.stroke(rgba(107, 255, 176, 0.9f)); c.lineWidth = 4f
                c.strokeCircle(qx, qy, st.target.toFloat())
                c.stroke(rgba(255, 255, 255, 0.95f)); c.lineWidth = 5f
                c.strokeCircle(qx, qy, max(2f, st.radius.toFloat()))
            }
        }
        c.saved {
            c.textAlign = Ctx2D.Align.CENTER; c.fill(Color.White)
            c.font = Ctx2D.Font(24f, FontWeight.Black)
            c.textShadow = Shadow(rgba(0, 0, 0, 0.8f), Offset.Zero, 8f)
            c.fillText("GÈRE TON SOUFFLE", w / 2, h * 0.08f + 20)
        }
        for (i in 0 until VilleEngine.STAIRS_COUNT) {
            c.fill(
                when {
                    i < st.rings.size -> if (st.rings[i]) hex("#6bffb0") else hex("#ff6b6b")
                    else -> rgba(255, 255, 255, 0.3f)
                },
            )
            c.fillCircle(w / 2 - (VilleEngine.STAIRS_COUNT - 1) * 11 + i * 22, h * 0.08f + 44, 7f)
        }
    }

    /* ============ COMBAT ============ */
    private fun drawFightHud() {
        val st = e.story ?: return
        val f = st.fight ?: return
        val w = vw
        val bw = min(w * 0.6f, 520f)
        val bx = (w - bw) / 2
        val by = 76f
        c.saved {
            c.fill(rgba(0, 0, 0, 0.6f)); c.fillRoundRect(bx - 4, by - 4, bw + 8, 24f, 8f)
            c.fill(hex("#3a0d12")); c.fillRoundRect(bx, by, bw, 16f, 6f)
            c.fill = c.linearGradient(bx, 0f, bx + bw, 0f, 0f to hex("#ff2d4a"), 1f to hex("#ff8a3c"))
            c.fillRoundRect(bx, by, max(0.01f, bw * f.hp / VilleLevels.BOSS_HP), 16f, 6f)
            c.fill(Color.White); c.font = Ctx2D.Font(13f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
            c.fillText("???  — l'autre toi", w / 2, by - 8)
            // 5 cœurs en bas à droite, au-dessus des commandes tactiles
            c.font = Ctx2D.Font(26f); c.textAlign = Ctx2D.Align.RIGHT; c.textBaseline = Ctx2D.Baseline.MIDDLE
            val hy = vh - 120
            for (i in 0 until 5) c.fillText(if (i < e.player.hearts) "❤️" else "🖤", w - 16 - i * 32, hy)
        }
    }
}
