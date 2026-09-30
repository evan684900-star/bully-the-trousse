package com.bullythetrousse.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/** Une flaque du toit : centre et demi-largeur, en pixels "monde". */
data class Puddle(val centerX: Double, val halfWidth: Double)

/** Densité des flaques pour un évènement : une "case" tous les [segment]
 *  pixels, qui contient une flaque avec la probabilité [chance]. */
data class PuddleParams(val segment: Double, val chance: Double, val halfWidth: Double)

/** Ce que donne une relance après une glissade (`villePuddleRelaunch()`). */
data class PuddleRelaunch(
    val vx: Double,
    val vy: Double,
    val initialSpeed: Double,
    val isPerfect: Boolean,
    val rotationSpeed: Double,
)

/**
 * Le toit de la Tour, terrain de lancer du monde Ville (section 9quater
 * d'index.html) : ce qui touche directement à la boucle de lancer.
 *
 * - les flaques (fuite d'eau / pluie) : la trousse qui atterrit dedans
 *   glisse 3 s, puis repart d'une relance visée, plus vite qu'au départ ;
 * - la canicule : repos forcé de 2 minutes tous les 5 lancers.
 */
object VilleRooftop {
    /** Distance de génération des flaques : calculées à la demande, aucune
     *  liste à stocker, même jusqu'à 2 000 000 m. */
    const val PUDDLE_MAX_METERS = 2_000_000.0
    const val SLIDE_SECONDS = 3.0

    /** La barre de relance ne bloque jamais : tir automatique au bout de 6 s. */
    const val AIM_AUTO_RELAUNCH_SECONDS = 6.0

    const val HEAT_THROWS_BEFORE_REST = 5
    const val HEAT_REST_MILLIS = 2 * 60 * 1000L

    /** Pas de flaque juste au pied du lanceur (12 m). */
    private const val PUDDLE_FREE_START = 12 * PhysicsConstants.SCALE

    fun puddleParams(event: VilleEventType?): PuddleParams? = when (event) {
        VilleEventType.FUITE -> PuddleParams(segment = 70 * PhysicsConstants.SCALE, chance = 0.4, halfWidth = 55.0)
        VilleEventType.PLUIE -> PuddleParams(segment = 38 * PhysicsConstants.SCALE, chance = 0.7, halfWidth = 70.0)
        else -> null
    }

    /** `villePuddleInSeg(i, p)` : la flaque de la case [index], ou rien. La
     *  graine est retirée à chaque lancer (`villePuddleSeed`). */
    fun puddleInSegment(index: Int, params: PuddleParams, seed: Double): Puddle? {
        if (index < 0) return null
        if (index * params.segment / PhysicsConstants.SCALE > PUDDLE_MAX_METERS) return null
        if (CourDecor.seededRand(index * 7.13 + seed) > params.chance) return null
        val centerX = index * params.segment + params.segment * (0.2 + 0.6 * CourDecor.seededRand(index * 3.71 + seed))
        val halfWidth = params.halfWidth + 45 * CourDecor.seededRand(index * 1.93 + seed)
        if (centerX - halfWidth < PUDDLE_FREE_START) return null
        return Puddle(centerX, halfWidth)
    }

    fun puddlesInRange(fromX: Double, toX: Double, event: VilleEventType?, seed: Double): List<Puddle> {
        val params = puddleParams(event) ?: return emptyList()
        val first = floor(fromX / params.segment).toInt() - 1
        val last = floor(toX / params.segment).toInt() + 1
        return (first..last).mapNotNull { puddleInSegment(it, params, seed) }
    }

    fun puddleAt(x: Double, event: VilleEventType?, seed: Double): Boolean =
        puddlesInRange(x, x, event, seed).any { abs(x - it.centerX) <= it.halfWidth }

    /** `villeSlideV = Math.max(Math.abs(vx) * 1.2, 700)` au contact de la flaque. */
    fun slideStartSpeed(vx: Double): Double = maxOf(abs(vx) * 1.2, 700.0)

    /** Une image de glissade : la trousse avance et ralentit doucement. */
    fun slideStep(worldX: Double, speed: Double, dt: Double): Pair<Double, Double> =
        (worldX + speed * dt) to speed * (1 - 0.18 * dt)

    /** À plat, elle glisse : la rotation se cale sur le multiple de π le plus proche. */
    fun slideRotation(rotation: Double, dt: Double): Double {
        val flat = jsRound(rotation / PI) * PI
        return rotation + (flat - rotation) * minOf(1.0, dt * 8)
    }

    /** `villePuddleAimValue()` : la barre tremble et oscille plus vite qu'au lancer. */
    fun aimValue(secondsSinceAimStart: Double): Double = sin(secondsSinceAimStart * 4.4)

    /**
     * `villePuddleRelaunch()` : plus de vitesse qu'au départ (x1,3, au moins
     * 600), angle de 45° corrigé par la visée comme un lancer normal. Un
     * lancer parfait le reste, même si la relance ne l'est pas.
     */
    fun relaunch(
        aimValue: Double,
        lastLaunchSpeed: Double,
        perfectWindow: Double,
        wasPerfect: Boolean,
    ): PuddleRelaunch {
        val v0 = maxOf(600.0, lastLaunchSpeed * 1.3)
        val angle = (45 - aimValue * 16) * PI / 180
        return PuddleRelaunch(
            vx = v0 * cos(angle),
            vy = v0 * sin(angle),
            initialSpeed = v0,
            isPerfect = wasPerfect || abs(aimValue) < perfectWindow,
            rotationSpeed = rotationSpeed(v0),
        )
    }

    /** `villeHeatResting()` : seulement quand on lance depuis le toit. */
    fun isResting(save: GameSave, nowMillis: Long): Boolean =
        save.currentWorld == Ville.WORLD_ID && save.villeHeatRestUntil > nowMillis

    /** Secondes de repos restantes, arrondies au-dessus (`Math.ceil`). */
    fun restSecondsLeft(save: GameSave, nowMillis: Long): Long =
        maxOf(0L, (save.villeHeatRestUntil - nowMillis + 999) / 1000)

    /** "m:ss", comme le compte à rebours du texte d'aide. */
    fun formatRest(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    /**
     * `villeOnThrowCounted()` : compté à chaque atterrissage en Ville ; la
     * canicule impose une pause de 2 minutes tous les 5 lancers. Renvoie
     * aussi si CE lancer vient de déclencher la pause (pour le message).
     */
    fun onThrowCounted(save: GameSave, event: VilleEventType?, nowMillis: Long): Pair<GameSave, Boolean> {
        var updated = save.copy(villeThrows = save.villeThrows + 1)
        if (event != VilleEventType.CANICULE) return updated to false
        val heat = updated.villeHeatThrows + 1
        if (heat < HEAT_THROWS_BEFORE_REST) return updated.copy(villeHeatThrows = heat) to false
        updated = updated.copy(villeHeatThrows = 0, villeHeatRestUntil = nowMillis + HEAT_REST_MILLIS)
        return updated to true
    }

    /** `Math.round` du JS : les demis vont vers +∞ (-0.5 -> 0), pas vers le pair. */
    private fun jsRound(x: Double): Double = floor(x + 0.5).roundToLong().toDouble()
}
