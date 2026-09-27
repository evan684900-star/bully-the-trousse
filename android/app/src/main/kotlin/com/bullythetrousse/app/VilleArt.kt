package com.bullythetrousse.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.bullythetrousse.app.Ctx2D.Companion.hex
import com.bullythetrousse.app.Ctx2D.Companion.rgba
import com.bullythetrousse.core.CourDecor
import com.bullythetrousse.core.Puddle
import com.bullythetrousse.core.VilleEventType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Dessins partagés du monde Ville, portage de ville-art.js : ciel à l'heure
 * réelle, silhouettes de buildings, avion de ligne, toit-terrain de lancer,
 * météo, voitures, crabes en uniforme, portail. Aucun état de jeu ici :
 * juste des fonctions de dessin, utilisées par l'écran Ville, la
 * cinématique d'arrivée et le toit (écran de jeu).
 */

private const val TAU = (2 * PI).toFloat()

internal fun sr(n: Double): Float = CourDecor.seededRand(n).toFloat()
internal fun sr(n: Float): Float = CourDecor.seededRand(n.toDouble()).toFloat()
internal fun c01f(v: Float): Float = v.coerceIn(0f, 1f)
internal fun lerpf(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Le ciel à un instant donné : dégradé, part de nuit (0 = plein jour), heure locale. */
internal class VilleSkyInfo(
    val topArr: IntArray,
    val bottomArr: IntArray,
    val night: Float,
    val hour: Float,
) {
    val top: Color = rgba(topArr[0], topArr[1], topArr[2])
    val bottom: Color = rgba(bottomArr[0], bottomArr[1], bottomArr[2])
}

/** Horloge commune des animations de la Ville (`performance.now() / 1000`). */
internal object VilleClock {
    private val start = System.nanoTime()
    val seconds: Float get() = ((System.nanoTime() - start) / 1e9).toFloat()
    val millis: Double get() = (System.nanoTime() - start) / 1e6
}

/** Le ciel suit l'heure réelle de l'appareil (`skyAt()`), recalculé au plus une fois par seconde. */
internal object VilleSky {
    // [heure, couleur haut, couleur bas, nuit]
    private val KEYS: List<Triple<Float, Pair<IntArray, IntArray>, Float>> = listOf(
        Triple(0f, intArrayOf(10, 14, 34) to intArrayOf(26, 32, 62), 1f),
        Triple(5f, intArrayOf(14, 20, 48) to intArrayOf(40, 44, 82), 1f),
        Triple(6.4f, intArrayOf(64, 78, 140) to intArrayOf(245, 165, 115), 0.5f),
        Triple(8f, intArrayOf(88, 164, 228) to intArrayOf(190, 226, 255), 0f),
        Triple(17.6f, intArrayOf(86, 160, 222) to intArrayOf(205, 231, 255), 0f),
        Triple(19.3f, intArrayOf(78, 82, 156) to intArrayOf(255, 150, 90), 0.35f),
        Triple(20.7f, intArrayOf(30, 34, 82) to intArrayOf(110, 62, 112), 0.8f),
        Triple(22f, intArrayOf(10, 14, 34) to intArrayOf(26, 32, 62), 1f),
        Triple(24f, intArrayOf(10, 14, 34) to intArrayOf(26, 32, 62), 1f),
    )

    private var cache: VilleSkyInfo? = null
    private var cacheAt = 0L

    /** Heure forcée (0-24), pour les aperçus ; null = l'heure de l'appareil. */
    internal var forcedHour: Float? = null
        set(value) { field = value; cache = null }

    fun now(): VilleSkyInfo {
        val nowMillis = System.currentTimeMillis()
        cache?.let { if (nowMillis - cacheAt < 1000) return it }
        val local = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        val hr = forcedHour ?: (local.hour + local.minute / 60f + local.second / 3600f)
        var i = 0
        while (i < KEYS.size - 2 && KEYS[i + 1].first <= hr) i++
        val a = KEYS[i]
        val b = KEYS[i + 1]
        val t = c01f((hr - a.first) / (b.first - a.first))
        fun mix(x: IntArray, y: IntArray) = IntArray(3) { k -> lerpf(x[k].toFloat(), y[k].toFloat(), t).roundToInt() }
        val info = VilleSkyInfo(
            topArr = mix(a.second.first, b.second.first),
            bottomArr = mix(a.second.second, b.second.second),
            night = lerpf(a.third, b.third, t),
            hour = hr,
        )
        cache = info
        cacheAt = nowMillis
        return info
    }
}

/** Couleur d'un bâtiment lointain : teinte du ciel assombrie (brume). */
internal fun hazeColor(sky: VilleSkyInfo, k: Float, alpha: Float = 1f): Color {
    val m = IntArray(3) { i -> (lerpf(sky.bottomArr[i].toFloat(), sky.topArr[i].toFloat(), 0.3f) * k).roundToInt().coerceIn(0, 255) }
    return rgba(m[0], m[1], m[2], alpha)
}

internal fun Ctx2D.drawVilleSky(w: Float, h: Float, horizonY: Float, sky: VilleSkyInfo, camX: Float = 0f, noClouds: Boolean = false) {
    fill = linearGradient(0f, 0f, 0f, max(10f, horizonY), 0f to sky.top, 1f to sky.bottom)
    fillRect(0f, 0f, w, h)
    val t = VilleClock.seconds
    // étoiles
    if (sky.night > 0.15f) {
        fill(Color.White)
        for (i in 0 until 70) {
            val x = sr(i * 3.1) * w
            val y = sr(i * 7.7) * horizonY * 0.9f
            alpha = sky.night * (0.35f + 0.65f * abs(sin(t * (0.6f + sr(i.toDouble()) * 1.8f) + i)))
            fillRect(x, y, 1.6f, 1.6f)
        }
        alpha = 1f
    }
    // soleil (6 h -> 20 h) ou lune
    val hr = sky.hour
    if (hr in 5.8f..20.4f) {
        val p = (hr - 5.8f) / 14.6f
        val x = w * (0.08f + 0.84f * p) - camX * 0.01f
        val y = horizonY - sin(p * PI.toFloat()) * horizonY * 0.78f + 20
        fill = radialGradient(x, y, 4f, 90f, 0f to rgba(255, 240, 190, 0.55f), 1f to rgba(255, 200, 120, 0f))
        fillRect(x - 90, y - 90, 180f, 180f)
        fill(if (p < 0.12f || p > 0.88f) hex("#ffb070") else hex("#fff2c4"))
        fillCircle(x, y, 22f)
    }
    if (sky.night > 0.3f) {
        val p = ((hr + 24 - 19.5f) % 24) / 11
        val x = w * (0.1f + 0.8f * c01f(p))
        val y = horizonY - sin(c01f(p) * PI.toFloat()) * horizonY * 0.7f + 30
        alpha = c01f((sky.night - 0.3f) / 0.4f)
        fill(hex("#f3f1e6"))
        fillCircle(x, y, 17f)
        fill(sky.top)
        fillCircle(x + 7, y - 4, 15f)
        alpha = 1f
    }
    // quelques nuages qui dérivent
    if (!noClouds) {
        fill(if (sky.night > 0.5f) rgba(120, 130, 170, 0.18f) else rgba(255, 255, 255, 0.55f))
        for (i in 0 until 5) {
            val span = w + 400
            val x = ((sr(i * 4.4) * span + t * (8 + i * 3) - camX * 0.03f) % span + span) % span - 200
            val y = horizonY * (0.12f + sr(i * 9.1) * 0.4f)
            val s = 0.6f + sr(i * 2.2) * 0.8f
            cloudPuff(x, y, 60 * s)
        }
    }
}

/** Un nuage : quatre disques remplis d'un seul tenant (pas de double opacité aux recouvrements). */
internal fun Ctx2D.cloudPuff(x: Float, y: Float, r: Float) {
    beginPath()
    arc(x, y, r, 0f, TAU)
    arc(x + r * 0.9f, y + r * 0.15f, r * 0.75f, 0f, TAU)
    arc(x - r * 0.85f, y + r * 0.2f, r * 0.7f, 0f, TAU)
    arc(x + r * 0.2f, y - r * 0.45f, r * 0.65f, 0f, TAU)
    fill()
}

/** Options de [drawSkyline]. */
internal class SkylineStyle(
    val color: Color,
    val lit: Float,
    val minH: Float,
    val maxH: Float,
    val bw: Float = 90f,
    val dayWindows: Color? = null,
    val extra: Float = 4f,
)

/** Rangée de buildings en silhouette (parallaxe), fenêtres allumées la nuit. */
internal fun Ctx2D.drawSkyline(w: Float, baseY: Float, camX: Float, par: Float, seed: Int, o: SkylineStyle) {
    val bw = o.bw
    val off = camX * par
    val i0 = floor(off / bw).toInt() - 1
    val i1 = floor((off + w) / bw).toInt() + 1
    val blink = floor(VilleClock.millis / 700).toLong()
    for (i in i0..i1) {
        val x = i * bw - off
        val r = sr(i * 1.7 + seed)
        val hgt = o.minH + r * (o.maxH - o.minH)
        val ww = bw * (0.72f + 0.28f * sr(i * 2.9 + seed))
        val top = baseY - hgt
        fill(o.color)
        fillRect(x, top, ww, hgt + o.extra)
        val kind = sr(i * 5.3 + seed)
        if (kind > 0.72f) {
            fillRect(x + ww * 0.45f, top - 26, 2f, 26f)
        } else if (kind > 0.55f) {
            fillRect(x + ww * 0.2f, top - 10, ww * 0.25f, 10f)
        } else if (kind < 0.12f) {
            beginPath(); moveTo(x, top); lineTo(x + ww / 2, top - 22); lineTo(x + ww, top); fill()
        }
        if (kind > 0.72f && o.lit > 0.2f && (blink + i) % 2 != 0L) {
            fill(hex("#ff4040"))
            fillRect(x + ww * 0.45f - 1, top - 28, 4f, 4f)
        }
        if (o.lit > 0.05f || o.dayWindows != null) {
            val cols = max(2, ((ww - 8) / 11).toInt())
            val rows = ((hgt - 12) / 15).toInt()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val q = sr(i * 31.7 + row * 7.3 + col * 3.1 + seed)
                    if (o.lit > 0.05f && q < o.lit * 0.5f) {
                        fill(if (q < 0.08f) rgba(170, 210, 255, 0.85f) else rgba(255, 214, 130, 0.85f))
                        fillRect(x + 5 + col * 11, top + 8 + row * 15, 5f, 7f)
                    } else if (o.dayWindows != null && q < 0.7f) {
                        fill(o.dayWindows)
                        fillRect(x + 5 + col * 11, top + 8 + row * 15, 5f, 7f)
                    }
                }
            }
        }
    }
}

