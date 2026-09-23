package com.bullythetrousse.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Entrées d'un lancer, équivalent des variables locales de
 * lockAccuracyAndLaunch() côté web (index.html).
 *
 * @param puissanceLevel Niveau de Puissance acheté + bonus du skin équipé
 *   (voir totalPuissance() côté web).
 * @param vitesseLevel Niveau de Vitesse acheté + bonus du skin équipé
 *   (voir totalVitesse() côté web).
 * @param lockedPower Puissance verrouillée par le joueur pendant la charge,
 *   de 0.0 (vide) à 1.0 (barre pleine).
 * @param accuracyValue Position verrouillée sur la barre de précision,
 *   de -1.0 à 1.0 ; 0.0 = visée parfaite (angle optimal de 45°).
 * @param perfectWindow Demi-largeur de la fenêtre "lancer parfait" autour de
 *   0.0 ; PhysicsConstants.PERFECT_WINDOW par défaut, élargie pour la
 *   Trousse Claude (voir PhysicsConstants.PERFECT_WINDOW_CLAUDE).
 */
data class ThrowInput(
    val puissanceLevel: Int,
    val vitesseLevel: Int,
    val lockedPower: Double,
    val accuracyValue: Double,
    val perfectWindow: Double = PhysicsConstants.PERFECT_WINDOW,
)

/**
 * Résultat d'un lancer : distance parcourue et quelques valeurs
 * intermédiaires utiles pour l'affichage/l'animation côté UI.
 */
data class ThrowResult(
    val distanceMeters: Double,
    val isPerfect: Boolean,
    val initialSpeed: Double,
    val angleDegrees: Double,
    val effectiveGravity: Double,
    /** La puissance et la visée figées qui ont produit ce lancer. Le site
     *  les garde après coup pour décider de l'easter egg spatial
     *  (`spaceEggTriggered`, voir [SpaceSequence.shouldTrigger]) : sans
     *  elles, impossible de savoir si CE lancer part en apesanteur. */
    val lockedPower: Double = 0.0,
    val accuracyValue: Double = 0.0,
)

/**
 * Physique du lancer, portée depuis lockAccuracyAndLaunch() côté web.
 * Un lancer atterrit toujours à la même hauteur qu'il a décollé (worldY=0
 * au départ et à l'arrivée), donc la distance suit la formule classique de
 * portée d'un tir parabolique : range = v0² × sin(2θ) / g. La version web
 * intègre la trajectoire image par image (méthode d'Euler, voir gameLoop)
 * pour gérer les rebonds/évènements en cours de vol (parasol, skid...) ;
 * cette formule fermée donne le même résultat pour un lancer "simple" (sans
 * rebond), et sert de référence pour vérifier tout futur portage de la
 * boucle image par image.
 */
object ThrowPhysics {
    fun simulateThrow(input: ThrowInput): ThrowResult {
        val v0 = PhysicsConstants.BASE_V0 * (1 + input.puissanceLevel * 0.12) * input.lockedPower
        val angleDegrees = 45.0 - input.accuracyValue * 16.0
        val angleRadians = angleDegrees * PI / 180.0
        val gravity = PhysicsConstants.BASE_G / (1 + input.vitesseLevel * 0.09)

        val vx = v0 * cos(angleRadians)
        val vy = v0 * sin(angleRadians)
        val range = (2 * vx * vy) / gravity
        val distanceMeters = range / PhysicsConstants.SCALE

        val isPerfect = kotlin.math.abs(input.accuracyValue) < input.perfectWindow

        return ThrowResult(
            lockedPower = input.lockedPower,
            accuracyValue = input.accuracyValue,
            distanceMeters = distanceMeters,
            isPerfect = isPerfect,
            initialSpeed = v0,
            angleDegrees = angleDegrees,
            effectiveGravity = gravity,
        )
    }
}
