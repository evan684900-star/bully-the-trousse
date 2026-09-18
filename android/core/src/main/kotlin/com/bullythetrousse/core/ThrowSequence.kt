package com.bullythetrousse.core

/**
 * Machine à états d'un lancer, portée depuis le trio
 * startChargingPower() / lockPower() / lockAccuracyAndLaunch() côté web
 * (index.html) : 3 taps successifs (idle → charge de puissance → charge de
 * précision → lancé), chacun figeant l'oscillation en cours à l'instant du
 * tap.
 *
 * L'horloge est injectable (voir [clock]) pour que les tests puissent
 * simuler un temps écoulé précis entre deux taps, sans dépendre du temps
 * réel ni de kotlinx-datetime (":core" n'a aucune dépendance externe).
 */
sealed interface ThrowState {
    data object Idle : ThrowState
    data class ChargingPower(val startedAtMillis: Long) : ThrowState
    data class ChargingAccuracy(val startedAtMillis: Long, val lockedPower: Double) : ThrowState
    data class Landed(val result: ThrowResult) : ThrowState
}

class ThrowSequence(private val clock: () -> Long = System::currentTimeMillis) {
    var state: ThrowState = ThrowState.Idle
        private set

    /**
     * Un tap fait avancer la machine à états d'un cran, comme handleTap()
     * côté web. "puissanceLevel"/"vitesseLevel"/"perfectWindow" ne sont
     * utiles qu'au tout dernier tap (celui qui calcule le lancer), mais
     * sont demandés à chaque appel pour rester simple à utiliser depuis
     * l'UI. `perfectWindow` élargi (voir PhysicsConstants.PERFECT_WINDOW_CLAUDE)
     * pour la Trousse Claude, comme `getSkin(save.equippedSkin).isClaude`
     * côté web (lockAccuracyAndLaunch).
     */
    fun tap(puissanceLevel: Int, vitesseLevel: Int, perfectWindow: Double = PhysicsConstants.PERFECT_WINDOW): ThrowState {
        val now = clock()
        state = when (val current = state) {
            is ThrowState.Idle ->
                ThrowState.ChargingPower(startedAtMillis = now)

            is ThrowState.ChargingPower -> {
                val elapsedSeconds = (now - current.startedAtMillis) / 1000.0
                val lockedPower = PowerAndAccuracy.powerFraction(elapsedSeconds)
                ThrowState.ChargingAccuracy(startedAtMillis = now, lockedPower = lockedPower)
            }

            is ThrowState.ChargingAccuracy -> {
                val elapsedSeconds = (now - current.startedAtMillis) / 1000.0
                val accuracyValue = PowerAndAccuracy.accuracyValue(elapsedSeconds)
                val result = ThrowPhysics.simulateThrow(
                    ThrowInput(
                        puissanceLevel = puissanceLevel,
                        vitesseLevel = vitesseLevel,
                        lockedPower = current.lockedPower,
                        accuracyValue = accuracyValue,
                        perfectWindow = perfectWindow,
                    )
                )
                ThrowState.Landed(result)
            }

            // Un nouveau tap après un atterrissage démarre un nouveau lancer,
            // comme le bouton "Relancer" côté web.
            is ThrowState.Landed ->
                ThrowState.ChargingPower(startedAtMillis = now)
        }
        return state
    }

    /** Repart de zéro sans lancer (ex: on quitte l'écran de jeu en pleine charge). */
    fun reset() {
        state = ThrowState.Idle
    }
}