/** Avion de ligne vu de profil, nez vers la droite (dir = -1 pour l'inverser). */
internal fun Ctx2D.drawAirliner(
    x: Float,
    y: Float,
    s: Float,
    dir: Int = 1,
    silhouette: Color? = null,
    rot: Float = 0f,
    lights: Boolean = false,
) {
    val sil = silhouette
    saved {
        translate(x, y)
        if (rot != 0f) rotate(rot)
        scale(s * dir, s)
        val accent = sil ?: hex("#e8503f")
        val wingC = sil ?: hex("#b9bfca")
        val farWing = sil ?: hex("#98a0ad")
        // aile lointaine (derrière le fuselage)
        fill(farWing)
        beginPath(); moveTo(30f, -18f); lineTo(-70f, -78f); lineTo(-40f, -80f); lineTo(84f, -20f); closePath(); fill()
        // dérive
        fill(accent)
        beginPath(); moveTo(-200f, -30f); lineTo(-258f, -130f); lineTo(-218f, -130f); lineTo(-136f, -30f); closePath(); fill()
        // stabilisateur lointain
        fill(farWing)
        beginPath(); moveTo(-212f, -12f); lineTo(-260f, -42f); lineTo(-238f, -44f); lineTo(-178f, -14f); closePath(); fill()
        // fuselage
        fill = if (sil != null) androidx.compose.ui.graphics.SolidColor(sil) else linearGradient(
            0f, -36f, 0f, 36f, 0f to Color.White, 0.55f to hex("#edf0f5"), 1f to hex("#bfc6d2"),
        )
        beginPath()
        moveTo(-245f, -34f); lineTo(175f, -34f)
        bezierCurveTo(232f, -34f, 262f, -14f, 266f, 6f)
        bezierCurveTo(262f, 26f, 236f, 34f, 190f, 34f)
        lineTo(-215f, 34f)
        bezierCurveTo(-250f, 30f, -272f, 6f, -275f, -14f)
        bezierCurveTo(-270f, -30f, -258f, -34f, -245f, -34f)
        closePath()
        fill()
        if (sil == null) {
            saved {
                clip()
                fill(accent); fillRect(-300f, 11f, 600f, 7f)
                fill(hex("#2a3b6e")); fillRect(-300f, 18f, 600f, 4f)
            }
            fill(hex("#3a4a66"))
            var wx = -205f
            while (wx <= 150f) { fillRoundRect(wx, -16f, 8f, 10f, 3f); wx += 15f }
            fill(hex("#26334d"))
            beginPath(); moveTo(206f, -21f); lineTo(238f, -17f); lineTo(247f, -8f); lineTo(211f, -9f); closePath(); fill()
            stroke(rgba(80, 90, 110, 0.55f)); lineWidth = 1.5f
            strokeRoundRect(168f, -24f, 14f, 38f, 4f)
            strokeRoundRect(-192f, -24f, 14f, 38f, 4f)
            saved {
                translate(-206f, -74f); rotate(-1.05f)
                fill(Color.White); font = Ctx2D.Font(17f, FontWeight.Black)
                textAlign = Ctx2D.Align.LEFT; textBaseline = Ctx2D.Baseline.ALPHABETIC
                fillText("BULLY", -26f, 6f)
            }
        }
        // aile proche + réacteur
        fill(wingC)
        beginPath(); moveTo(58f, 8f); lineTo(-85f, 84f); lineTo(-48f, 88f); lineTo(104f, 16f); closePath(); fill()
        fill(sil ?: hex("#8d95a2"))
        fillRoundRect(-6f, 34f, 72f, 24f, 12f)
        if (sil == null) {
            fill(hex("#2b2f38")); fillEllipse(64f, 46f, 5f, 11f)
            fill(rgba(255, 255, 255, 0.35f)); fillRect(2f, 37f, 54f, 3f)
        }
        fill(wingC)
        beginPath(); moveTo(-205f, 4f); lineTo(-262f, 30f); lineTo(-238f, 32f); lineTo(-170f, 6f); closePath(); fill()
        if (lights) {
            val on = floor(VilleClock.millis / 450).toLong() % 2 != 0L
            fill(if (on) hex("#ff3b3b") else rgba(255, 59, 59, 0.3f)); fillCircle(-68f, 86f, 5f)
            fill(if (on) Color.White else rgba(255, 255, 255, 0.3f)); fillCircle(-240f, -130f, 4f)
        }
    }
}

