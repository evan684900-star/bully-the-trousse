package com.bullythetrousse.core

/** Phases de la cinématique de déblocage du volcan, portées depuis
 *  `cinePhase`/`CINE_TIMES` côté web (index.html). */
enum class CinePhase { FADE, APPROACH, QUAKE, ERUPTION, ASCENT, STABILIZE, ROCKS, DESCENT, LANDING, OUTRO, DEATH }

/** Sous-état d'une roche pendant la phase ROCKS, porté depuis `cineRockState`. */
enum class RockState { WAIT, WARN, INCOMING, GAP }

/** Issue finale de la cinématique : soit le monde se débloque, soit le
 *  joueur doit attendre avant de retenter (voir `cineFinish()`/`cineFail()`). */
enum class VolcanoCineOutcome { UNLOCKED, FAILED }

/**
 * État complet de la cinématique du volcan, porté depuis les variables
 * `cine*` côté web. Volontairement immuable (contrairement au web) : chaque
 * pas produit un nouvel état plutôt que de muter des `let` globaux, pour
 * rester testable sans dépendance au temps réel ni au DOM.
 *
 * Ne couvre QUE la logique de déroulement (quelle phase, qui a gagné/perdu) :
 * les effets purement visuels du web (secousse d'écran, éclair, particules
 * de poussière, défilement des nuages) restent dans `:app` au moment du
 * rendu — ce ne sont pas des informations dont dépend l'issue de la cinématique.
 */
data class VolcanoCineState(
    val phase: CinePhase = CinePhase.FADE,
    val phaseElapsed: Double = 0.0,
    val lane: Int = 0, // -1 gauche, 0 centre, 1 droite
    val lives: Int = 2,
    val rockIndex: Int = 0,
    val rockState: RockState = RockState.WAIT,
    val rockElapsed: Double = 0.0,
    val clicks: Int = 0,
    val outcome: VolcanoCineOutcome? = null, // null tant que la cinématique tourne
)

/**
 * Moteur de la cinématique de déblocage du volcan : la séquence de phases
 * chronométrées (`CINE_TIMES`), le mini-jeu d'esquive des roches (5 roches,
 * 2 vies, esquive automatique "de justesse" en cas d'échec) et le QTE de
 * clics de la descente, portés depuis `cineUpdate()`/`cineStartRock()`/
 * `cineResolveRock()`/`cineMove()`/`cineAddClick()` côté web.
 */
object VolcanoCinematic {
    const val FADE_DURATION = 2.4
    const val APPROACH_DURATION = 5.6
    const val QUAKE_DURATION = 9.0
    const val ERUPTION_DURATION = 2.0
    const val ASCENT_DURATION = 5.0
    const val STABILIZE_DURATION = 1.0
    const val DESCENT_DURATION = 5.0
    const val LANDING_DURATION = 3.2
    const val OUTRO_DURATION = 1.6
    const val DEATH_DURATION = 3.0

    const val ROCKS = 5
    const val ROCK_WAIT = 0.35
    const val ROCK_WARN = 0.6
    const val ROCK_TRAVEL = 0.45
    const val ROCK_GAP = 0.4
    const val CLICK_TARGET = 10

    const val RETRY_COOLDOWN_MILLIS = 10 * 60 * 1000L

    /** Fait avancer la cinématique de [dt] secondes. Sans effet une fois
     *  [VolcanoCineState.outcome] fixé (cinématique terminée). */
    fun step(state: VolcanoCineState, dt: Double, random: () -> Double = Math::random): VolcanoCineState {
        if (state.outcome != null) return state
        val advanced = state.copy(phaseElapsed = state.phaseElapsed + dt)
        return when (advanced.phase) {
            CinePhase.FADE -> ifElapsed(advanced, FADE_DURATION, CinePhase.APPROACH)
            CinePhase.APPROACH -> ifElapsed(advanced, APPROACH_DURATION, CinePhase.QUAKE)
            CinePhase.QUAKE -> ifElapsed(advanced, QUAKE_DURATION, CinePhase.ERUPTION)
            CinePhase.ERUPTION -> ifElapsed(advanced, ERUPTION_DURATION, CinePhase.ASCENT)
            CinePhase.ASCENT -> ifElapsed(advanced, ASCENT_DURATION, CinePhase.STABILIZE)
            CinePhase.STABILIZE -> ifElapsed(advanced, STABILIZE_DURATION, CinePhase.ROCKS)
            CinePhase.ROCKS -> updateRocks(advanced, dt, random)
            CinePhase.DESCENT -> updateDescent(advanced)
            CinePhase.LANDING -> ifElapsed(advanced, LANDING_DURATION, CinePhase.OUTRO)
            CinePhase.OUTRO -> if (advanced.phaseElapsed >= OUTRO_DURATION) advanced.copy(outcome = VolcanoCineOutcome.UNLOCKED) else advanced
            CinePhase.DEATH -> if (advanced.phaseElapsed >= DEATH_DURATION) advanced.copy(outcome = VolcanoCineOutcome.FAILED) else advanced
        }
    }

