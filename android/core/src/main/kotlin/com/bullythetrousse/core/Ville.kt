package com.bullythetrousse.core

import kotlin.math.roundToInt

/**
 * Mécaniques "de base" du monde Ville, portées d'index.html (section
 * 9quater) et de ville.js : l'économie (tout 20 % plus cher, 20 % de gains
 * en plus), l'arrivée par l'avion et le départ par un vol, et la règle qui
 * garde le joueur "coincé" en Ville tant qu'il n'a pas repris l'avion.
 *
 * Le monde lui-même (aéroport, rue, réception, mode histoire) est porté
 * dans [VilleEngine] ; le toit-terrain de lancer dans [VilleRooftop].
 */
object Ville {
    /** `VILLE_PRICE_MULT` : "ici, tout est 20% plus cher..." */
    const val PRICE_MULT = 1.2

    /** `VILLE_EARN_MULT` : "...mais vous gagnez 20% plus d'argent". */
    const val EARN_MULT = 1.2

    const val WORLD_ID = "ville"

    /** `villePriceMult()` : ne dépend que du monde courant, comme le site. */
    fun priceMult(save: GameSave): Double = if (save.currentWorld == WORLD_ID) PRICE_MULT else 1.0

    /** `Math.round(cost * villePriceMult())` : le prix réellement affiché et débité. */
    fun price(baseCost: Int, save: GameSave): Int = (baseCost * priceMult(save)).roundToInt()

    /** `earn = Math.round(earn * VILLE_EARN_MULT)`, appliqué dans onLanded()
     *  après le bonus des niveaux et AVANT la Trousse Pièce/Vampire. */
    fun applyEarnMultiplier(earn: Int, save: GameSave): Int =
        if (save.currentWorld == WORLD_ID) (earn * EARN_MULT).roundToInt() else earn

    /**
     * `arriveInCity()` (ville.js) : fin de la cinématique d'arrivée, ou vol
     * pris depuis le menu. Débloque la Ville pour de bon, y enferme le
     * joueur et retient la date de la toute première arrivée.
     */
    fun arrive(save: GameSave, nowMillis: Long): GameSave = save.copy(
        villeUnlocked = true,
        inVille = true,
        inPlage = false,
        currentWorld = WORLD_ID,
        villeArrivedAt = if (save.villeArrivedAt == 0L) nowMillis else save.villeArrivedAt,
    )

    /** `leaveVille(dest)` : un vol au comptoir des départs de l'aéroport.
     *  Partir vers la Plage y enferme de nouveau le joueur (inPlage). */
    fun leave(save: GameSave, destination: String): GameSave = save.copy(
        inVille = false,
        currentWorld = destination,
        inPlage = destination == "plage",
    )

    /**
     * Le bloc d'initialisation d'index.html : on reste en Ville d'une
     * session à l'autre tant qu'on n'a pas repris l'avion, et une
     * sauvegarde qui pointe sur "ville" sans y être coincée retombe sur la
     * Cour.
     */
    fun normalizeOnLoad(save: GameSave): GameSave = when {
        save.inVille -> save.copy(inPlage = false, currentWorld = WORLD_ID)
        save.currentWorld == WORLD_ID -> save.copy(currentWorld = "cour")
        else -> save
    }

    /** Une destination du comptoir des départs (`openDepartures()`). */
    data class Departure(val worldId: String, val label: String)

    /** La Cour toujours, les Volcans et la Plage seulement une fois débloqués. */
    fun departures(save: GameSave): List<Departure> = buildList {
        add(Departure("cour", "🏫 Cour d'école"))
        if (save.volcanUnlocked) add(Departure("volcans", "🌋 Volcans"))
        if (save.plageUnlocked) add(Departure("plage", "🏖️ Plage"))
    }
}
