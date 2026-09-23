package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.bullythetrousse.core.Skin
import com.bullythetrousse.core.SpacePhase
import com.bullythetrousse.core.SpaceSequence
import com.bullythetrousse.core.SpaceState

/**
 * Le pilote de la séquence en apesanteur : il fait tourner
 * [SpaceSequence] (`:core`, testé) pendant que le vol est suspendu, encaisse
 * les taps du joueur sur les anneaux, et rend au vol la vitesse du boost.
 *
 * Séparé de l'animation de vol parce que la séquence a besoin de deux
 * choses que la boucle de vol n'a pas : des entrées du joueur en plein
 * milieu, et un état que l'écran doit pouvoir dessiner (l'anneau en cours,
 * les pastilles de résultat). Le vol, lui, ne veut qu'un résultat : la
 * vitesse de sortie.
 */
class SpaceFlight(
    private val skin: Skin,
    private val onFinished: (SpaceState) -> Unit,
) {
    /** `null` tant que la trousse n'est pas partie en apesanteur : c'est ce
     *  qui dit à l'écran s'il doit dessiner l'espace ou le monde. */
    var state by mutableStateOf<SpaceState?>(null)
        private set

    /** Un tap pendant le QTE. Sans effet dans les autres phases. */
    fun tap() {
        val current = state ?: return
        state = SpaceSequence.tap(current, skin)
    }

    /**
     * Joue toute la séquence et ne rend la main qu'une fois le boost décidé.
     * Renvoie la vitesse (vx, vy) avec laquelle le vol reprend.
     */
    suspend fun run(): Pair<Double, Double> {
        var current = SpaceState()
        state = current
        var lastFrameMillis = System.currentTimeMillis()
        while (current.phase != SpacePhase.DONE) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val dt = ((now - lastFrameMillis).coerceAtMost(50)) / 1000.0
            lastFrameMillis = now
            // Un tap a pu faire avancer l'état entre deux frames : on repart
            // de celui-là, pas de notre copie locale devenue périmée.
            current = SpaceSequence.step(state ?: current, dt, skin)
            state = current
        }
        onFinished(current)
        state = null
        return SpaceSequence.boostVelocity(current.hits, skin)
    }
}

/** Crée le pilote une fois par lancer (il porte l'état du QTE en cours). */
@Composable
fun rememberSpaceFlight(key: Any?, skin: Skin, onFinished: (SpaceState) -> Unit): SpaceFlight =
    remember(key) { SpaceFlight(skin, onFinished) }
