package com.bullythetrousse.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bullythetrousse.core.CourDecor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Effets d'ambiance des deux cinématiques : particules (poussière, braises,
 * débris, sable, fumée), lumière (vignetage, halo du soleil, voile chaud),
 * ombres portées et lignes de vitesse.
 *
 * Ce fichier va volontairement PLUS LOIN que le site : le web se contente de
 * `spawnDust()` et d'un aplat de couleur, là où une cinématique gagne
 * beaucoup à avoir de la matière en suspension, une source de lumière et un
 * peu de profondeur. Rien ici n'influe sur le déroulement des séquences —
 * uniquement sur ce qu'on voit.
 *
 * Contrainte assumée : pas de flou (`Modifier.blur`/`RenderEffect` demandent
 * Android 12, et le téléphone visé est en Android 10). La douceur est donc
 * obtenue en superposant des formes très transparentes, pas en floutant.
 */

/** Une particule : position/vitesse en pixels écran, durée de vie en secondes. */
internal class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val radius: Float,
    val color: Color,
    val gravity: Float,
    val drag: Float,
    val shrink: Boolean,
    val glow: Boolean,
    val spin: Float,
    var angle: Float,
)

/**
 * Le nuage de particules d'une cinématique. Les anciennes sont recyclées
 * une fois [MAX] atteint plutôt que de laisser la liste enfler : une
 * cinématique dure plusieurs minutes, et le budget d'une frame sur un
 * téléphone d'entrée de gamme est vite dépassé.
 */
internal class ParticleField {
    val particles = ArrayList<Particle>(MAX)

    fun clear() = particles.clear()

    fun update(dt: Float) {
        var i = 0
        while (i < particles.size) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) {
                particles.removeAt(i)
                continue
            }
            p.vy += p.gravity * dt
            val damp = 1f - min(0.95f, p.drag * dt)
            p.vx *= damp
            p.vy *= damp
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            i++
        }
    }

    /**
     * Émet [count] particules depuis ([x], [y]). [spread] est la vitesse
     * horizontale maximale, [up] la vitesse verticale ascendante moyenne.
     */
    @Suppress("LongParameterList")
    fun burst(
        x: Float,
        y: Float,
        count: Int,
        spread: Float,
        up: Float,
        radius: Float,
        life: Float,
        color: Color,
        gravity: Float = 420f,
        drag: Float = 0.6f,
        jitter: Float = 0f,
        glow: Boolean = false,
        shrink: Boolean = true,
        spin: Float = 0f,
    ) {
        repeat(count) {
            if (particles.size >= MAX) particles.removeAt(0)
            val l = life * (0.65f + Random.nextFloat() * 0.7f)
            particles.add(
                Particle(
                    x = x + (Random.nextFloat() - 0.5f) * jitter,
                    y = y + (Random.nextFloat() - 0.5f) * jitter * 0.4f,
                    vx = (Random.nextFloat() - 0.5f) * 2f * spread,
                    vy = -up * (0.35f + Random.nextFloat()),
                    life = l,
                    maxLife = l,
                    radius = radius * (0.55f + Random.nextFloat() * 0.9f),
                    color = color,
                    gravity = gravity,
                    drag = drag,
                    shrink = shrink,
                    glow = glow,
                    spin = spin * (Random.nextFloat() - 0.5f) * 2f,
                    angle = Random.nextFloat() * 6.28f,
                ),
            )
        }
    }
}

private const val MAX = 260

/**
 * Les particules, des plus anciennes aux plus récentes. Les braises sont
 * précédées d'un halo pour qu'elles éclairent vraiment au lieu d'être de
 * simples points orange.
 */
internal fun DrawScope.drawParticles(field: ParticleField) {
    for (p in field.particles) {
        val t = (p.life / p.maxLife).coerceIn(0f, 1f)
        val alpha = if (t > 0.75f) (1f - t) * 4f else t / 0.75f
        val r = if (p.shrink) p.radius * (0.35f + 0.65f * t) else p.radius
        if (r <= 0.2f || alpha <= 0.01f) continue
        if (p.glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to p.color.copy(alpha = 0.55f * alpha),
                        1f to p.color.copy(alpha = 0f),
                    ),
                    center = Offset(p.x, p.y),
                    radius = r * 4f,
                ),
                radius = r * 4f,
                center = Offset(p.x, p.y),
            )
        }
        drawCircle(color = p.color, radius = r, center = Offset(p.x, p.y), alpha = alpha.coerceIn(0f, 1f))
    }
}

