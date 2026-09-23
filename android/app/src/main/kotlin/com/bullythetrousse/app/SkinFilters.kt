package com.bullythetrousse.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Le site n'a qu'UN sprite de trousse (`trousse-skin-1.png`) : chaque skin
 * le recolore avec un filtre CSS (`filter: hue-rotate(...) saturate(...)`,
 * voir le tableau SKINS d'index.html). Ce fichier rejoue ces filtres sous
 * forme de matrices de couleur Compose, pour que les 14 trousses aient les
 * mêmes teintes que sur le site au lieu d'être toutes identiques.
 *
 * Les matrices viennent des définitions de la spécification CSS Filter
 * Effects (les mêmes que celles utilisées par les navigateurs).
 */

/** Filtre CSS de chaque skin, repris tel quel du tableau SKINS du site. */
val SKIN_FILTERS: Map<String, String> = mapOf(
    "classique" to "none",
    "doree" to "sepia(0.6) saturate(4) hue-rotate(-10deg) brightness(1.15)",
    "glacee" to "hue-rotate(150deg) saturate(2) brightness(1.1)",
    "feu" to "hue-rotate(-60deg) saturate(3) brightness(1.05)",
    "arcenciel" to "rainbow",
    "piece" to "none",
    "basket" to "none",
    "lunaire" to "grayscale(1) brightness(1.7) contrast(0.85)",
    "fer" to "none",
    "claude" to "sepia(0.5) saturate(1.8) hue-rotate(-15deg) brightness(0.95)",
    "fusee" to "grayscale(0.25) saturate(1.5) hue-rotate(190deg) brightness(1.05)",
    "avion" to "grayscale(0.5) brightness(1.2) contrast(0.9)",
    "fantome" to "grayscale(1) brightness(1.4) opacity(0.85)",
    "vampire" to "grayscale(0.4) saturate(2.5) hue-rotate(260deg) brightness(0.8)",
)

/**
 * Traduit un filtre CSS en [ColorFilter], ou `null` quand il n'y a rien à
 * appliquer. `rainbow` (Trousse Arc-en-ciel) est le cas spécial de
 * `rainbowOrFilter()` côté web : la teinte tourne en continu, donc
 * [hueDegrees] permet de l'animer.
 */
fun skinColorFilter(skinId: String, hueDegrees: Float = 0f): ColorFilter? {
    val css = SKIN_FILTERS[skinId] ?: return null
    if (css == "none") return null
    val effective = if (css == "rainbow") {
        "hue-rotate(${hueDegrees}deg) saturate(2.2) brightness(1.1)"
    } else {
        css
    }
    return ColorFilter.colorMatrix(ColorMatrix(cssFilterToMatrix(effective)))
}

/** `grayscale(1)` : une trousse pas encore possédée, dans la liste du profil. */
val LockedSkinFilter: ColorFilter by lazy { ColorFilter.colorMatrix(ColorMatrix(cssFilterToMatrix("grayscale(1)"))) }

/**
 * Le filtre d'un skin, prêt à poser sur l'image — en animant la teinte
 * quand c'est la Trousse Arc-en-ciel (le seul skin dont le filtre bouge).
 */
@Composable
fun rememberSkinColorFilter(skinId: String): ColorFilter? {
    if (SKIN_FILTERS[skinId] != "rainbow") return skinColorFilter(skinId)
    val transition = rememberInfiniteTransition(label = "rainbow")
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbowHue",
    )
    return skinColorFilter(skinId, hue)
}

/** Applique les fonctions du filtre l'une après l'autre, comme le fait CSS. */
private fun cssFilterToMatrix(css: String): FloatArray {
    var matrix = identity()
    for (match in FUNCTION_REGEX.findAll(css)) {
        val name = match.groupValues[1]
        val rawValue = match.groupValues[2].trim()
        val amount = rawValue.removeSuffix("deg").toFloatOrNull() ?: continue
        val step = when (name) {
            "brightness" -> brightness(amount)
            "saturate" -> saturate(amount)
            "grayscale" -> saturate(1f - amount)
            "sepia" -> sepia(amount)
            "contrast" -> contrast(amount)
            "opacity" -> opacity(amount)
            "hue-rotate" -> hueRotate(amount)
            else -> continue
        }
        matrix = concat(after = step, before = matrix)
    }
    return matrix
}

private val FUNCTION_REGEX = Regex("""([a-z-]+)\(([^)]*)\)""")

/**
 * Compose une matrice par-dessus une autre : `after` est appliquée au
 * résultat de `before`, comme l'enchaînement des fonctions d'un filtre CSS.
 * Les matrices sont en 4 lignes de 5 colonnes (la 5e étant le décalage).
 */
private fun concat(after: FloatArray, before: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += after[row * 5 + k] * before[k * 5 + col]
            }
            out[row * 5 + col] = sum
        }
        var offset = after[row * 5 + 4]
        for (k in 0 until 4) {
            offset += after[row * 5 + k] * before[k * 5 + 4]
        }
        out[row * 5 + 4] = offset
    }
    return out
}

private fun identity() = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun brightness(k: Float) = floatArrayOf(
    k, 0f, 0f, 0f, 0f,
    0f, k, 0f, 0f, 0f,
    0f, 0f, k, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun opacity(o: Float) = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, o, 0f,
)

/** Le décalage d'une matrice Compose est exprimé sur 0-255, pas sur 0-1. */
private fun contrast(c: Float): FloatArray {
    val offset = (0.5f - c * 0.5f) * 255f
    return floatArrayOf(
        c, 0f, 0f, 0f, offset,
        0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun saturate(s: Float) = floatArrayOf(
    0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun sepia(k: Float): FloatArray {
    // Interpolation entre l'identité et la matrice sépia complète.
    fun mix(identityValue: Float, sepiaValue: Float) = identityValue + (sepiaValue - identityValue) * k
    return floatArrayOf(
        mix(1f, 0.393f), mix(0f, 0.769f), mix(0f, 0.189f), 0f, 0f,
        mix(0f, 0.349f), mix(1f, 0.686f), mix(0f, 0.168f), 0f, 0f,
        mix(0f, 0.272f), mix(0f, 0.534f), mix(1f, 0.131f), 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun hueRotate(degrees: Float): FloatArray {
    val radians = degrees * Math.PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return floatArrayOf(
        0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
        0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
        0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