/* ---- Toit-terrain de lancer ---- */

private val ROOF_PROPS = listOf("ac", "vent", "ac", "dish", "skylight", "antenna", "solar", "chimney", "tank", "vent", "pigeon", "ac")

private fun Ctx2D.drawRooftopProp(kind: String, x: Float, gY: Float, seed: Int, night: Float, outage: Boolean) {
    val now = VilleClock.millis
    saved {
        when (kind) {
            "ac" -> {
                fill(hex("#b9bec6")); fillRoundRect(x - 40, gY - 46, 80f, 46f, 4f)
                fill(hex("#9ea4ad")); fillRect(x - 40, gY - 8, 80f, 8f)
                stroke(hex("#7d838c")); lineWidth = 2f
                var i = -30f
                while (i <= -8f) { line(x + i, gY - 38, x + i, gY - 14); i += 5f }
                fill(hex("#6e747d")); fillCircle(x + 16, gY - 26, 14f)
                saved {
                    translate(x + 16, gY - 26); rotate((now / 120 * (if (outage) 0 else 1)).toFloat())
                    fill(hex("#b9bec6"))
                    repeat(3) { rotate(TAU / 3); fillRect(-2f, -12f, 4f, 12f) }
                }
            }
            "vent" -> {
                fill(hex("#8f959e")); fillRect(x - 7, gY - 54, 14f, 54f)
                fill(hex("#737982")); fillRoundRect(x - 16, gY - 64, 32f, 12f, 4f)
                fill(hex("#a6acb4")); fillRect(x + 22, gY - 30, 10f, 30f)
                fill(hex("#737982")); fillRect(x + 19, gY - 34, 16f, 6f)
            }
            "tank" -> {
                stroke(hex("#4a3a2c")); lineWidth = 5f
                beginPath()
                moveTo(x - 30, gY); lineTo(x - 24, gY - 70); moveTo(x + 30, gY); lineTo(x + 24, gY - 70)
                moveTo(x - 27, gY - 30); lineTo(x + 27, gY - 45)
                stroke()
                fill(hex("#8a5a35")); fillRoundRect(x - 38, gY - 146, 76f, 80f, 8f)
                fill(rgba(0, 0, 0, 0.18f))
                var k = -30f
                while (k <= 30f) { fillRect(x + k, gY - 146, 3f, 80f); k += 12f }
                fill(hex("#5a3a22")); beginPath(); moveTo(x - 42, gY - 146); lineTo(x, gY - 172); lineTo(x + 42, gY - 146); fill()
            }
            "antenna" -> {
                stroke(hex("#8b9099")); lineWidth = 3f
                line(x, gY, x, gY - 150)
                lineWidth = 2f
                for (k in 0 until 4) line(x - 16 + k * 3, gY - 60 - k * 22, x + 16 - k * 3, gY - 60 - k * 22)
                fill(
                    when {
                        outage -> hex("#552222")
                        floor(now / 600).toLong() % 2 != 0L && night > 0.2f -> hex("#ff3030")
                        else -> hex("#aa2222")
                    },
                )
                fillCircle(x, gY - 152, 4f)
            }
            "dish" -> {
                fill(hex("#7f858e")); fillRect(x - 3, gY - 40, 6f, 40f)
                saved {
                    translate(x, gY - 48); rotate(-0.5f)
                    fill(hex("#dfe3e8")); fillEllipse(0f, 0f, 26f, 10f)
                    stroke(hex("#7f858e")); lineWidth = 2f; line(0f, 0f, 0f, -22f)
                }
            }
            "skylight" -> {
                fill(hex("#7c828b")); fillRect(x - 46, gY - 12, 92f, 12f)
                fill(if (night > 0.4f && !outage) rgba(255, 210, 130, 0.85f) else rgba(150, 200, 235, 0.75f))
                beginPath(); moveTo(x - 42, gY - 12); lineTo(x - 20, gY - 38); lineTo(x + 42, gY - 38); lineTo(x + 42, gY - 12); fill()
                stroke(hex("#5e646c")); lineWidth = 2f
                var k = -20f
                while (k <= 42f) { line(x + k, gY - 38, x + k - 4, gY - 12); k += 16f }
            }
            "solar" -> {
                for (k in 0 until 3) {
                    val px = x - 60 + k * 42
                    fill(hex("#6f757e")); fillRect(px + 16, gY - 18, 3f, 18f)
                    fill(hex("#23406e"))
                    beginPath(); moveTo(px, gY - 14); lineTo(px + 10, gY - 40); lineTo(px + 44, gY - 40); lineTo(px + 34, gY - 14); fill()
                    stroke(rgba(160, 200, 255, 0.4f)); lineWidth = 1f
                    line(px + 5, gY - 27, px + 39, gY - 27)
                }
            }
            "chimney" -> {
                fill(hex("#8a4b3c")); fillRect(x - 14, gY - 70, 28f, 70f)
                fill(hex("#6e3a2e")); fillRect(x - 18, gY - 76, 36f, 8f)
                fill(rgba(0, 0, 0, 0.12f))
                for (k in 0 until 6) fillRect(x - 14, gY - 64 + k * 11, 28f, 2f)
            }
            "pigeon" -> {
                val bob = sin(now / 180 + seed * 10).toFloat() * 1.5f
                fill(hex("#8e94a3")); fillEllipse(x, gY - 9 + bob, 10f, 7f)
                fill(hex("#6d7384")); fillCircle(x + 8, gY - 16 + bob, 5f)
                fill(hex("#e0a030")); fillRect(x + 12, gY - 16 + bob, 4f, 2f)
                fill(hex("#c05050")); fillRect(x - 2, gY - 3, 2f, 3f); fillRect(x + 3, gY - 3, 2f, 3f)
            }
        }
    }
}

