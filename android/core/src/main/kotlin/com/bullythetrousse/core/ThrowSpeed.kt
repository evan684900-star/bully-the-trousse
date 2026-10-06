package com.bullythetrousse.core

/**
 * Bouton ⏩ pendant le lancer (`throwSpeed` côté site) : le nombre de pas de
 * physique joués par image pendant le vol et le dérapage. Ce sont exactement
 * les pas de la vitesse normale (même trajectoire, mêmes détections d'apogée,
 * d'avion et d'atterrissage), simplement enchaînés plus vite.
 */
object ThrowSpeed {
    val SPEEDS = listOf(1, 2, 4, 10, 100)

    /** Chaque appui passe à la vitesse suivante : ×1 → ×2 → ×4 → ×10 → ×100 → ×1. */
    fun next(current: Int): Int = SPEEDS[(SPEEDS.indexOf(current) + 1) % SPEEDS.size]

    /** Une valeur relue du stockage, ramenée à une vitesse connue. */
    fun sanitize(stored: Int): Int = if (stored in SPEEDS) stored else 1
}
