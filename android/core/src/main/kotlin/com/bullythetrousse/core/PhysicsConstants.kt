package com.bullythetrousse.core

/**
 * Constantes de physique du lancer, portées telles quelles depuis la
 * version web (index.html, section "1. Constantes de jeu"). Les valeurs
 * doivent rester identiques des deux côtés pour que le jeu se comporte
 * pareil sur web et sur Android.
 */
object PhysicsConstants {
    /** Pixels représentant 1 mètre à l'écran côté web ; réutilisé ici comme
     *  simple facteur d'échelle pour convertir la physique (en "pixels/s")
     *  en mètres, indépendamment de tout rendu. */
    const val SCALE = 12.0

    /** Gravité (px/s²) avec un niveau de Vitesse à 0. */
    const val BASE_G = 950.0

    /** Vitesse initiale (px/s) avec Puissance à 0 et charge à 100%. */
    const val BASE_V0 = 480.0

    /** Fenêtre de précision (valeur absolue autour de 0) pour un lancer parfait. */
    const val PERFECT_WINDOW = 0.08

    /** Trousse Claude : fenêtre du lancer parfait élargie de 75%. */
    const val PERFECT_WINDOW_CLAUDE = 0.14
}
