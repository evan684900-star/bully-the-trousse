package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.math.sin

/**
 * Barres de puissance/précision : des oscillations purement basées sur le
 * temps écoulé depuis le début de la charge, portées depuis lockPower() et
 * lockAccuracyAndLaunch() côté web (index.html). Le joueur tape au bon
 * moment pour "figer" la valeur courante de l'oscillation.
 */
object PowerAndAccuracy {
    /** Oscille entre 0.4 et 1.0 (jamais à vide, jamais totalement pleine
     *  indéfiniment) pendant la charge de puissance. */
    fun powerFraction(elapsedSeconds: Double): Double =
        0.4 + 0.6 * abs(sin(elapsedSeconds * 2.6))

    /** Oscille entre -1.0 et 1.0 ; 0.0 = visée parfaite (angle optimal),
     *  pendant la charge de précision. */
    fun accuracyValue(elapsedSeconds: Double): Double =
        sin(elapsedSeconds * 3.4)
}
