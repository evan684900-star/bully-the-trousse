package com.bullythetrousse.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * Placement du décor du monde "Cour d'école" (bâtiments, arbres, dessins à
 * la craie), porté depuis `drawBackground()`/`seededRand()` côté web
 * (index.html). Volontairement séparé du rendu (Canvas) : ce fichier ne
 * calcule QUE quels éléments sont visibles et où, en pixels "monde" — le
 * dessin lui-même (formes, couleurs) reste dans `:app` (pas testable dans
 * ce bac à sable sans SDK Android).
 *
 * Chaque élément a un "seed" entier stable (son index) : c'est ce qui rend
 * le décor déterministe (le même bâtiment est toujours au même endroit,
 * pareil pour les arbres/dessins) sans avoir à stocker leur position.
 */
object CourDecor {
    const val BUILDING_SPACING = 260.0
    const val CHALK_SPACING = 210.0
    const val TREE_SPACING = 300.0

    /** Porté tel quel depuis `seededRand()` côté web : un bruit pseudo-aléatoire
     *  déterministe (même seed -> même valeur), pas un vrai générateur aléatoire. */
    fun seededRand(seed: Double): Double {
        val x = sin(seed * 12.9898) * 43758.5453
        return x - floor(x)
    }

    /** `buildingStartIdx..buildingEndIdx` de `drawBackground()`, avec `parX = cameraX * 0.25`
     *  (parallaxe : les bâtiments défilent 4x plus lentement que le sol). */
    fun visibleBuildingIndices(cameraX: Double, screenWidth: Double): IntRange {
        val parX = cameraX * 0.25
        val start = floor((parX - 220.0) / BUILDING_SPACING).toInt()
        val end = ceil((parX + screenWidth + 40.0) / BUILDING_SPACING).toInt()
        return start..end
    }

    /** Position écran (x) d'un bâtiment d'index [index] : `bx = i * spacing - parX`. */
    fun buildingScreenX(index: Int, cameraX: Double): Double =
        index * BUILDING_SPACING - cameraX * 0.25

    /** `chalkStartIdx` + boucle de `drawBackground()` pour les dessins à la craie. */
    fun visibleChalkIndices(cameraX: Double, screenWidth: Double): IntRange {
        val start = floor(cameraX / CHALK_SPACING).toInt() - 1
        val count = ceil(screenWidth / CHALK_SPACING).toInt() + 2
        return start until (start + count)
    }

    /** `worldPos = i * chalkSpacing + (seededRand(i + 0.3) - 0.5) * 60`. */
    fun chalkWorldX(index: Int): Double =
        index * CHALK_SPACING + (seededRand(index + 0.3) - 0.5) * 60.0

    /** `treeStartIdx` + boucle de `drawBackground()` pour les arbres. */
    fun visibleTreeIndices(cameraX: Double, screenWidth: Double): IntRange {
        val start = floor(cameraX / TREE_SPACING).toInt() - 1
        val count = ceil(screenWidth / TREE_SPACING).toInt() + 2
        return start until (start + count)
    }

    /**
     * Easter egg 💩 : caché derrière l'arbre n°1 de la cour (dessiné juste
     * AVANT lui, donc recouvert par son feuillage opaque), visible seulement
     * avant le décollage — la caméra ne bouge pas encore, sa position à
     * l'écran est donc fixe (voir `poopEggScreenPos()` côté site).
     */
    const val POOP_EGG_TREE_INDEX = 1
    const val POOP_EGG_OFFSET_Y = -55.0
    const val POOP_EGG_SIZE = 15.0
    const val POOP_EGG_HIT_RADIUS = 16.0

    /** Position du 💩 à l'écran, par rapport à la ligne du sol. */
    fun poopEggScreenX(cameraX: Double): Double = treeWorldX(POOP_EGG_TREE_INDEX) - cameraX

    /** `hitsPoopEgg()` : le toucher tombe-t-il dans la zone du 💩 ? */
    fun hitsPoopEgg(tapX: Double, tapY: Double, cameraX: Double, groundScreenY: Double, hitRadius: Double = POOP_EGG_HIT_RADIUS): Boolean =
        kotlin.math.hypot(tapX - poopEggScreenX(cameraX), tapY - (groundScreenY + POOP_EGG_OFFSET_Y)) <= hitRadius

    /** `worldPos = i * treeSpacing + (seededRand(i + 0.7) - 0.5) * 80`. */
    fun treeWorldX(index: Int): Double =
        index * TREE_SPACING + (seededRand(index + 0.7) - 0.5) * 80.0
}
