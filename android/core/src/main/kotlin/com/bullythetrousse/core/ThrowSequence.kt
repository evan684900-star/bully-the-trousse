package com.bullythetrousse.core

/**
 * Machine à états d'un lancer, portée depuis le trio
 * startChargingPower() / lockPower() / lockAccuracyAndLaunch() côté web
 * (index.html) : 3 taps successifs (idle → charge de puissance → charge de
 * précision → lancé), chacun figeant l'oscillation en cours à l'instant du
 * tap.
 *
 * `cumulativeDistanceMeters`/`bounceCount` portent le rebond de la Trousse à
 * Baskets (voir tryBasketBounce() côté web) : un atterrissage qui rebondit
 * ne conclut pas le lancer, il relance directement une charge de puissance
 * (le joueur retape 3 fois), en gardant la distance déjà parcourue. Valent
 * toujours 0 pour un skin normal (aucune bascule dans `tap()`), donc n'ont
 * aucun effet en dehors de la Trousse à Baskets.
 *
 * L'horloge est injectable (voir [clock]) pour que les tests puissent
 * simuler un temps écoulé précis entre deux taps, sans dépendre du temps
 * réel ni de kotlinx-datetime (":core" n'a aucune dépendance externe).
 */
sealed interface ThrowState {
    data object Idle : ThrowState
    data class ChargingPower(
        val startedAtMillis: Long,
        val cumulativeDistanceMeters: Double = 0.0,
        val bounceCount: Int = 0,
    ) : ThrowState
    data class ChargingAccuracy(
        val startedAtMillis: Long,
        val lockedPower: Double,
        val cumulativeDistanceMeters: Double = 0.0,
        val bounceCount: Int = 0,
    ) : ThrowState
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
     *
     * `basketBounceChances` est la liste des probabilités de rebond
     * (BASKET_BOUNCE_CHANCES côté web, vide pour tout skin autre que la
     * Trousse à Baskets) : au 3e tap, avant de conclure le lancer, un tirage
     * décide si la trousse rebondit plutôt que d'atterrir (voir
     * tryBasketBounce()) — dans ce cas ce tap relance une charge de
     * puissance au lieu de produire un `Landed`, et la distance de ce
     * segment s'ajoute à `cumulativeDistanceMeters` plutôt que d'être perdue.
     */
    fun tap(
        puissanceLevel: Int,
        vitesseLevel: Int,
        perfectWindow: Double = PhysicsConstants.PERFECT_WINDOW,
        basketBounceChances: List<Double> = emptyList(),
        random: () -> Double = Math::random,
    ): ThrowState {
        val now = clock()
        state = when (val current = state) {
            is ThrowState.Idle ->
                ThrowState.ChargingPower(startedAtMillis = now)

            is ThrowState.ChargingPower -> {
                val elapsedSeconds = (now - current.startedAtMillis) / 1000.0
                val lockedPower = PowerAndAccuracy.powerFraction(elapsedSeconds)
                ThrowState.ChargingAccuracy(
                    startedAtMillis = now,
                    lockedPower = lockedPower,
                    cumulativeDistanceMeters = current.cumulativeDistanceMeters,
                    bounceCount = current.bounceCount,
                )
            }

            is ThrowState.ChargingAccuracy -> {
                val elapsedSeconds = (now - current.startedAtMillis) / 1000.0
                val accuracyValue = PowerAndAccuracy.accuracyValue(elapsedSeconds)
                val segment = ThrowPhysics.simulateThrow(
                    ThrowInput(
                        puissanceLevel = puissanceLevel,
                        vitesseLevel = vitesseLevel,
                        lockedPower = current.lockedPower,
                        accuracyValue = accuracyValue,
                        perfectWindow = perfectWindow,
                    )
                )
                val bounces = current.bounceCount
                val shouldBounce = bounces < basketBounceChances.size && random() < basketBounceChances[bounces]
                if (shouldBounce) {
                    ThrowState.ChargingPower(
                        startedAtMillis = now,
                        cumulativeDistanceMeters = current.cumulativeDistanceMeters + segment.distanceMeters,
                        bounceCount = bounces + 1,
                    )
                } else {
                    val totalDistance = current.cumulativeDistanceMeters + segment.distanceMeters
                    ThrowState.Landed(segment.copy(distanceMeters = totalDistance))
                }
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
