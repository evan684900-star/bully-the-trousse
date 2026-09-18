package com.bullythetrousse.core

import kotlin.math.ceil
import kotlin.math.floor

/** Une couche de volcans en parallaxe, porté depuis l'entrée `layers` de
 *  `drawVolcanoBackground()` côté web (index.html). */
data class VolcanoLayer(val spacing: Double, val parallax: Double, val width: Double, val height: Double, val offset: Double)

/**
 * Placement du décor du monde "Volcan" (deux couches de volcans en
 * parallaxe, fissures incandescentes au sol), porté depuis
 * `drawVolcanoBackground()` côté web. Comme [CourDecor], ne calcule QUE le
 * placement (en pixels "monde") ; le dessin reste dans `:app`.
 */
object VolcanoDecor {
    val LAYERS = listOf(
        VolcanoLayer(spacing = 340.0, parallax = 0.12, width = 300.0, height = 190.0, offset = 0.0),
        VolcanoLayer(spacing = 250.0, parallax = 0.3, width = 220.0, height = 130.0, offset = 120.0),
    )

    const val CRACK_SPACING = 190.0

    /** `start..end` de `layers.forEach()` pour une couche donnée. */
    fun visibleVolcanoIndices(layer: VolcanoLayer, cameraX: Double, screenWidth: Double): IntRange {
        val parX = cameraX * layer.parallax
        val start = floor((parX - layer.width) / layer.spacing).toInt()
        val end = ceil((parX + screenWidth + layer.width) / layer.spacing).toInt()
        return start..end
    }

    /** `sx = i * spacing - parX + off`. */
    fun volcanoScreenX(layer: VolcanoLayer, index: Int, cameraX: Double): Double =
        index * layer.spacing - cameraX * layer.parallax + layer.offset

    /** `seed = i * 3.7 + li * 11`, `layerIndex` = 0 (lointain) ou 1 (proche). */
    fun volcanoSeed(index: Int, layerIndex: Int): Double = index * 3.7 + layerIndex * 11.0

    /** Largeur/hauteur effectives d'un volcan donné, mêmes formules que
     *  `L.vw * (0.75 + seededRand(seed) * 0.5)` / `L.vh * (0.7 + seededRand(seed+1) * 0.6)`. */
    fun volcanoWidth(layer: VolcanoLayer, seed: Double): Double = layer.width * (0.75 + CourDecor.seededRand(seed) * 0.5)
    fun volcanoHeight(layer: VolcanoLayer, seed: Double): Double = layer.height * (0.7 + CourDecor.seededRand(seed + 1.0) * 0.6)

    /** `cs..cs+ceil(w/spacing)+2` de la boucle des fissures. */
    fun visibleCrackIndices(cameraX: Double, screenWidth: Double): IntRange {
        val start = floor(cameraX / CRACK_SPACING).toInt() - 1
        val count = ceil(screenWidth / CRACK_SPACING).toInt() + 2
        return start until (start + count)
    }

    /** `worldPos = i * crackSpacing + (seededRand(i + 0.6) - 0.5) * 70`. */
    fun crackWorldX(index: Int): Double = index * CRACK_SPACING + (CourDecor.seededRand(index + 0.6) - 0.5) * 70.0
}
