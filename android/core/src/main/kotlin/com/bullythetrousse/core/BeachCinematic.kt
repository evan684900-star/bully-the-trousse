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

    /**
     * `bcPhase = "dialog"` côté web : une boîte de dialogue "appuyer
     * n'importe où pour continuer", qui FIGE la cinématique (aucun timer ne
     * progresse) jusqu'à ce que le joueur ait tapé une fois par réplique en
     * attente — voir [BeachCineState.dialogQueueRemaining] et
     * [BeachCinematic.advanceDialog]. Entrée depuis PARASOL, CASTLE et
     * TOWEL, jamais depuis l'ordre linéaire des autres phases (exclue de
     * [BeachCinematic.ORDER] pour cette raison).
     */
    DIALOG,
}

/**
 * État de la cinématique de la plage. `finished` passe à `true` une fois la
 * dernière phase (OUTRO) écoulée — voir [BeachCinematic.step].
 */
data class BeachCineState(
    val phase: BeachCinePhase = BeachCinePhase.FADE,
    val phaseElapsed: Double = 0.0,
    val finished: Boolean = false,
    /** Répliques encore à passer avant de reprendre [dialogNextPhase], tant
     *  que [phase] vaut [BeachCinePhase.DIALOG]. */
    val dialogQueueRemaining: Int = 0,
    val dialogNextPhase: BeachCinePhase? = null,
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

    // DIALOG n'a pas sa place dans la progression linéaire par durée : elle
    // est entrée/quittée par les déclencheurs explicites ci-dessous
    // (DIALOG_TRIGGERS/advanceDialog), pas par indexOf+1.
    private val ORDER = BeachCinePhase.entries.filter { it != BeachCinePhase.DIALOG }
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

    /**
     * `bcShowDialogs(keys, nextPhase)` côté web : quelle phase déclenche une
     * pause dialogue à sa fin, combien de répliques elle contient (le texte
     * lui-même est une préoccupation d'affichage, portée côté `:app`,
     * comme les libellés d'[Achievement]) et où reprendre ensuite.
     */
    private val DIALOG_TRIGGERS: Map<BeachCinePhase, Pair<Int, BeachCinePhase>> = mapOf(
        BeachCinePhase.PARASOL to (2 to BeachCinePhase.FLY2), // bcineParasol1/2
        BeachCinePhase.CASTLE to (1 to BeachCinePhase.FLY3), // bcineTutoAnyway
        BeachCinePhase.TOWEL to (3 to BeachCinePhase.FLY4), // bcineTowel1/2/3
    )

    /** La barre de visée oscillante de la phase AIM (`bcAimValue =
     *  Math.sin(bcT * 3.4)` côté web), utilisée pour l'affichage ET pour
     *  valider un tir manuel (voir [tryLaunchFromAim]). */
    const val AIM_OSCILLATION_SPEED = 3.4

    /** `BCINE_AIM_FORBIDDEN` côté web (= `SPACE_EGG_ACCURACY_MAX`) : la
     *  "zone verte" du début de barre, où un tir manuel est refusé. */
    const val AIM_FORBIDDEN_MAX = -0.6

    fun aimValue(phaseElapsed: Double): Double = kotlin.math.sin(phaseElapsed * AIM_OSCILLATION_SPEED)

    fun step(state: BeachCineState, dt: Double): BeachCineState {
        if (state.finished) return state
        if (state.phase == BeachCinePhase.DIALOG) {
            // Le temps continue de s'écouler (pour une éventuelle animation
            // d'apparition côté :app), mais rien n'avance tout seul : le
            // joueur doit taper (voir advanceDialog), exactement comme
            // bcUpdate() côté web, dont le switch n'a pas de cas "dialog".
            return state.copy(phaseElapsed = state.phaseElapsed + dt)
        }

        val elapsed = state.phaseElapsed + dt
        val duration = DURATIONS.getValue(state.phase)
        if (elapsed < duration) return state.copy(phaseElapsed = elapsed)

        val trigger = DIALOG_TRIGGERS[state.phase]
        if (trigger != null) {
            val (lineCount, nextPhase) = trigger
            return state.copy(
                phase = BeachCinePhase.DIALOG,
                phaseElapsed = 0.0,
                dialogQueueRemaining = lineCount,
                dialogNextPhase = nextPhase,
            )
        }

        val nextIndex = ORDER.indexOf(state.phase) + 1
        return if (nextIndex >= ORDER.size) {
            state.copy(phaseElapsed = duration, finished = true)
        } else {
            state.copy(phase = ORDER[nextIndex], phaseElapsed = 0.0)
        }
    }

    /**
     * `bcDialogAdvance()` côté web : un tap fait passer à la réplique
     * suivante, ou reprend la cinématique (vers [BeachCineState.dialogNextPhase])
     * une fois la dernière passée. Sans effet hors de la phase DIALOG.
     */
    fun advanceDialog(state: BeachCineState): BeachCineState {
        if (state.phase != BeachCinePhase.DIALOG) return state
        val remaining = state.dialogQueueRemaining - 1
        if (remaining > 0) return state.copy(dialogQueueRemaining = remaining)
        val next = state.dialogNextPhase ?: BeachCinePhase.OUTRO
        return state.copy(phase = next, phaseElapsed = 0.0, dialogQueueRemaining = 0, dialogNextPhase = null)
    }

    /**
     * `bcLaunchFromAim(true)` côté web : un tir manuel pendant la phase AIM.
     * Refusé (barre restée à l'écran) si la barre est dans la "zone verte"
     * interdite au moment du tap ; sinon la cinématique reprend aussitôt son
     * vol, sans attendre les 5 s de la phase (déjà couvertes par [step],
     * qui tire automatiquement au bout de ce délai — CETTE fonction est
     * l'ajout d'un tir plus tôt). Sans effet hors de cette phase.
     */
    fun tryLaunchFromAim(state: BeachCineState): AimLaunchResult {
        if (state.phase != BeachCinePhase.AIM) return AimLaunchResult.Launched(state)
        if (aimValue(state.phaseElapsed) <= AIM_FORBIDDEN_MAX) return AimLaunchResult.Forbidden
        return AimLaunchResult.Launched(state.copy(phase = BeachCinePhase.FLY1, phaseElapsed = 0.0))
    }

    /** `bcFinish()` côté web : débloque le monde et y dépose le joueur (voir Beach.enter). */
    fun applyOutcome(save: GameSave): GameSave = Beach.enter(save.copy(plageUnlocked = true))
}

/** Issue d'un tir manuel pendant la phase AIM, voir [BeachCinematic.tryLaunchFromAim]. */
sealed interface AimLaunchResult {
    data class Launched(val state: BeachCineState) : AimLaunchResult
    /** Refusé : la barre était dans la zone verte interdite au moment du tap
     *  (`showToast(tr("bcineNotGreen"))` côté web — le message est une
     *  préoccupation d'affichage, portée côté `:app`). */
    data object Forbidden : AimLaunchResult
}