/**
 * Vignetage : assombrit les bords pour concentrer le regard au centre. C'est
 * ce qui fait le plus pour « l'effet cinéma » alors que ça ne coûte qu'un
 * dégradé.
 */
internal fun DrawScope.drawVignette(strength: Float, tint: Color = Color.Black) {
    if (strength <= 0.01f) return
    val radius = max(size.width, size.height) * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.45f to tint.copy(alpha = 0f),
                0.78f to tint.copy(alpha = strength * 0.45f),
                1f to tint.copy(alpha = strength),
            ),
            center = Offset(size.width / 2f, size.height * 0.48f),
            radius = radius,
        ),
    )
}

/** Voile coloré uniforme : la lumière chaude d'une éruption, la fraîcheur de
 *  l'altitude. Sert de « étalonnage » d'un plan à l'autre. */
internal fun DrawScope.drawLightWash(color: Color, strength: Float) {
    if (strength <= 0.01f) return
    drawRect(color = color.copy(alpha = color.alpha * strength))
}

/** Ombre portée au sol : une ellipse qui rétrécit et pâlit avec la hauteur. */
internal fun DrawScope.drawGroundShadow(centerX: Float, groundY: Float, objectSize: Float, height: Float) {
    val k = (1f - (height / 320f)).coerceIn(0.15f, 1f)
    val rx = objectSize * 0.52f * k
    val ry = max(3f, objectSize * 0.15f * k)
    drawOval(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = 0.32f * k),
                1f to Color.Black.copy(alpha = 0f),
            ),
            center = Offset(centerX, groundY),
            radius = max(rx, ry),
        ),
        topLeft = Offset(centerX - rx, groundY - ry),
        size = Size(rx * 2f, ry * 2f),
    )
}

/**
 * Lignes de vitesse verticales : la sensation de montée ou de chute, que le
 * site n'a pas du tout. [speed] positif fait défiler vers le bas (on monte).
 */
internal fun DrawScope.drawSpeedLines(scroll: Float, intensity: Float, color: Color = Color.White) {
    if (intensity <= 0.01f) return
    val w = size.width
    val h = size.height
    val count = 22
    for (i in 0 until count) {
        val seed = CourDecor.seededRand(i * 4.31).toFloat()
        val length = 60f + seed * 180f * intensity
        val x = CourDecor.seededRand(i * 2.13).toFloat() * w
        val y = mod((seed * (h + 400f) + scroll * (0.6f + seed)).toDouble(), (h + 400f).toDouble()).toFloat() - 200f
        drawLine(
            color = color.copy(alpha = (0.05f + 0.16f * seed) * intensity),
            start = Offset(x, y),
            end = Offset(x, y + length),
            strokeWidth = 1.5f + seed * 2f,
            cap = StrokeCap.Round,
        )
    }
}

/** Onde de choc d'un impact : un anneau aplati qui s'élargit en s'effaçant. */
internal fun DrawScope.drawShockwave(centerX: Float, centerY: Float, progress: Float, maxRadius: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val r = maxRadius * progress
    val alpha = (1f - progress) * 0.7f
    drawOval(
        color = color.copy(alpha = alpha),
        topLeft = Offset(centerX - r, centerY - r * 0.28f),
        size = Size(r * 2f, r * 0.56f),
        style = Stroke(width = max(1.5f, 7f * (1f - progress))),
    )
}

/** Halo du soleil : un disque et deux auréoles, pour une vraie source de lumière. */
internal fun DrawScope.drawSunGlow(centerX: Float, centerY: Float, radius: Float, core: Color, halo: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to halo.copy(alpha = 0.45f),
                0.4f to halo.copy(alpha = 0.16f),
                1f to halo.copy(alpha = 0f),
            ),
            center = Offset(centerX, centerY),
            radius = radius * 5f,
        ),
        radius = radius * 5f,
        center = Offset(centerX, centerY),
    )
    drawCircle(color = core.copy(alpha = 0.85f), radius = radius, center = Offset(centerX, centerY))
    drawCircle(color = Color.White.copy(alpha = 0.5f), radius = radius * 0.6f, center = Offset(centerX, centerY))
}

