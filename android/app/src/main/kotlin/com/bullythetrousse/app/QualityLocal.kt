package com.bullythetrousse.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.bullythetrousse.core.GraphicsQuality

/**
 * Le niveau de détail choisi dans les Réglages, mis à disposition de tout
 * l'arbre d'affichage.
 *
 * Pourquoi un CompositionLocal et pas un paramètre : la qualité n'intéresse
 * que les quelques endroits qui DESSINENT (canvas de jeu, cinématiques,
 * sprite), pas les écrans qu'elle devrait traverser au passage. Un paramètre
 * obligerait chaque écran intermédiaire à transporter une valeur dont il n'a
 * rien à faire.
 *
 * `staticCompositionLocalOf` plutôt que `compositionLocalOf` : la valeur ne
 * change qu'au moment où le joueur touche le réglage, donc on préfère un
 * accès sans surcoût de lecture, quitte à recomposer tout le sous-arbre ce
 * jour-là.
 */
val LocalGraphicsQuality = staticCompositionLocalOf { GraphicsQuality.DEFAULT }