/**
 * Le toit-terrain de lancer, dessiné à la place du décor habituel quand on
 * lance depuis la Ville (`drawRooftop()`). [camX] et [gY] sont en pixels
 * écran.
 *
 * [k] agrandit le décor (ciel, silhouettes, accessoires, textes) sans
 * toucher aux positions qui comptent pour le jeu (flaques, repères de
 * distance, en pixels "monde") : le canvas de jeu Android travaille en
 * pixels physiques, là où le site dessine en pixels CSS — sans ce facteur,
 * une clim du toit serait trois fois plus petite que la trousse.
 */
internal fun Ctx2D.drawRooftop(
    w: Float,
    h: Float,
    camX: Float,
    gY: Float,
    event: VilleEventType?,
    puddles: List<Puddle>,
    k: Float = 1f,
) {
    val sky = VilleSky.now()
    val outage = event == VilleEventType.COUPURE
    saved {
        scale(k, k)
        drawVilleSky(w / k, h / k, (gY - 30 * k) / k, sky, camX / k)
    }
    if (event == VilleEventType.PLUIE) { fill(rgba(70, 80, 100, 0.45f)); fillRect(0f, 0f, w, gY) }
    if (event == VilleEventType.CANICULE) { fill(rgba(255, 150, 60, 0.16f)); fillRect(0f, 0f, w, gY) }
    val lit = if (outage) 0f else sky.night
    saved {
        scale(k, k)
        drawSkyline(w / k, (gY - 26 * k) / k, camX / k, 0.04f, 11, SkylineStyle(hazeColor(sky, 0.72f), lit * 0.8f, 60f, 190f, bw = 70f))
        drawSkyline(
            w / k, (gY - 6 * k) / k, camX / k, 0.16f, 23,
            SkylineStyle(hazeColor(sky, 0.5f), lit, 90f, 290f, bw = 120f, dayWindows = if (sky.night < 0.5f) rgba(255, 255, 255, 0.12f) else null),
        )
    }
    // surface du toit
    val dark = 1 - sky.night * 0.45f
    fun shadeOf(r: Int, g: Int, b: Int) = rgba((r * dark).roundToInt(), (g * dark).roundToInt(), (b * dark).roundToInt())
    fill = linearGradient(0f, gY, 0f, h, 0f to shadeOf(96, 100, 108), 1f to shadeOf(58, 60, 66))
    fillRect(0f, gY, w, h - gY)
    // gravier
    fill(rgba(255, 255, 255, 0.08f))
    val cell = 16f * k
    for (i in floor(camX / cell).toInt()..floor((camX + w) / cell).toInt()) {
        for (j in 0 until 3) {
            val gx = i * cell + sr(i * 3.3 + j) * cell - camX
            val gy = gY + 10 * k + sr(i * 7.1 + j * 2.2) * (h - gY - 12 * k)
            fillRect(gx, gy, 2f * k, 2f * k)
        }
    }
    // jointures entre buildings voisins + rebord
    val seam = 1100f
    for (i in floor(camX / seam).toInt()..floor((camX + w) / seam).toInt() + 1) {
        if (i <= 0) continue
        val sx = i * seam - camX
        fill(rgba(0, 0, 0, 0.35f)); fillRect(sx - 3 * k, gY, 6f * k, h - gY)
        fill(hex("#9aa0aa")); fillRect(sx - 8 * k, gY - 16 * k, 16f * k, 16f * k)
    }
    fill(rgba(210, 214, 222, 0.9f * dark))
    fillRect(0f, gY - 3 * k, w, 6f * k)
    // accessoires du toit
    val slot = 250f * k
    for (i in floor((camX - 200 * k) / slot).toInt()..floor((camX + w + 200 * k) / slot).toInt()) {
        if (i < 2) continue
        if (sr(i * 3.37) < 0.3f) continue
        val kind = ROOF_PROPS[(sr(i * 9.1) * ROOF_PROPS.size).toInt().coerceIn(0, ROOF_PROPS.size - 1)]
        saved {
            translate(i * slot + sr(i * 1.3) * 120 * k - camX, gY)
            scale(k, k)
            drawRooftopProp(kind, 0f, 0f, i, sky.night, outage)
        }
    }
    // repères peints tous les 100 m (SCALE = 12 px/m côté jeu)
    fill(rgba(255, 255, 255, 0.35f))
    font = Ctx2D.Font(15f * k, FontWeight.ExtraBold)
    textAlign = Ctx2D.Align.CENTER
    textBaseline = Ctx2D.Baseline.ALPHABETIC
    var m = max(100, (floor(camX / 1200).toInt()) * 100)
    while (m * 12 < camX + w + 60 * k) {
        val sx = m * 12 - camX
        fillText("$m m", sx, gY + 32 * k)
        fillRect(sx - 1 * k, gY + 6 * k, 2f * k, 10f * k)
        m += 100
    }
    textAlign = Ctx2D.Align.LEFT
    // flaques (fuite d'eau / pluie)
    val t = VilleClock.seconds
    for (pd in puddles) {
        val sx = (pd.centerX - camX).toFloat()
        val hw = pd.halfWidth.toFloat()
        fill(rgba(70, 140, 215, 0.8f)); fillEllipse(sx, gY + 12 * k, hw, 9f * k)
        fill(rgba(200, 235, 255, 0.55f)); fillEllipse(sx - hw * 0.3f, gY + 9 * k, hw * 0.35f, 2.5f * k)
        if (event == VilleEventType.PLUIE) {
            stroke(rgba(220, 240, 255, 0.5f)); lineWidth = 1f * k
            val rp = (t * 1.3f + sr(pd.centerX)) % 1f
            alpha = 1 - rp
            strokeEllipse(sx + hw * 0.2f, gY + 12 * k, (6 + rp * 22) * k, (1.5f + rp * 4) * k)
            alpha = 1f
        }
        if (event == VilleEventType.FUITE) {
            // tuyau percé qui goutte au-dessus de la flaque
            fill(hex("#7d838c")); fillRect(sx - 4 * k, gY - 20 * k, 8f * k, 20f * k)
            val dp = (t * 2 + sr(pd.centerX) * 3) % 1f
            fill(rgba(120, 190, 255, 0.9f)); fillCircle(sx + 6 * k, gY + (-16 + dp * 26) * k, 2.5f * k)
        }
    }
}