    /** Le joueur change de canal pendant la fenêtre d'esquive (WARN) :
     *  esquive volontaire, réussie quel que soit le canal choisi (comme
     *  cineMove() côté web, qui ne vérifie pas que le nouveau canal est
     *  vraiment "libre" — l'esquive marche dès qu'on bouge à temps). */
    fun move(state: VolcanoCineState, direction: Int): VolcanoCineState {
        if (state.phase != CinePhase.ROCKS || state.rockState != RockState.WARN) return state
        val next = (state.lane + direction).coerceIn(-1, 1)
        if (next == state.lane) return state
        return resolveRock(state.copy(lane = next), dodged = true, random = { 0.0 })
    }

    /** Un clic pendant le QTE de descente. Sans effet hors de cette phase
     *  ou une fois la cible atteinte. */
    fun click(state: VolcanoCineState): VolcanoCineState {
        if (state.phase != CinePhase.DESCENT || state.clicks >= CLICK_TARGET) return state
        return state.copy(clicks = state.clicks + 1)
    }

    /** `save.volcanUnlocked = true`/`save.volcanFailedUntil = now + 10min`,
     *  selon l'issue — portage de cineFinish()/cineFail() côté web. */
    fun applyOutcome(save: GameSave, outcome: VolcanoCineOutcome, nowMillis: Long): GameSave = when (outcome) {
        VolcanoCineOutcome.UNLOCKED -> save.copy(volcanUnlocked = true, currentWorld = "volcans", volcanHelpAvailable = false)
        VolcanoCineOutcome.FAILED -> save.copy(volcanFailedUntil = nowMillis + RETRY_COOLDOWN_MILLIS, volcanHelpAvailable = true)
    }

    private fun ifElapsed(state: VolcanoCineState, duration: Double, next: CinePhase): VolcanoCineState =
        if (state.phaseElapsed >= duration) nextPhase(state, next) else state

    private fun nextPhase(state: VolcanoCineState, phase: CinePhase): VolcanoCineState = state.copy(
        phase = phase,
        phaseElapsed = 0.0,
        rockIndex = if (phase == CinePhase.ROCKS) 0 else state.rockIndex,
        rockState = if (phase == CinePhase.ROCKS) RockState.WAIT else state.rockState,
        rockElapsed = if (phase == CinePhase.ROCKS) 0.0 else state.rockElapsed,
    )

    private fun updateRocks(state: VolcanoCineState, dt: Double, random: () -> Double): VolcanoCineState {
        val s = state.copy(rockElapsed = state.rockElapsed + dt)
        return when (s.rockState) {
            RockState.WAIT -> if (s.rockElapsed >= ROCK_WAIT) startRock(s) else s
            RockState.WARN -> if (s.rockElapsed >= ROCK_WARN) resolveRock(s, dodged = false, random) else s
            RockState.INCOMING ->
                if (s.rockElapsed >= ROCK_TRAVEL) s.copy(rockIndex = s.rockIndex + 1, rockState = RockState.GAP, rockElapsed = 0.0) else s
            RockState.GAP ->
                if (s.rockElapsed >= ROCK_GAP) {
                    if (s.rockIndex >= ROCKS) nextPhase(s, CinePhase.DESCENT) else startRock(s)
                } else s
        }
    }

    private fun startRock(state: VolcanoCineState): VolcanoCineState =
        state.copy(rockState = RockState.WARN, rockElapsed = 0.0)

    private fun resolveRock(state: VolcanoCineState, dodged: Boolean, random: () -> Double): VolcanoCineState {
        val incoming = state.copy(rockState = RockState.INCOMING, rockElapsed = 0.0)
        if (dodged) return incoming
        if (incoming.lives <= 0) return incoming.copy(phase = CinePhase.DEATH, phaseElapsed = 0.0)
        // esquive automatique "de justesse" : on bascule sur un autre canal au hasard
        val others = listOf(-1, 0, 1).filter { it != incoming.lane }
        val pick = (random() * others.size).toInt().coerceIn(0, others.size - 1)
        return incoming.copy(lives = incoming.lives - 1, lane = others[pick])
    }

    private fun updateDescent(state: VolcanoCineState): VolcanoCineState = when {
        state.clicks >= CLICK_TARGET -> nextPhase(state, CinePhase.LANDING)
        state.phaseElapsed >= DESCENT_DURATION -> state.copy(phase = CinePhase.DEATH, phaseElapsed = 0.0)
        else -> state
    }
}