/** Étoiles d'altitude : elles n'apparaissent qu'au-dessus des nuages, et
 *  scintillent doucement. */
internal fun DrawScope.drawStars(intensity: Float, clock: Float) {
    if (intensity <= 0.01f) return
    val w = size.width
    val h = size.height
    for (i in 0 until 34) {
        val sx = CourDecor.seededRand(i * 6.7).toFloat() * w
        val sy = CourDecor.seededRand(i * 3.1).toFloat() * h * 0.55f
        val twinkle = 0.5f + 0.5f * sin(clock * (1.4f + CourDecor.seededRand(i * 1.7).toFloat()) + i)
        drawCircle(
            color = Color.White.copy(alpha = (0.25f + 0.55f * twinkle) * intensity),
            radius = 1f + CourDecor.seededRand(i * 9.3).toFloat() * 1.6f,
            center = Offset(sx, sy),
        )
    }
}

/** Une mouette : deux coups d'aile, en silhouette. Trois suffisent à rendre
 *  un ciel vivant. */
internal fun DrawScope.drawGull(centerX: Float, centerY: Float, scale: Float, flap: Float, color: Color) {
    val span = 14f * scale
    val lift = span * 0.55f * flap
    val wing = Path().apply {
        moveTo(centerX - span, centerY)
        quadraticBezierTo(centerX - span * 0.5f, centerY - lift, centerX, centerY - lift * 0.15f)
        quadraticBezierTo(centerX + span * 0.5f, centerY - lift, centerX + span, centerY)
        quadraticBezierTo(centerX + span * 0.45f, centerY - lift * 0.35f, centerX, centerY + lift * 0.18f)
        quadraticBezierTo(centerX - span * 0.45f, centerY - lift * 0.35f, centerX - span, centerY)
        close()
    }
    drawPath(wing, color = color)
}

/** Reflets du soleil sur l'eau : des traits horizontaux qui ondulent. */
internal fun DrawScope.drawSeaSparkle(topY: Float, bottomY: Float, clock: Float, alpha: Float) {
    if (alpha <= 0.01f || bottomY <= topY) return
    val w = size.width
    for (i in 0 until 26) {
        val seed = CourDecor.seededRand(i * 5.9).toFloat()
        val y = topY + (bottomY - topY) * seed
        val phase = clock * (0.8f + seed) + i
        val x = w * (0.5f + 0.42f * sin(phase * 0.6f)) + (seed - 0.5f) * w * 0.35f
        val len = 8f + 26f * abs(cos(phase))
        drawLine(
            color = Color.White.copy(alpha = alpha * (0.15f + 0.45f * abs(sin(phase)))),
            start = Offset(x - len / 2f, y),
            end = Offset(x + len / 2f, y),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

/** Colonne de fumée : des bouffées qui montent en s'élargissant. Dessinée
 *  hors du système de particules, car elle doit rester derrière le décor. */
internal fun DrawScope.drawSmokeColumn(centerX: Float, baseY: Float, height: Float, clock: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    for (i in 0 until 14) {
        val k = i / 13f
        val drift = sin(clock * 0.7f + i * 0.6f) * 26f * k
        val r = 18f + 62f * k
        drawCircle(
            color = Color(0xFF4A3B34).copy(alpha = alpha * (0.42f - 0.3f * k)),
            radius = r,
            center = Offset(centerX + drift, baseY - height * k),
        )
    }
}

// ---- Courbes d'accélération partagées par les deux cinématiques ----
// Portage de cIn()/cOut()/cInOut()/c01() côté web, mises en commun ici
// plutôt que recopiées dans chaque écran.

internal fun cIn(t: Float) = t * t * t
internal fun cOut(t: Float) = 1f - (1f - t).pow(3)
internal fun cInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f
internal fun c01(v: Float) = v.coerceIn(0f, 1f)
