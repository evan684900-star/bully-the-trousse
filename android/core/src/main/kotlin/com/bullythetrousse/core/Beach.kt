package com.bullythetrousse.core

import kotlin.math.abs

/**
 * Évènements tirés pour CE lancer (parasol/serviette/château), portage de
 * `beachEvents`/`rollBeachEvents()` côté web.
 */
data class BeachEvents(val parasol: Boolean = false, val towel: Boolean = false, val castle: Boolean = false)

/** Ce qui se déclenche à un atterrissage donné, portage de la priorité
 *  parasol > serviette > château de `tryBeachParasolBounce()`/
 *  `beachFinalizeLanding()` côté web. */
enum class BeachLandingOutcome { NORMAL, PARASOL_BOUNCE, TOWEL_FOUND, CASTLE_CRUSHED }

/**
 * Mécaniques du monde Plage : accès (Trousse à Claquettes, billet de bus),
 * remise à 0 de l'argent à la première arrivée, et les 3 évènements de
 * lancer (parasol qui fait rebondir, serviette/château purement narratifs).
 * Portage de la section 9ter côté web (index.html).
 */
object Beach {
    const val CLAQUETTES_COST = 100000
    const val BUS_TICKET_COST = 250000

    /** En dessous de cette distance PRÉDITE (avant tout rebond), aucun
     *  évènement ne peut se déclencher — zone d'échauffement sans surprise. */
    const val EVENT_MIN_DISTANCE_METERS = 100.0

    const val PARASOL_CHANCE = 0.65
    const val TOWEL_CHANCE = 0.70
    const val CASTLE_CHANCE = 0.55

    /** `beachOnLaunch()`/`rollBeachEvents()` : rien en dessous du seuil de
     *  distance, sinon un tirage indépendant par évènement. */
    fun rollEvents(predictedDistanceMeters: Double, random: () -> Double = Math::random): BeachEvents {
        if (predictedDistanceMeters < EVENT_MIN_DISTANCE_METERS) return BeachEvents()
        return BeachEvents(
            parasol = random() < PARASOL_CHANCE,
            towel = random() < TOWEL_CHANCE,
            castle = random() < CASTLE_CHANCE,
        )
    }

    /** Quel évènement se déclenche à CET atterrissage, par ordre de priorité
     *  (le parasol continue le vol, donc passe avant les deux autres, qui
     *  concluent le lancer). Rien ne se déclenche en apesanteur. */
    fun landingOutcome(events: BeachEvents, used: BeachEvents, inSpaceMode: Boolean): BeachLandingOutcome = when {
        inSpaceMode -> BeachLandingOutcome.NORMAL
        events.parasol && !used.parasol -> BeachLandingOutcome.PARASOL_BOUNCE
        events.towel && !used.towel -> BeachLandingOutcome.TOWEL_FOUND
        events.castle && !used.castle -> BeachLandingOutcome.CASTLE_CRUSHED
        else -> BeachLandingOutcome.NORMAL
    }

    /** `worldY=1; vy=Math.max(240,Math.abs(vy)*0.62); vx=vx*0.94` — la trousse
     *  repart dans les airs au lieu de finir son lancer. `rotSpeed` (purement
     *  visuel) n'est pas porté ici, voir rotationSpeed() côté :app/rendu. */
    fun applyParasolBounce(state: FlightState): FlightState {
        val newVy = maxOf(240.0, abs(state.vy) * 0.62)
        val newVx = state.vx * 0.94
        return state.copy(worldY = 1.0, vy = newVy, vx = newVx)
    }

    /** `enterBeachProfile()` : bascule sur "plage", remet l'argent à 0 une
     *  seule fois dans la vie de la sauvegarde. Sans effet si déjà sur place. */
    fun enter(save: GameSave): GameSave {
        if (save.inPlage) return save
        val moneyReset = if (!save.hasForgottenMoney) save.copy(money = 0, hasForgottenMoney = true) else save
        return moneyReset.copy(inPlage = true, currentWorld = "plage")
    }

    /** `leaveBeachProfile()` : simple retour au monde normal. */
    fun leave(save: GameSave): GameSave {
        if (!save.inPlage) return save
        return save.copy(inPlage = false, currentWorld = "cour")
    }

    sealed interface PurchaseResult {
        data class Success(val save: GameSave) : PurchaseResult
        data object NotEnoughMoney : PurchaseResult
    }

    /** Achat de la Trousse à Claquettes : donne accès à la cinématique de
     *  déblocage (voir handlePlageCardClick() côté web), ne débloque pas le
     *  monde à elle seule. */
    fun buyClaquettes(save: GameSave): PurchaseResult {
        if (save.money < CLAQUETTES_COST) return PurchaseResult.NotEnoughMoney
        return PurchaseResult.Success(save.copy(money = save.money - CLAQUETTES_COST, hasClaquettes = true))
    }

    /** Billet de bus du retour : débite puis renvoie au monde normal. */
    fun buyBusTicket(save: GameSave): PurchaseResult {
        if (save.money < BUS_TICKET_COST) return PurchaseResult.NotEnoughMoney
        val debited = save.copy(money = save.money - BUS_TICKET_COST, hasTakenBusBack = true)
        return PurchaseResult.Success(leave(debited))
    }
}
