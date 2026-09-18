package com.bullythetrousse.core

/**
 * Phases de la cinématique de déblocage de la plage, portées depuis les clés
 * de `BCINE`/`bcSetPhase()` côté web (index.html), dans le même ordre.
 * Contrairement à la cinématique du volcan, celle-ci n'a pas d'échec
 * possible côté web (`bcFinish()` est toujours atteint) : pas de phase
 * "death" équivalente ici.
 */
enum class BeachCinePhase {
    FADE, HAIL, BUS_IN, DOOR_OPEN, BOARDING, SEATED, BUS_OUT, IRIS, BLACK,
    ARRIVE, BUS_LEAVE, PIVOT, COLLAPSE, FORGOT, CHEH, AFTER_CHEH, CRAB,
    BACK_AWAY, SWARM, CHASE, AIM_INTRO, AIM, FLY1, PARASOL, FLY2, CASTLE,
    FLY3, TOWEL, FLY4, OUTRO,
}

/**
 * État de la cinématique de la plage. `finished` passe à `true` une fois la
 * dernière phase (OUTRO) écoulée — voir [BeachCinematic.step].
 */
data class BeachCineState(
    val phase: BeachCinePhase = BeachCinePhase.FADE,
    val phaseElapsed: Double = 0.0,
    val finished: Boolean = false,
)

/**
 * Moteur (séquencement des phases uniquement) de la cinématique de
 * déblocage de la plage, portage des durées exactes de `BCINE` côté web.
 *
 * Simplification assumée, documentée comme telle : le web anime cette
 * cinématique avec plusieurs mini-séquences interactives (une course-
 * poursuite de crabes, une visée à ne pas poser dans la "zone verte", des
 * boîtes de dialogue à valider une à une) qui ne changent JAMAIS l'issue
 * (`bcFinish()` est toujours atteint, il n'y a pas de "death" comme pour le
 * volcan) — uniquement le rythme/l'habillage. Ce moteur porte donc la
 * VRAIE temporisation de chaque phase (les mêmes durées que `BCINE`), mais
 * pas les mini-jeux eux-mêmes : le joueur avance phase par phase, sans
 * pouvoir échouer, comme côté web au final.
 */
object BeachCinematic {
    // Durées exactes de BCINE côté web (secondes), dans le même ordre.
    const val FADE = 4.0
    const val HAIL = 2.4
    const val BUS_IN = 3.4
    const val DOOR_OPEN = 0.9
    const val BOARDING = 1.8
    const val SEATED = 1.6
    const val BUS_OUT = 2.6
    const val IRIS = 1.2
    const val BLACK = 3.0
    const val ARRIVE = 2.8
    const val BUS_LEAVE = 2.4
    const val PIVOT = 3.2
    const val COLLAPSE = 1.2
    const val FORGOT = 4.2
    const val CHEH = 0.1
    const val AFTER_CHEH = 0.7
    const val CRAB = 2.2
    const val BACK_AWAY = 2.0
    const val SWARM = 2.0
    const val CHASE = 3.0
    const val AIM_INTRO = 0.7
    const val AIM = 5.0
    const val FLY1 = 2.8
    const val PARASOL = 1.1
    const val FLY2 = 3.0
    const val CASTLE = 1.2
    const val FLY3 = 2.6
    const val TOWEL = 1.6
    const val FLY4 = 1.8
    const val OUTRO = 4.5

    private val ORDER = BeachCinePhase.entries
    private val DURATIONS: Map<BeachCinePhase, Double> = mapOf(
        BeachCinePhase.FADE to FADE, BeachCinePhase.HAIL to HAIL, BeachCinePhase.BUS_IN to BUS_IN,
        BeachCinePhase.DOOR_OPEN to DOOR_OPEN, BeachCinePhase.BOARDING to BOARDING, BeachCinePhase.SEATED to SEATED,
        BeachCinePhase.BUS_OUT to BUS_OUT, BeachCinePhase.IRIS to IRIS, BeachCinePhase.BLACK to BLACK,
        BeachCinePhase.ARRIVE to ARRIVE, BeachCinePhase.BUS_LEAVE to BUS_LEAVE, BeachCinePhase.PIVOT to PIVOT,
        BeachCinePhase.COLLAPSE to COLLAPSE, BeachCinePhase.FORGOT to FORGOT, BeachCinePhase.CHEH to CHEH,
        BeachCinePhase.AFTER_CHEH to AFTER_CHEH, BeachCinePhase.CRAB to CRAB, BeachCinePhase.BACK_AWAY to BACK_AWAY,
        BeachCinePhase.SWARM to SWARM, BeachCinePhase.CHASE to CHASE, BeachCinePhase.AIM_INTRO to AIM_INTRO,
        BeachCinePhase.AIM to AIM, BeachCinePhase.FLY1 to FLY1, BeachCinePhase.PARASOL to PARASOL,
        BeachCinePhase.FLY2 to FLY2, BeachCinePhase.CASTLE to CASTLE, BeachCinePhase.FLY3 to FLY3,
        BeachCinePhase.TOWEL to TOWEL, BeachCinePhase.FLY4 to FLY4, BeachCinePhase.OUTRO to OUTRO,
    )

    fun step(state: BeachCineState, dt: Double): BeachCineState {
        if (state.finished) return state
        val elapsed = state.phaseElapsed + dt
        val duration = DURATIONS.getValue(state.phase)
        if (elapsed < duration) return state.copy(phaseElapsed = elapsed)

        val nextIndex = ORDER.indexOf(state.phase) + 1
        return if (nextIndex >= ORDER.size) {
            state.copy(phaseElapsed = duration, finished = true)
        } else {
            state.copy(phase = ORDER[nextIndex], phaseElapsed = 0.0)
        }
    }

    /** `bcFinish()` côté web : débloque le monde et y dépose le joueur (voir Beach.enter). */
    fun applyOutcome(save: GameSave): GameSave = Beach.enter(save.copy(plageUnlocked = true))
}
