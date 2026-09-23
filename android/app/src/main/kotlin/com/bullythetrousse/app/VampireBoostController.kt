package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bullythetrousse.core.Skin
import com.bullythetrousse.core.VampireBoost
import com.bullythetrousse.core.VampireBoostState

/**
 * Porte l'état du boost 🦇 pour un lancer et fait le pont entre le bouton
 * (qui appelle [press]/[release]) et la boucle de vol (qui appelle [step] à
 * chaque frame). Toutes les règles vivent dans [VampireBoost], `:core` ; cette
 * classe ne fait que tenir l'état observable par Compose.
 */
class VampireBoostController(private val skin: Skin) {
    var state by mutableStateOf(VampireBoostState())
        private set

    /** Vrai tant que le bouton doit rester affiché à l'écran. */
    fun isVisible(flying: Boolean): Boolean = VampireBoost.isVisible(state, skin, flying)

    fun press() { state = VampireBoost.start(state, skin) }

    fun release() { state = VampireBoost.end(state) }

    /** Consomme [dt] de budget et renvoie la vitesse horizontale accélérée. */
    fun step(dt: Double, vx: Double): Double {
        val (next, boostedVx) = VampireBoost.step(state, dt, vx)
        state = next
        return boostedVx
    }

    /** Remet le budget à neuf : le boost est utilisable une fois PAR LANCER. */
    fun reset() { state = VampireBoostState() }
}

@Composable
fun rememberVampireBoost(key: Any?, skin: Skin): VampireBoostController =
    remember(key) { VampireBoostController(skin) }
