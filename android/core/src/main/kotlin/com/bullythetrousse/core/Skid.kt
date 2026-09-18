package com.bullythetrousse.core

import kotlin.math.max
import kotlin.math.min

/** Résultat d'un pas de glissade, voir [Skid.step]. */
data class SkidState(val worldX: Double, val rotation: Double, val finished: Boolean)

/**
 * Dérapage du monde Volcan : la poussière rouge rend le sol glissant, la
 * trousse continue de glisser après l'atterrissage au lieu de s'arrêter net.
 * Portage de la constante SKID_CHANCE et de startSkid()/le bloc "skidding"
 * de gameLoop() côté web.
 */
object Skid {
    const val CHANCE = 0.25
    const val MIN_METERS = 5.0
    const val MAX_METERS = 30.0
    const val DURABILITY_COST = 10

    /** `Math.random() < SKID_CHANCE`, tirage injectable pour des tests déterministes. */
    fun shouldSkid(random: () -> Double = Math::random): Boolean = random() < CHANCE

    /** `skidTargetX = worldX + (SKID_MIN_M + Math.random() * (SKID_MAX_M - SKID_MIN_M)) * SCALE`. */
    fun targetWorldX(landingWorldX: Double, random: () -> Double = Math::random): Double =
        landingWorldX + (MIN_METERS + random() * (MAX_METERS - MIN_METERS)) * PhysicsConstants.SCALE

    /**
     * Un pas de la glissade, portage exact du bloc "skidding" de gameLoop() :
     * ```
     * const remaining = skidTargetX - worldX;
     * const speed = Math.max(40, remaining * 2.4);
     * worldX = Math.min(skidTargetX, worldX + speed * dt);
     * rotation += (speed / 90) * dt * 4;
     * ...
     * if (remaining <= 1) onLanded();
     * ```
     * `finished` est calculé sur la distance restante AVANT ce pas (comme le
     * web, qui teste `remaining` avant d'avoir avancé worldX ce tour-ci).
     */
    fun step(worldX: Double, targetWorldX: Double, rotation: Double, dt: Double): SkidState {
        val remaining = targetWorldX - worldX
        val finished = remaining <= 1.0
        val speed = max(40.0, remaining * 2.4)
        val newWorldX = min(targetWorldX, worldX + speed * dt)
        val newRotation = rotation + (speed / 90.0) * dt * 4.0
        return SkidState(worldX = newWorldX, rotation = newRotation, finished = finished)
    }

    /**
     * Coût en durabilité d'un lancer qui s'est terminé par un dérapage,
     * appliqué une seule fois à l'atterrissage final (voir onLanded() côté
     * web) : `save.durability = Math.max(0, save.durability - 10)`, et
     * `hasBrokenDurability` passe à `true` la première fois qu'elle tombe à 0.
     */
    fun applyDurabilityCost(save: GameSave): GameSave {
        val newDurability = max(0, save.durability - DURABILITY_COST)
        return save.copy(
            durability = newDurability,
            hasBrokenDurability = save.hasBrokenDurability || newDurability <= 0,
        )
    }
}