/** Pluie, air qui ondule (canicule) ou voile sombre (coupure) par-dessus la scène. */
internal fun Ctx2D.drawVilleWeather(w: Float, h: Float, gY: Float, event: VilleEventType?, k: Float = 1f) {
    event ?: return
    val t = VilleClock.seconds
    when (event) {
        VilleEventType.PLUIE -> saved {
            stroke(rgba(200, 220, 255, 0.45f)); lineWidth = 1.3f * k
            beginPath()
            for (i in 0 until 140) {
                val x = ((sr(i * 1.7) * (w + 200 * k) - t * 260 * k) % (w + 200 * k) + w + 200 * k) % (w + 200 * k) - 100 * k
                val y = (sr(i * 5.3) * h + t * 900 * k) % h
                moveTo(x, y); lineTo(x - 6 * k, y + 18 * k)
            }
            stroke()
        }
        VilleEventType.CANICULE -> saved {
            stroke(rgba(255, 255, 255, 0.08f)); lineWidth = 2f * k
            for (j in 0 until 6) {
                beginPath()
                val y = gY - (20 + j * 16) * k
                var x = 0f
                while (x <= w) { lineTo(x, y + sin(x / (40 * k) + t * 3 + j) * 3 * k); x += 20f * k }
                stroke()
            }
        }
        VilleEventType.COUPURE -> { fill(rgba(0, 0, 10, 0.12f)); fillRect(0f, 0f, w, h) }
        VilleEventType.FUITE -> Unit
    }
}

