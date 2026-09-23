package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.random.Random

/** Les étapes de la séquence spatiale, portées de l'état `state` côté web
 *  (`space_transition`, `space_floating`, `qte`). */
enum class SpacePhase {
    /** Les nuages balaient l'écran et cachent le changement de décor. */
    TRANSITION,
    /** La trousse flotte, le temps de comprendre ce qui arrive. */
    FLOATING,
    /** Les anneaux à synchroniser. */
    QTE,
    /** Terminé : la vitesse de sortie est disponible (voir [SpaceSequence.boostVelocity]). */
    DONE,
}

/**
 * État de la séquence en apesanteur. Immuable, comme les autres moteurs de
 * `:core` : chaque pas produit un nouvel état plutôt que de muter des
 * variables globales.
 *
 * [ringTargetRadius] est tiré au sort à CHAQUE anneau (voir
 * [SpaceSequence.pickTargetRadius]) : c'est ce qui empêche de mémoriser le
 * timing d'un anneau sur l'autre.
 */
data class SpaceState(
    val phase: SpacePhase = SpacePhase.TRANSITION,
    val phaseElapsed: Double = 0.0,
    /** Un booléen par anneau déjà résolu : réussi ou raté. */
    val ringResults: List<Boolean> = emptyList(),
    val ringTargetRadius: Double = SpaceSequence.RING_SIZES[1],
) {
    val hits: Int get() = ringResults.count { it }
}

/**
 * L'easter egg « vers l'espace » : un lancer chargé à fond ET visé tout au
 * début de la barre bascule en apesanteur à son apogée, où un QTE d'anneaux
 * décide de la puissance du boost final.
 *
 * Portage de `SPACE_EGG_*`/`advanceQteRing()`/`finishQte()`/
 * `pickQteTargetRadius()` côté web (index.html). Tout ce qui décide du
 * RÉSULTAT vit ici et est testé ; le décor spatial et les anneaux dessinés
 * à l'écran restent côté `:app`.
 */
object SpaceSequence {
    /** `lockedPower` doit être proche du maximum. */
    const val EGG_POWER_MIN = 0.9

    /** `value` doit être très à gauche : la "zone verte" du début de barre. */
    const val EGG_ACCURACY_MAX = -0.6

    const val TRANSITION_DURATION = 0.8
    const val FLOAT_DURATION = 1.3

    /** Durée pendant laquelle CHAQUE anneau rétrécit. */
    const val RING_DURATION = 0.9

    /** Rayon de départ de l'anneau qui rétrécit (px). */
    const val RING_START_RADIUS = 110.0

    /** Marge acceptée autour de la cible pour réussir (px). */
    const val RING_TOLERANCE = 15.0

    /** Rayons possibles de l'anneau-cible : petit, moyen, grand. */
    val RING_SIZES = listOf(30.0, 46.0, 65.0)

    const val RING_COUNT = 5

    /** Trousse Lunaire : QTE simplifié à 3 anneaux, cible toujours moyenne. */
    const val RING_COUNT_LUNAR = 3

    const val BOOST_VX = 2600.0
    const val BOOST_VY = 150.0

    /** Trousse Fusée : 50% plus rapide une fois propulsée en apesanteur. */
    const val ROCKET_SPEED_MULT = 1.5

    /**
     * `spaceEggTriggered = lockedPower >= SPACE_EGG_POWER_MIN && value <=
     * SPACE_EGG_ACCURACY_MAX && !isPlane` : le Boeing 747 ne part JAMAIS en
     * apesanteur, même en visant pile cette zone.
     */
    fun shouldTrigger(lockedPower: Double, accuracy: Double, skin: Skin): Boolean =
        lockedPower >= EGG_POWER_MIN && accuracy <= EGG_ACCURACY_MAX && !skin.isPlane

    /** `currentQteRingCount()` : 3 anneaux avec la Trousse Lunaire, 5 sinon. */
    fun ringCount(skin: Skin): Int = if (skin.isLunar) RING_COUNT_LUNAR else RING_COUNT

