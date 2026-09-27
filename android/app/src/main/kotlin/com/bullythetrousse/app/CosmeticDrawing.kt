package com.bullythetrousse.app

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bullythetrousse.app.Ctx2D.Companion.hex
import com.bullythetrousse.app.Ctx2D.Companion.rgba
import kotlin.math.PI

/**
 * Le cosmétique porté (La Trousserie, monde Ville), dessiné par-dessus la
 * trousse : portage de `drawCosmetic()` (index.html). Le repère est centré
 * sur le sprite — le boîtier occupe à peu près y de -0,19 à +0,2 et x de
 * -0,25 à +0,36 de [size] — et suit la rotation de la trousse.
 *
 * Écart volontaire avec le site : là-bas, pour la Trousse Pièce et la
 * Trousse de Fer, le repère a déjà été décalé d'une demi-taille avant
 * l'appel, et le cosmétique flotte à côté de la trousse. Ici il est à sa
 * place pour tous les skins.
 */
internal fun DrawScope.drawCosmetic(id: String, centerX: Float, centerY: Float, size: Float, rotationRadians: Float) {
    if (id.isEmpty()) return
    val c = Ctx2D(this, null)
    c.saved {
        c.translate(centerX, centerY)
        c.rotate(rotationRadians)
        c.drawCosmeticShape(id, size)
    }
}

/** Le dessin lui-même, dans un repère déjà centré sur la trousse. */
internal fun Ctx2D.drawCosmeticShape(id: String, s: Float) {
    val top = -0.19f * s
    val cx = 0.06f * s
    saved {
        when (id) {
            "chapeau" -> {
                fill(hex("#15151a")); fillRect(cx - 0.24f * s, top - 0.04f * s, 0.48f * s, 0.05f * s)
                fillRect(cx - 0.14f * s, top - 0.34f * s, 0.28f * s, 0.31f * s)
                fill(hex("#b3202a")); fillRect(cx - 0.14f * s, top - 0.1f * s, 0.28f * s, 0.05f * s)
            }
            "casquette" -> {
                fill(hex("#2f6fb5"))
                beginPath(); arc(cx, top + 0.01f * s, 0.17f * s, PI.toFloat(), 0f); fill()
                fill(hex("#1f4f85")); fillRect(cx + 0.1f * s, top - 0.02f * s, 0.2f * s, 0.04f * s)
                fill(hex("#ffffff")); fillCircle(cx, top - 0.16f * s, 0.025f * s)
            }
            "couronne" -> {
                fill(hex("#ffd23f"))
                beginPath(); moveTo(cx - 0.2f * s, top + 0.01f * s)
                val ks = floatArrayOf(-0.2f, -0.1f, 0f, 0.1f, 0.2f)
                ks.forEachIndexed { i, k ->
                    lineTo(cx + k * s, top - 0.2f * s)
                    if (i < 4) lineTo(cx + (k + 0.05f) * s, top - 0.08f * s)
                }
                lineTo(cx + 0.2f * s, top + 0.01f * s); closePath(); fill()
                fill(hex("#e8503f")); fillCircle(cx, top - 0.05f * s, 0.025f * s)
            }
            "lunettes" -> {
                fill(hex("#111111"))
                fillRect(cx - 0.17f * s, -0.08f * s, 0.14f * s, 0.08f * s)
                fillRect(cx + 0.03f * s, -0.08f * s, 0.14f * s, 0.08f * s)
                fillRect(cx - 0.03f * s, -0.07f * s, 0.06f * s, 0.02f * s)
                fill(rgba(255, 255, 255, 0.35f))
                fillRect(cx - 0.15f * s, -0.07f * s, 0.04f * s, 0.02f * s)
                fillRect(cx + 0.05f * s, -0.07f * s, 0.04f * s, 0.02f * s)
            }
            "noeud" -> {
                fill(hex("#c0392b"))
                beginPath(); moveTo(cx, 0.16f * s); lineTo(cx - 0.1f * s, 0.11f * s); lineTo(cx - 0.1f * s, 0.21f * s); closePath(); fill()
                beginPath(); moveTo(cx, 0.16f * s); lineTo(cx + 0.1f * s, 0.11f * s); lineTo(cx + 0.1f * s, 0.21f * s); closePath(); fill()
                fill(hex("#8e2418")); fillCircle(cx, 0.16f * s, 0.025f * s)
            }
            "moustache" -> {
                fill(hex("#3b2414"))
                fillEllipse(cx - 0.06f * s, 0.06f * s, 0.07f * s, 0.03f * s, 0.3f)
                fillEllipse(cx + 0.06f * s, 0.06f * s, 0.07f * s, 0.03f * s, -0.3f)
            }
            "aureole" -> {
                stroke(hex("#ffe066")); lineWidth = 0.035f * s
                strokeEllipse(cx, top - 0.14f * s, 0.17f * s, 0.05f * s)
            }
            "antennes" -> {
                for (d in intArrayOf(-1, 1)) {
                    stroke(hex("#2e7d32")); lineWidth = 0.025f * s
                    line(cx + d * 0.07f * s, top, cx + d * 0.13f * s, top - 0.22f * s)
                    fill(hex("#7cfc00")); fillCircle(cx + d * 0.13f * s, top - 0.24f * s, 0.04f * s)
                }
            }
        }
    }
}
