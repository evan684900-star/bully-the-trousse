package com.bullythetrousse.core

import kotlin.math.ceil
import kotlin.math.floor

/** Type d'accessoire posé sur le sable, purement décoratif ici (voir
 *  [Beach] pour les accessoires qui comptent pour le gameplay). */
enum class BeachPropType { PARASOL, TOWEL, CASTLE }

/** Un accessoire décoratif de plage, porté depuis une entrée de `beachProps`
 *  générée par `generateBeachDecor()` côté web. */
data class BeachProp(val type: BeachPropType, val worldX: Double, val seed: Double, val scale: Double, val depth: Double)

/**
 * Placement du décor du monde "Plage" (dunes lointaines, bosses de sable +
 * coquillages au sol, accessoires décoratifs parasol/serviette/château),
 * porté depuis `drawBeachBackground()`/`generateBeachDecor()` côté web.
 * Comme [CourDecor]/[VolcanoDecor], ne calcule QUE le placement ; le dessin
 * reste dans `:app`.
 *
 * Côté web, `beachDecorInSlot()` mélange une graine tirée à chaque lancer
 * (les accessoires décoratifs changent d'un lancer à l'autre) ; ici,
 * [propInSlot] n'utilise que [CourDecor.seededRand] pour rester déterministe
 * et testable —
 * un choix délibéré puisque leur position exacte n'a aucune incidence sur
 * le jeu, seulement sur le décor.
 */
object BeachDecor {
    const val DUNE_SPACING = 320.0
    const val DUNE_PARALLAX = 0.18
    const val SAND_BUMP_SPACING = 150.0
    const val PROP_SLOT = 520.0

    /** `dStart..dEnd` des dunes lointaines. */
    fun visibleDuneIndices(cameraX: Double, screenWidth: Double): IntRange {
        val parX = cameraX * DUNE_PARALLAX
        val start = floor((parX - DUNE_SPACING) / DUNE_SPACING).toInt()
        val end = ceil((parX + screenWidth + DUNE_SPACING) / DUNE_SPACING).toInt()
        return start..end
    }

    fun duneScreenX(index: Int, cameraX: Double): Double = index * DUNE_SPACING - cameraX * DUNE_PARALLAX

    /** `dh = 34 + seededRand(i*2.3)*30`, `dw = 190 + seededRand(i*5.1)*130`. */
    fun duneHeight(index: Int): Double = 34.0 + CourDecor.seededRand(index * 2.3) * 30.0
    fun duneWidth(index: Int): Double = 190.0 + CourDecor.seededRand(index * 5.1) * 130.0

    /** `bStart..bStart+ceil(w/spacing)+2` des bosses de sable (+ coquillages). */
    fun visibleSandBumpIndices(cameraX: Double, screenWidth: Double): IntRange {
        val start = floor(cameraX / SAND_BUMP_SPACING).toInt() - 1
        val count = ceil(screenWidth / SAND_BUMP_SPACING).toInt() + 2
        return start until (start + count)
    }

    /** `worldPos = i * bumpSpacing + (seededRand(i+0.4)-0.5)*70`. */
    fun sandBumpWorldX(index: Int): Double = index * SAND_BUMP_SPACING + (CourDecor.seededRand(index + 0.4) - 0.5) * 70.0
    fun sandBumpHeight(index: Int): Double = 10.0 + CourDecor.seededRand(index * 1.7) * 16.0
    fun sandBumpWidth(index: Int): Double = 90.0 + CourDecor.seededRand(index * 3.1) * 70.0

    /** `seededRand(i*7.9) > 0.45` : un coquillage sur cette bosse ou pas. */
    fun hasShell(index: Int): Boolean = CourDecor.seededRand(index * 7.9) > 0.45

    /** `BEACH_DECOR_MAX_M` côté site : le décor couvre 2 000 000 m. */
    const val PROP_MAX_METERS = 2_000_000.0

    /** Nombre de cases d'accessoires jusqu'à [PROP_MAX_METERS]. */
    val PROP_COUNT: Int = ceil(PROP_MAX_METERS * PhysicsConstants.SCALE / PROP_SLOT).toInt()

    /**
     * `beachDecorInSlot(i)` : l'accessoire de la case [index], ou rien hors de
     * la portée. Calculé à la demande comme les flaques de la Ville : plus de
     * 46 000 accessoires à 2 000 000 m, qu'il serait inutile de garder en
     * mémoire ou de parcourir à chaque frame.
     */
    fun propInSlot(index: Int): BeachProp? {
        if (index < 0 || index >= PROP_COUNT) return null
        val roll = CourDecor.seededRand(index.toDouble())
        val type = when {
            roll < 0.4 -> BeachPropType.PARASOL
            roll < 0.75 -> BeachPropType.TOWEL
            else -> BeachPropType.CASTLE
        }
        return BeachProp(
            type = type,
            worldX = 350.0 + index * PROP_SLOT + CourDecor.seededRand(index + 0.5) * (PROP_SLOT - 220.0),
            seed = CourDecor.seededRand(index + 0.9) * 1000.0,
            scale = 0.8 + CourDecor.seededRand(index + 0.2) * 0.4,
            depth = 10.0 + CourDecor.seededRand(index + 0.7) * 40.0,
        )
    }

    /** Les cases dont l'accessoire peut être à l'écran (à [margin] près) : un
     *  accessoire reste entre 350 + i×slot et 350 + i×slot + (slot − 220). */
    fun visiblePropIndices(cameraX: Double, screenWidth: Double, margin: Double = 160.0): IntRange {
        val first = floor((cameraX - margin - 350.0 - (PROP_SLOT - 220.0)) / PROP_SLOT).toInt()
        val last = floor((cameraX + screenWidth + margin - 350.0) / PROP_SLOT).toInt()
        return maxOf(0, first)..minOf(PROP_COUNT - 1, last)
    }

    /** Portage de `generateBeachDecor()` sous forme de liste (voir
     *  [propInSlot]). `count` peut être réduit pour les tests/aperçus. */
    fun decorativeProps(count: Int = PROP_COUNT): List<BeachProp> =
        (0 until count).mapNotNull { propInSlot(it) }
}