    /** `pickQteTargetRadius()` : taille au hasard, sauf Trousse Lunaire
     *  (toujours moyenne, c'est la simplification qu'elle offre). */
    fun pickTargetRadius(skin: Skin, random: Random = Random.Default): Double =
        if (skin.isLunar) RING_SIZES[1] else RING_SIZES[random.nextInt(RING_SIZES.size)]

    /** `currentQteRingRadius()` : décroît linéairement de [RING_START_RADIUS]
     *  à 0 sur toute la durée d'un anneau. */
    fun currentRingRadius(phaseElapsed: Double): Double {
        val t = (phaseElapsed / RING_DURATION).coerceIn(0.0, 1.0)
        return RING_START_RADIUS * (1.0 - t)
    }

    /** Un tap réussit si l'anneau qui rétrécit est assez proche de la cible. */
    fun isHit(phaseElapsed: Double, targetRadius: Double): Boolean =
        abs(currentRingRadius(phaseElapsed) - targetRadius) <= RING_TOLERANCE

    /**
     * Fait avancer la séquence de [dt] secondes. Un anneau dont le temps est
     * écoulé compte comme raté (`advanceQteRing(false)` côté web).
     */
    fun step(state: SpaceState, dt: Double, skin: Skin, random: Random = Random.Default): SpaceState {
        if (state.phase == SpacePhase.DONE) return state
        val elapsed = state.phaseElapsed + dt
        return when (state.phase) {
            SpacePhase.TRANSITION ->
                if (elapsed >= TRANSITION_DURATION) {
                    state.copy(phase = SpacePhase.FLOATING, phaseElapsed = 0.0)
                } else {
                    state.copy(phaseElapsed = elapsed)
                }

            SpacePhase.FLOATING ->
                if (elapsed >= FLOAT_DURATION) {
                    state.copy(
                        phase = SpacePhase.QTE,
                        phaseElapsed = 0.0,
                        ringResults = emptyList(),
                        ringTargetRadius = pickTargetRadius(skin, random),
                    )
                } else {
                    state.copy(phaseElapsed = elapsed)
                }

            SpacePhase.QTE ->
                if (elapsed >= RING_DURATION) {
                    resolveRing(state, hit = false, skin = skin, random = random)
                } else {
                    state.copy(phaseElapsed = elapsed)
                }

            SpacePhase.DONE -> state
        }
    }

    /** Un tap pendant le QTE : résout l'anneau en cours selon sa taille au
     *  moment du tap. Sans effet hors de la phase QTE. */
    fun tap(state: SpaceState, skin: Skin, random: Random = Random.Default): SpaceState {
        if (state.phase != SpacePhase.QTE) return state
        return resolveRing(state, isHit(state.phaseElapsed, state.ringTargetRadius), skin, random)
    }

    /** `advanceQteRing()` : enregistre l'anneau, puis passe au suivant ou
     *  termine la séquence. */
    private fun resolveRing(state: SpaceState, hit: Boolean, skin: Skin, random: Random): SpaceState {
        val results = state.ringResults + hit
        if (results.size >= ringCount(skin)) {
            return state.copy(phase = SpacePhase.DONE, phaseElapsed = 0.0, ringResults = results)
        }
        return state.copy(
            phaseElapsed = 0.0,
            ringResults = results,
            ringTargetRadius = pickTargetRadius(skin, random),
        )
    }

    /**
     * `finishQte()` : la vitesse de sortie. Même un échec complet donne un
     * petit boost (30%), pour que le joueur "aille juste moins loin" plutôt
     * que de tout perdre.
     */
    fun boostVelocity(hits: Int, skin: Skin): Pair<Double, Double> {
        val factor = 0.3 + 0.7 * (hits.toDouble() / ringCount(skin))
        val mult = if (skin.isRocket) ROCKET_SPEED_MULT else 1.0
        return (BOOST_VX * factor * mult) to (BOOST_VY * mult)
    }

    /** `save.hasPerfectQte` : les anneaux tous réussis d'affilée. */
    fun isPerfect(state: SpaceState, skin: Skin): Boolean =
        state.ringResults.size >= ringCount(skin) && state.ringResults.all { it }
}