/* ---- Petits éléments réutilisés ---- */

internal fun Ctx2D.star(x: Float, y: Float, r: Float) {
    beginPath()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
        val rad = if (i % 2 != 0) r * 0.45f else r
        lineTo(x + cos(a) * rad, y + sin(a) * rad)
    }
    closePath()
    fill()
}

/** `drawCrab()` du site : pattes qui frétillent, pinces, corps, yeux sur tiges. */
internal fun Ctx2D.drawCrab(x: Float, y: Float, size: Float, t: Float, dir: Int) {
    val k = size / 34
    val wig = sin(t * 9) * 3 * k
    saved {
        translate(x, y)
        scale(if (dir < 0) -1f else 1f, 1f)
        stroke(hex("#a52a1a")); lineWidth = 2.4f * k; lineCap = StrokeCap.Round
        for (i in -1..1) {
            for (sgn in intArrayOf(-1, 1)) {
                line(sgn * 9 * k, -2 * k + i * 4 * k, sgn * (19 * k + i), 4 * k + i * 3 * k + wig * (i + 2) * 0.3f)
            }
        }
        // pinces
        for (sgn in intArrayOf(-1, 1)) {
            line(sgn * 11 * k, -6 * k, sgn * 21 * k, -13 * k - wig * 0.4f)
            fill(hex("#e8503f"))
            fillEllipse(sgn * 24 * k, -15 * k - wig * 0.4f, 6 * k, 4.5f * k, sgn * 0.5f)
        }
        // corps
        fill(hex("#e8503f")); fillEllipse(0f, 0f, 14 * k, 10 * k)
        fill(rgba(0, 0, 0, 0.12f)); fillEllipse(0f, 4 * k, 12 * k, 4 * k)
        // yeux sur tiges
        for (sgn in intArrayOf(-1, 1)) {
            stroke(hex("#e8503f")); lineWidth = 2 * k
            line(sgn * 5 * k, -8 * k, sgn * 5 * k, -15 * k)
            fill(Color.White); fillCircle(sgn * 5 * k, -17 * k, 3.2f * k)
            fill(hex("#1a1a1a")); fillCircle(sgn * 5 * k, -17 * k, 1.5f * k)
        }
    }
}

/** Casquettes/accessoires par-dessus le crabe (`drawCrabOutfit()`). */
internal fun Ctx2D.drawCrabOutfit(x: Float, y: Float, size: Float, dir: Int, kind: String) {
    val k = size / 34
    saved {
        translate(x, y)
        when (kind) {
            "police", "airline" -> {
                fill(if (kind == "police") hex("#1d2b52") else hex("#b8322a"))
                fillRoundRect(-9 * k, -14 * k, 18 * k, 7 * k, 2 * k)
                fill(if (kind == "police") hex("#111a33") else hex("#7a1f19"))
                fillEllipse(dir * 3 * k, -7.5f * k, 12 * k, 2.4f * k)
                fill(hex("#ffd23f")); fillCircle(0f, -11 * k, 1.8f * k)
                if (kind == "police") { fill(hex("#ffd23f")); star(6 * k * dir, 2 * k, 3 * k) }
            }
            "bowtie" -> {
                fill(hex("#c0392b"))
                beginPath(); moveTo(0f, -7 * k); lineTo(-6 * k, -10 * k); lineTo(-6 * k, -4 * k); closePath(); fill()
                beginPath(); moveTo(0f, -7 * k); lineTo(6 * k, -10 * k); lineTo(6 * k, -4 * k); closePath(); fill()
                fillCircle(0f, -7 * k, 1.6f * k)
            }
            "sunglasses" -> { fill(hex("#111111")); fillRect(-9 * k, -19 * k, 18 * k, 4 * k) }
        }
    }
}

/** Couleur #rrggbb assombrie d'un facteur [k] (`shade()`). */
internal fun shade(color: Int, k: Float): Color =
    rgba((((color shr 16) and 255) * k).roundToInt(), (((color shr 8) and 255) * k).roundToInt(), ((color and 255) * k).roundToInt())

/** Voiture vue de face (phares) ou de dos (feux rouges), (x, y) = sol. */
internal fun Ctx2D.drawCarFacing(x: Float, y: Float, s: Float, color: Int, fromFront: Boolean, night: Boolean) {
    saved {
        translate(x, y)
        scale(s, s)
        fill(rgba(0, 0, 0, 0.3f)); fillEllipse(0f, 0f, 54f, 8f)
        fill(hex("#15171c")); fillRect(-46f, -16f, 16f, 16f); fillRect(30f, -16f, 16f, 16f)
        fill(Ctx2D.rgb(color)); fillRoundRect(-50f, -56f, 100f, 44f, 12f)
        fill(shade(color, 0.8f)); fillRoundRect(-36f, -84f, 72f, 32f, 12f)
        fill(if (fromFront) hex("#1c2433") else hex("#2a3140")); fillRoundRect(-30f, -80f, 60f, 22f, 6f)
        fill(rgba(255, 255, 255, 0.18f)); fillRect(-26f, -78f, 18f, 4f)
        fill(hex("#e9ecf1")); fillRect(-12f, -26f, 24f, 9f)
        fill(hex("#22262e")); fillRect(-50f, -16f, 100f, 5f)
        fill(if (fromFront) hex("#fff6d0") else hex("#ff3030"))
        fillRoundRect(-44f, -44f, 16f, 10f, 4f)
        fillRoundRect(28f, -44f, 16f, 10f, 4f)
        if (fromFront) {
            fill(hex("#2c3038"))
            var k = -20f
            while (k <= 16f) { fillRect(k, -40f, 3f, 8f); k += 6f }
        }
    }
    if (night && fromFront) {
        fill = radialGradient(x, y - 38 * s, 4f, 120 * s, 0f to rgba(255, 245, 200, 0.4f), 1f to rgba(255, 245, 200, 0f))
        fillRect(x - 120 * s, y - 158 * s, 240 * s, 240 * s)
    }
}

/**
 * Portail rond : anneau de pierre grise, halo et tourbillon verts. [broken]
 * = anneau fêlé, éteint (fumée/étincelles gérées par le moteur).
 */
internal fun Ctx2D.drawPortal(x: Float, y: Float, r: Float, t: Float, broken: Boolean, power: Float = 1f) {
    saved {
        if (!broken && power > 0) {
            fill = radialGradient(x, y, r * 0.6f, r * 1.7f, 0f to rgba(90, 255, 140, 0.45f * power), 1f to rgba(90, 255, 140, 0f))
            fillRect(x - r * 1.8f, y - r * 1.8f, r * 3.6f, r * 3.6f)
            fill = radialGradient(
                x, y, 0f, r,
                0f to rgba(210, 255, 220, 0.95f * power),
                0.5f to rgba(60, 220, 110, 0.8f * power),
                1f to rgba(10, 90, 40, 0.9f * power),
            )
            fillCircle(x, y, r * 0.9f)
            lineWidth = 3f
            for (i in 0 until 7) {
                stroke(rgba(200, 255, 210, (0.25f + 0.1f * (i % 3)) * power))
                val rad = r * (0.2f + i * 0.1f)
                val a0 = t * (1.2f + i * 0.3f) * (if (i % 2 != 0) 1 else -1) + i
                beginPath(); arc(x, y, rad, a0, a0 + 2.2f); stroke()
            }
        } else {
            fill(hex("#15181d")); fillCircle(x, y, r * 0.9f)
        }
        // anneau de pierre
        lineWidth = r * 0.22f
        stroke(hex("#8d9097"))
        if (broken) {
            beginPath(); arc(x, y, r, 0.5f, TAU - 0.2f); stroke()
            stroke(hex("#6f737a"))
            beginPath(); arc(x + 6, y + 8, r, 0.05f, 0.38f); stroke()
        } else {
            strokeCircle(x, y, r)
        }
        stroke(hex("#5f636a")); lineWidth = 2f
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6
            if (broken && a > 0.05f && a < 0.5f) continue
            line(x + cos(a) * r * 0.89f, y + sin(a) * r * 0.89f, x + cos(a) * r * 1.11f, y + sin(a) * r * 1.11f)
        }
        if (broken) {
            stroke(hex("#2b2e33")); lineWidth = 2.5f
            beginPath(); moveTo(x - r * 0.7f, y - r * 0.75f); lineTo(x - r * 0.55f, y - r * 0.5f); lineTo(x - r * 0.7f, y - r * 0.3f); stroke()
            beginPath(); moveTo(x + r * 0.9f, y + r * 0.3f); lineTo(x + r * 0.7f, y + r * 0.45f); stroke()
        }
    }
}
