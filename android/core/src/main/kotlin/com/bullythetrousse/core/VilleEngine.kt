package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/*
 * Le moteur du monde Ville, portage de ville.js : plateformes 2D, transitions,
 * séquences scriptées, cinématique d'arrivée, escaliers et générique. Les
 * niveaux eux-mêmes (aéroport + rue, réception, pièce noire, monde bugé et
 * combat de boss) sont dans VilleLevels.kt, comme ville-levels.js côté site.
 *
 * Tout l'ÉTAT du jeu vit ici, en Kotlin pur : l'app ne fait que dessiner ce
 * qu'elle lit (VilleRenderer.kt), transmettre les appuis (VilleInput) et
 * rendre les services demandés par [VilleHost] (sons, sauvegarde, écrans).
 * C'est ce qui permet de tester le mode histoire de bout en bout dans :core,
 * sans téléphone.
 *
 * Unités : comme le site, un repère VIRTUEL (≈ 760 × 540, voir [resize]),
 * `x` vers la droite, `alt` vers le haut depuis le sol ([groundY] à l'écran).
 */

/** Taille de la trousse (px virtuels). */
const val VILLE_TS = 64.0

enum class VilleMode { PLAY, ARRIVAL, STAIRS, CREDITS }

/** La musique que l'app doit jouer (voir setLevel()/startFight() côté site). */
enum class VilleMusic { WORLD, SILENT, BOSS }

/** Les transitions de ville.js, avec l'instant (en fraction) de leur bascule. */
enum class VilleTransKind(val mid: Double) {
    IRIS(0.5), FADE(0.5), GLITCH(0.86), BLUR(0.62), IRIS_HOLD(1.0), GLITCH_QUICK(0.5),
}

class VilleTransition(
    val kind: VilleTransKind,
    val duration: Double,
    val onMid: (() -> Unit)?,
    /** Centre du diaphragme (px virtuels), null = centre de l'écran. */
    val centerX: Double? = null,
    val centerY: Double? = null,
) {
    var t = 0.0
    var midDone = false
    val progress: Double get() = (t / duration).coerceIn(0.0, 1.0)
}

/** Ce qu'une séquence scriptée attend avant de reprendre (`yield` côté site). */
sealed interface VilleWait {
    data class Seconds(val seconds: Double) : VilleWait
    class Until(val condition: () -> Boolean) : VilleWait
}

/** Les trois façons de dessiner une trousse (`drawTrousseV`). */
enum class TrousseVariant { PLAYER, INVERTED, BASE }

class VillePlayer {
    var x = 0.0
    var alt = 0.0
    var vx = 0.0
    var va = 0.0
    var dir = 1
    var ground = true
    var coyote = 0.0
    var dashT = 0.0
    var dashCd = 0.0
    var walk = 0.0
    var rot = 0.0
    var invert = false
    var sword = false
    var swing = 0.0
    var swingCd = 0.0
    var hurt = 0.0
    var hearts = 5
    var frozen = false
    var visible = true
    var scale = 1.0
    var alpha = 1.0
    var bang = 0.0
    var say = ""
    var sayT = 0.0
    /** Déplacement scripté en cours (voir walkTo()). */
    var autoWalk = false
}

/** Une autre trousse : les rencontres du mode histoire et le boss. */
class VilleNpc(
    var x: Double,
    var alt: Double,
    var dir: Int,
    val variant: TrousseVariant,
    var alpha: Double,
    var say: String = "",
    var rot: Double = 0.0,
    var size: Double = VILLE_TS,
    /** Halo lumineux (ARGB), null = aucun. */
    var glow: Long? = null,
    var flash: Double = 0.0,
    var hurt: Double = 0.0,
    var big: Boolean = false,
    var dark: Boolean = false,
    var baseAlt: Double = 0.0,
)

class VilleParticle(
    var x: Double,
    var alt: Double,
    var vx: Double,
    var va: Double,
    var life: Double,
    val maxLife: Double,
    /** 0xRRGGBB */
    val color: Int,
    var size: Double,
    val gravity: Double,
    val grow: Double,
    val square: Boolean,
)

/** Les options de `spawn(x, alt, n, o)`. */
data class ParticleSpec(
    val life: Double,
    val color: Int = 0xFFFFFF,
    val size: Double = 4.0,
    val gravity: Double = 300.0,
    val grow: Double = 0.0,
    val square: Boolean = false,
    val spreadX: Double = 10.0,
    val spreadY: Double = 10.0,
    val vx: Double = 100.0,
    val up: Double = 60.0,
)

/** Un objet avec lequel interagir (E) : invite affichée à [promptAlt] au-dessus du sol. */
class VilleInteract(
    val x: Double,
    val range: Double = 70.0,
    val promptAlt: Double = 150.0,
    val label: () -> String,
    val condition: (() -> Boolean)? = null,
    val act: () -> Unit,
)

class VillePlatform(val x: Double, val alt: Double, val width: Double)

abstract class VilleLevel(val id: String) {
    abstract val width: Double
    /** Niveau du mode histoire : pas de musique, pas de bouton retour. */
    open val story: Boolean = false
    open val interacts: List<VilleInteract> = emptyList()
    open fun enter() {}
    open fun bounds(): Pair<Double, Double> = 30.0 to width - 30.0
    open fun platforms(): List<VillePlatform> = emptyList()
    open fun update(dt: Double) {}
}

/** Contenu d'une petite fenêtre (`openPop`), dessiné par l'app. */
sealed interface VillePopupBody {
    data object None : VillePopupBody
    data class Text(val text: String) : VillePopupBody
    /** Le tableau des annonces de la réception. */
    data class Board(val events: List<VilleEvent>, val nowMillis: Long) : VillePopupBody
    /** Les commandes, au réveil dans le monde bugé. */
    data object Controls : VillePopupBody
}

class VillePopupButton(val label: String, val secondary: Boolean = false, val action: () -> Unit)

class VillePopup(
    val title: String,
    val body: VillePopupBody,
    val buttons: List<VillePopupButton>,
    val onClose: (() -> Unit)?,
)

/**
 * Les commandes : état maintenu (gauche/droite/bas/saut tenu) et impulsions
 * d'une seule image (remises à zéro après chaque [VilleEngine.frame]).
 */
class VilleInput {
    var left = false
    var right = false
    var down = false
    var holdJump = false
    var jump = false
    var dash = false
    var interact = false
    var attack = false
    var any = false
    /** Appui sur la zone de jeu elle-même (QTE, générique...). */
    var tap = false

    fun clearPulses() {
        jump = false; dash = false; interact = false; attack = false; any = false; tap = false
    }

    fun releaseAll() {
        left = false; right = false; down = false; holdJump = false
    }
}

/** Ce que le moteur demande à l'app. */
interface VilleHost {
    /** La sauvegarde la plus récente (y compris les changements de cette image). */
    val save: GameSave
    fun updateSave(transform: (GameSave) -> GameSave)
    fun nowMillis(): Long
    fun sfx(beeps: List<Beep>)
    /** Crissement de pneus du passage piéton (`sfx-tires`). */
    fun playTires()
    /** La boutique (améliorations, traînées) par-dessus la réception. */
    fun openShop()
    /** La Trousserie (skins + cosmétiques) ; [onClose] à sa fermeture. */
    fun openSkinShop(onClose: () -> Unit)
    fun goRooftop()
    fun exitToMenu()
    fun leaveVille(destination: String)
}

/** Où l'on entre dans la Ville (voir VilleWorld côté site). */
enum class VilleEntry { ARRIVAL, FLY_IN, AIRPORT, RECEPTION, ELEVATOR }

class VilleEngine(
    val host: VilleHost,
    val random: Random = Random.Default,
) {
    /* ============ ÉTAT GLOBAL (V côté site) ============ */
    var mode = VilleMode.PLAY
        private set
    var camX = 0.0
    var camLock: Double? = null
    var viewWidth = 960.0
        private set
    var viewHeight = 540.0
        private set
    /** La ligne du sol, en px virtuels depuis le haut. */
    var groundY = 445.0
        private set
    /** Facteur px réels / px virtuels. */
    var scale = 1.0
        private set
    var shake = 0.0
    var glitch = 0.0
    var near: VilleInteract? = null
        private set
    val particles = ArrayList<VilleParticle>()
    var npcs: MutableList<VilleNpc> = ArrayList()
    /** Un popup ou une boutique est ouvert : la trousse ne bouge plus. */
    var lock = false
    var allowDash = true
    var story: VilleStory? = null
    /** Horloge du monde (anime les crabes, les feux...). */
    var time = 0.0
    var dt = 0.0
        private set
    var paused = false

    val player = VillePlayer()
    val input = VilleInput()
    val levels = VilleLevels(this)
    var level: VilleLevel = levels.city
        private set

    /* ---- Ce que l'app affiche par-dessus le canvas ---- */
    var message: String? = null
        private set
    private var messageTimer = 0.0
    var popup: VillePopup? = null
        private set
    var choices: List<String>? = null
        private set
    private var choiceCallback: ((Int) -> Unit)? = null
    var transition: VilleTransition? = null
        private set
    var music = VilleMusic.WORLD
        private set
    /** Incrémenté à chaque (re)départ du combat : la musique du boss repart du début. */
    var bossMusicStarts = 0
    var footstepsOn = false
        private set
    /** Volume de la musique du boss (baissé pendant le générique). */
    var bossVolume = 0.5f
        private set
    /** Le bouton "← Menu" (masqué dans le mode histoire et le générique). */
    var showBackButton = true
        private set

    /* ---- Arrivée, escaliers, générique ---- */
    val arrival = ArrivalState()
    val stairs = StairsState()
    var credits: VilleCredits? = null
        private set

    /* ---- Compteurs mis de côté puis versés dans la sauvegarde ---- */
    private val pending = PendingStats()
    private var flushTimer = 0.0

    /* ---- Minuteries (setTimeout côté site, pour les bips décalés) ---- */
    private val timers = ArrayList<Pair<Double, () -> Unit>>()

    /* ============ ÉVÈNEMENTS DU CALENDRIER ============ */
    fun activeEvent(): VilleEvent? = VilleEvents.activeEvent(host.nowMillis())
    fun isOutage(): Boolean = activeEvent()?.type == VilleEventType.COUPURE

    /** "⚡ Coupure de courant · jusqu'à 14:20" (pastille du haut), ou null. */
    fun eventHudText(formatTime: (Long) -> String): String? {
        if (mode == VilleMode.CREDITS) return null
        val ev = activeEvent() ?: return null
        return "${ev.type.emoji} ${ev.type.label} · jusqu'à ${formatTime(ev.endMillis)}"
    }

    val pseudo: String get() = host.save.pseudo.ifEmpty { "toi" }

    /* ============ TAILLE DE L'ÉCRAN ============ */
    /** `resize()` : le repère virtuel garde ~540 px de haut et au moins ~760 de large. */
    fun resize(widthPx: Double, heightPx: Double) {
        if (widthPx <= 0 || heightPx <= 0) return
        scale = min(heightPx / 540.0, widthPx / 760.0)
        viewWidth = widthPx / scale
        viewHeight = heightPx / scale
        groundY = min(viewHeight - 95, 445 + (viewHeight - 540) * 0.5)
    }

    /* ============ MESSAGES, FENÊTRES, CHOIX ============ */
    /** [seconds] = 0 : reste affiché jusqu'au prochain message. */
    fun showMessage(text: String, seconds: Double = 4.0) {
        message = text
        messageTimer = seconds
    }

    fun hideMessage() {
        message = null
        messageTimer = 0.0
    }

    fun openPopup(
        title: String,
        body: VillePopupBody = VillePopupBody.None,
        buttons: List<VillePopupButton> = emptyList(),
        onClose: (() -> Unit)? = null,
    ) {
        popup = VillePopup(title, body, buttons, onClose)
        lock = true
    }

    fun closePopup() {
        popup = null
        lock = false
    }

    /** Bouton [index] de la fenêtre ouverte. */
    fun pressPopupButton(index: Int) {
        val button = popup?.buttons?.getOrNull(index) ?: return
        closePopup()
        button.action()
    }

    /** La croix de la fenêtre. */
    fun dismissPopup() {
        val onClose = popup?.onClose
        closePopup()
        onClose?.invoke()
    }

    fun showChoices(options: List<String>, callback: (Int) -> Unit) {
        choices = options
        choiceCallback = callback
    }

    fun pickChoice(index: Int) {
        val cb = choiceCallback ?: return
        choiceCallback = null
        choices = null
        cb(index)
    }

    /* ============ SONS ============ */
    fun beep(frequency: Double, duration: Double, waveform: Waveform, volume: Double) {
        host.sfx(listOf(Beep(frequency, duration, waveform, volume)))
    }

    fun after(seconds: Double, action: () -> Unit) {
        timers += seconds to action
    }

    fun footsteps(on: Boolean) {
        footstepsOn = on
    }

    fun playBossMusic() {
        music = VilleMusic.BOSS
    }

    /** `pauseWorldMusic()` : silence (mode histoire). */
    fun silenceMusic() {
        music = VilleMusic.SILENT
    }

    /* ============ PARTICULES ============ */
    fun spawn(x: Double, alt: Double, count: Int, spec: ParticleSpec) {
        repeat(count) {
            particles += VilleParticle(
                x = x + (random.nextDouble() - 0.5) * spec.spreadX,
                alt = alt + (random.nextDouble() - 0.5) * spec.spreadY,
                vx = (random.nextDouble() - 0.5) * spec.vx,
                va = spec.up * (0.4 + random.nextDouble()),
                life = spec.life * (0.6 + random.nextDouble() * 0.6),
                maxLife = spec.life,
                color = spec.color,
                size = spec.size,
                gravity = spec.gravity,
                grow = spec.grow,
                square = spec.square,
            )
        }
        if (particles.size > 500) particles.subList(0, particles.size - 500).clear()
    }

    private fun updateParticles(dt: Double) {
        for (p in particles) {
            p.x += p.vx * dt
            p.alt += p.va * dt
            p.va -= p.gravity * dt
            p.life -= dt
            p.size += p.grow * dt
        }
        particles.removeAll { it.life <= 0 }
    }

    /* ============ SÉQUENCES SCRIPTÉES ============
       Une séquence est un générateur : `wait(1.5)` attend 1,5 s,
       `until { cond }` attend que la condition soit vraie. Une seule à la fois. */
    private var coroutine: Iterator<VilleWait>? = null
    private var coWait = 0.0
    private var coCondition: (() -> Boolean)? = null

    fun run(script: Sequence<VilleWait>) {
        coroutine = script.iterator()
        coWait = 0.0
        coCondition = null
        stepCoroutine()
    }

    fun stopCoroutine() {
        coroutine = null
        coCondition = null
        coWait = 0.0
    }

    val busy: Boolean get() = coroutine != null

    private fun stepCoroutine() {
        var guard = 0
        while (coroutine != null && guard++ < 200) {
            if (coWait > 0) return
            coCondition?.let { if (!it()) return; coCondition = null }
            val co = coroutine ?: return
            if (!co.hasNext()) {
                if (coroutine === co) coroutine = null
                return
            }
            val next = co.next()
            // La séquence a pu en lancer une autre (run() depuis l'intérieur) :
            // c'est alors la nouvelle qui compte.
            if (coroutine !== co) continue
            when (next) {
                is VilleWait.Seconds -> coWait = next.seconds
                is VilleWait.Until -> coCondition = next.condition
            }
        }
    }

    private fun tickCoroutine(dt: Double) {
        if (coroutine == null) return
        if (coWait > 0) {
            coWait -= dt
            if (coWait > 0) return
            coWait = 0.0
        }
        stepCoroutine()
    }

    /* ============ TRANSITIONS ============ */
    fun startTransition(
        kind: VilleTransKind,
        duration: Double,
        onMid: (() -> Unit)? = null,
        centerX: Double? = null,
        centerY: Double? = null,
    ): VilleTransition {
        val trans = VilleTransition(kind, duration, onMid, centerX, centerY)
        transition = trans
        return trans
    }

    /** Ne joue que la seconde moitié (l'ouverture) d'une transition. */
    fun openingTransition(kind: VilleTransKind, duration: Double) {
        startTransition(kind, duration).apply {
            t = duration * kind.mid
            midDone = true
        }
    }

    val transitionBusy: Boolean get() = transition != null

    private fun updateTransition(dt: Double) {
        val trans = transition ?: return
        trans.t += dt
        if (!trans.midDone && trans.t >= trans.duration * trans.kind.mid) {
            trans.midDone = true
            trans.onMid?.invoke()
        }
        val current = transition
        if (current != null && current.t >= current.duration && current.kind != VilleTransKind.IRIS_HOLD) {
            transition = null
        }
    }

    /** Diaphragme centré sur la trousse (`irisTo()`). */
    fun irisTo(duration: Double = 1.0, onMid: () -> Unit) {
        val (sx, sy) = playerScreen()
        startTransition(VilleTransKind.IRIS, duration, onMid, sx, sy)
    }

    /* ============ JOUEUR ============ */
    fun resetPlayer(x: Double, dir: Int = 1) {
        player.apply {
            this.x = x; alt = 0.0; vx = 0.0; va = 0.0; this.dir = dir; ground = true; dashT = 0.0
            rot = 0.0; scale = 1.0; alpha = 1.0; visible = true; frozen = false; bang = 0.0
            say = ""; sayT = 0.0; swing = 0.0; hurt = 0.0
        }
    }

    fun platforms(): List<VillePlatform> = level.platforms()

    fun bounds(): Pair<Double, Double> = level.bounds()

    private fun updatePlayer(dt: Double) {
        val p = player
        val frozen = p.frozen || lock || transitionBusy
        val move = if (frozen) 0 else (if (input.right) 1 else 0) - (if (input.left) 1 else 0)
        if (move != 0) p.dir = move
        p.dashCd = max(0.0, p.dashCd - dt)
        if (!frozen && input.dash && p.dashCd <= 0 && allowDash) {
            p.dashT = DASH_T
            p.dashCd = DASH_CD
            pending.dashes++
            beep(520.0, 0.12, Waveform.SAWTOOTH, 0.05)
        }
        if (p.dashT > 0) {
            p.dashT -= dt
            p.vx = p.dir * DASH_V
            p.va = max(p.va, 0.0) * 0.5
            if (random.nextDouble() < 0.8) {
                spawn(p.x - p.dir * 20, p.alt + 14, 1, ParticleSpec(life = 0.3, size = 5.0, gravity = 0.0, vx = 20.0, up = 5.0))
            }
        } else if (!p.autoWalk) {
            p.vx = lerp(p.vx, move * SPEED, min(1.0, dt * 14))
        }
        p.coyote = if (p.ground) 0.1 else max(0.0, p.coyote - dt)
        if (!frozen && input.jump && p.coyote > 0) {
            p.va = JUMP
            p.ground = false
            p.coyote = 0.0
            pending.jumps++
        }
        if (p.dashT <= 0) {
            p.va -= GRAV * dt
            if (!input.holdJump && p.va > 0) p.va -= GRAV * dt * 0.9 // saut plus court si on relâche
        }
        val prevAlt = p.alt
        val prevX = p.x
        p.x += p.vx * dt
        p.alt += p.va * dt
        val (minX, maxX) = bounds()
        p.x = p.x.coerceIn(minX, maxX)
        p.ground = false
        if (p.alt <= 0) {
            p.alt = 0.0; p.va = 0.0; p.ground = true
        } else if (p.va <= 0 && !input.down) {
            for (pl in platforms()) {
                if (prevAlt >= pl.alt - 1 && p.alt <= pl.alt && abs(p.x - pl.x) <= pl.width / 2 + 8) {
                    p.alt = pl.alt; p.va = 0.0; p.ground = true
                    break
                }
            }
        }
        val moved = abs(p.x - prevX)
        pending.walked += moved
        p.walk += moved * 0.045
        footsteps(p.ground && moved > 1.2 && p.visible && p.alpha > 0.2 && mode == VilleMode.PLAY)
        p.hurt = max(0.0, p.hurt - dt)
        p.swing = max(0.0, p.swing - dt)
        p.swingCd = max(0.0, p.swingCd - dt)
        if (p.sayT > 0) {
            p.sayT -= dt
            if (p.sayT <= 0) p.say = ""
        }
    }

    /** Position écran (px virtuels) du centre de la trousse. */
    fun playerScreen(): Pair<Double, Double> =
        (player.x - camX) to (groundY - player.alt - VILLE_TS * player.scale * 0.19)

    /** Déplace la trousse toute seule jusqu'à [targetX] (séquences). */
    fun walkTo(targetX: Double, speed: Double = 210.0): Boolean {
        val d = targetX - player.x
        if (abs(d) < 5) {
            player.x = targetX; player.vx = 0.0; player.autoWalk = false
            return true
        }
        player.autoWalk = true
        player.dir = if (d > 0) 1 else -1
        player.vx = player.dir * speed
        return false
    }

    /** `hold(dur, fn)` : exécute [action] à chaque image pendant [duration]. */
    fun hold(duration: Double, action: () -> Unit): VilleWait {
        var t = 0.0
        return VilleWait.Until {
            action()
            t += dt
            t >= duration
        }
    }

    /* ============ NIVEAUX ============ */
    fun setLevel(id: String, x: Double, dir: Int = 1) {
        val lv = levels.byId(id)
        flushStats()
        level = lv
        camLock = null
        particles.clear()
        npcs = ArrayList()
        resetPlayer(x, dir)
        lv.enter()
        snapCamera()
        // Le mode histoire coupe la musique du monde, mais celle du boss
        // continue jusqu'au générique (le site ne la met en pause qu'en
        // quittant la Ville ou en revenant à un niveau normal).
        music = when {
            !lv.story -> VilleMusic.WORLD
            music == VilleMusic.BOSS -> VilleMusic.BOSS
            else -> VilleMusic.SILENT
        }
        showBackButton = !lv.story
    }

    fun snapCamera() {
        camX = cameraTarget()
    }

    private fun cameraTarget(): Double {
        camLock?.let { return it }
        val w = level.width
        if (w <= viewWidth) return (w - viewWidth) / 2
        return (player.x - viewWidth * 0.42).coerceIn(0.0, w - viewWidth)
    }

    /* ============ BOUCLE ============ */
    /**
     * Une image. [rawDt] est plafonné à 33 ms, comme la boucle du site. Les
     * impulsions de [input] sont consommées à la fin.
     */
    fun frame(rawDt: Double) {
        if (paused) return
        val dt = min(0.033, max(0.0, rawDt))
        time += dt
        this.dt = dt
        try {
            update(dt)
        } finally {
            input.clearPulses()
        }
    }

    private fun update(dt: Double) {
        updateTimers(dt)
        updateTransition(dt)
        tickCoroutine(dt)
        if (messageTimer > 0) {
            messageTimer -= dt
            if (messageTimer <= 0) hideMessage()
        }
        shake = max(if (story?.quake == true) 5.0 else 0.0, shake - dt * 30)
        glitch = max(0.0, glitch - dt * 2.5)
        when (mode) {
            VilleMode.PLAY -> {
                updatePlayer(dt)
                level.update(dt)
                // Interaction : l'objet le plus proche dont l'invite est visible.
                near = null
                if (!player.frozen && !lock && !transitionBusy && !busy) {
                    var best = Double.MAX_VALUE
                    for (it in level.interacts) {
                        val d = abs(player.x - it.x)
                        if (d <= it.range && d < best && (it.condition?.invoke() != false)) {
                            best = d
                            near = it
                        }
                    }
                    if (input.interact) near?.act?.invoke()
                }
                camX = lerp(camX, cameraTarget(), min(1.0, dt * 8))
            }
            VilleMode.ARRIVAL -> updateArrival(dt)
            VilleMode.STAIRS -> updateStairs(dt)
            VilleMode.CREDITS -> Unit // le défilement est dessiné par l'app
        }
        updateParticles(dt)
        flushTimer += dt
        if (flushTimer >= 2.0) flushStats()
    }

    private fun updateTimers(dt: Double) {
        if (timers.isEmpty()) return
        val due = ArrayList<() -> Unit>()
        val it = timers.listIterator()
        while (it.hasNext()) {
            val (left, action) = it.next()
            val remaining = left - dt
            if (remaining <= 0) {
                due += action
                it.remove()
            } else {
                it.set(remaining to action)
            }
        }
        due.forEach { it() }
    }

    /* ============ SAUVEGARDE ============ */
    private class PendingStats {
        var walked = 0.0
        var jumps = 0
        var dashes = 0
        var swordHits = 0
        var heartsLost = 0
        val isEmpty: Boolean get() = walked == 0.0 && jumps == 0 && dashes == 0 && swordHits == 0 && heartsLost == 0
    }

    fun countSwordHit() { pending.swordHits++ }
    fun countHeartLost() { pending.heartsLost++ }

    /**
     * Verse les compteurs fréquents (pas, sauts, dashs...) dans la sauvegarde.
     * Le site les incrémente directement à chaque image ; ici, écrire la
     * sauvegarde 60 fois par seconde voudrait dire 60 écritures disque.
     */
    fun flushStats() {
        flushTimer = 0.0
        if (pending.isEmpty) return
        val walked = pending.walked
        val jumps = pending.jumps
        val dashes = pending.dashes
        val swordHits = pending.swordHits
        val heartsLost = pending.heartsLost
        pending.walked = 0.0; pending.jumps = 0; pending.dashes = 0; pending.swordHits = 0; pending.heartsLost = 0
        host.updateSave {
            it.copy(
                villeWalked = it.villeWalked + walked,
                villeJumps = it.villeJumps + jumps,
                villeDashes = it.villeDashes + dashes,
                villeSwordHits = it.villeSwordHits + swordHits,
                villeHeartsLost = it.villeHeartsLost + heartsLost,
            )
        }
    }

    /* ============ CINÉMATIQUE D'ARRIVÉE ============ */
    class ArrivalState {
        var t = 0.0
        var irisStarted = false
    }

    fun startArrival() {
        mode = VilleMode.ARRIVAL
        arrival.t = 0.0
        arrival.irisStarted = false
        particles.clear()
        showBackButton = false
    }

    private fun updateArrival(dt: Double) {
        arrival.t += dt
        if (!arrival.irisStarted && arrival.t >= ARRIVAL_CLOUDS + ARRIVAL_DESCENT) {
            arrival.irisStarted = true
            startTransition(VilleTransKind.IRIS, 2.0, { arriveInCity(first = true) })
        }
    }

    /** Fin de la cinématique (ou vol depuis le menu) : la trousse se pose dans l'aéroport. */
    fun arriveInCity(first: Boolean) {
        val wasUnlocked = host.save.villeUnlocked
        host.updateSave { Ville.arrive(it, host.nowMillis()) }
        mode = VilleMode.PLAY
        setLevel("city", 520.0, 1)
        if (!wasUnlocked) {
            showMessage("✈️ Bienvenue en Ville ! ◀ ▶ pour bouger, ▲ pour sauter, ⚡ pour dasher.", 7.0)
        } else if (first) {
            showMessage("✈️ Atterrissage... mouvementé.", 3.0)
        }
    }

    /* ============ ESCALIERS (coupure de courant) : "Gère ton souffle" x7 ============ */
    class StairsState {
        var t = 0.0
        val rings = ArrayList<Boolean>()
        var ringT = 0.0
        var target = 40.0
        var destination = ""
        var pause = 0.0
        var climb = 0.0
        var done = false

        /** Le cercle blanc qui rétrécit. */
        val radius: Double get() = STAIRS_RING_START * (1 - (ringT / STAIRS_RING_DURATION).coerceIn(0.0, 1.0))
    }

    private fun pickStairsTarget(): Double = listOf(34.0, 48.0, 62.0)[random.nextInt(3)]

    fun startStairs(destination: String) {
        mode = VilleMode.STAIRS
        stairs.apply {
            t = 0.0; rings.clear(); ringT = -0.6; target = pickStairsTarget(); this.destination = destination
            pause = 0.0; climb = 0.0; done = false
        }
        host.updateSave { it.copy(villeStairs = it.villeStairs + 1) }
        footsteps(false)
        showMessage("😮‍💨 Gère ton souffle ! Appuie quand les cercles se superposent.", 4.0)
    }

    private fun updateStairs(dt: Double) {
        val st = stairs
        st.t += dt
        if (st.done) return
        st.pause = max(0.0, st.pause - dt)
        st.climb += dt * (if (st.pause > 0) 0.2 else 1.0)
        st.ringT += dt
        if (st.ringT < 0) return
        val press = input.interact || input.jump || input.tap
        if (press) {
            resolveRing(abs(st.radius - st.target) <= STAIRS_TOLERANCE)
        } else if (st.ringT >= STAIRS_RING_DURATION) {
            resolveRing(false)
        }
    }

    private fun resolveRing(hit: Boolean) {
        val st = stairs
        st.rings += hit
        if (hit) {
            host.sfx(SfxCatalog.CHARGE)
        } else {
            host.sfx(SfxCatalog.ERROR)
            st.pause = 0.6
        }
        st.ringT = -0.35
        st.target = pickStairsTarget()
        if (st.rings.size >= STAIRS_COUNT) {
            st.done = true
            hideMessage()
            val hits = st.rings.count { it }
            showMessage(if (hits == STAIRS_COUNT) "Souffle parfait ! 💨" else "Ouf... arrivée !", 2.0)
            run(sequence {
                yield(VilleWait.Seconds(0.7))
                startTransition(VilleTransKind.IRIS, 1.0, { goFloor(st.destination) })
            })
        }
    }

    /* ============ ÉTAGES / ASCENSEUR ============ */
    fun openFloorPopup(viaStairs: Boolean) {
        openPopup(
            "Quel étage ?",
            buttons = listOf(
                VillePopupButton("1. Terrain de lancer") { levels.reception.goFloor("roof", viaStairs) },
                VillePopupButton("2. Mode histoire") { levels.reception.goFloor("story", viaStairs) },
            ),
        )
    }

    fun goFloor(destination: String) {
        if (destination == "roof") {
            goRooftop()
        } else {
            story = null
            mode = VilleMode.PLAY
            setLevel("darkroom", 150.0, 1)
        }
    }

    fun goRooftop() {
        mode = VilleMode.PLAY
        footsteps(false)
        flushStats()
        host.goRooftop()
    }

    /* ============ GÉNÉRIQUE ============ */
    fun startCredits() {
        mode = VilleMode.CREDITS
        transition = null
        hideMessage()
        footsteps(false)
        flushStats()
        credits = VilleCredits.build(host.save, pseudo, story?.answer2.orEmpty())
        showBackButton = false
        bossVolume = 0.3f
    }

    /** Le joueur a touché l'écran une fois le générique arrêté. */
    fun endCredits() {
        if (mode != VilleMode.CREDITS) return
        host.updateSave {
            it.copy(
                villeStoryDone = true,
                villeStoryRuns = it.villeStoryRuns + 1,
                villeStoryDoneAt = if (it.villeStoryDoneAt == 0L) host.nowMillis() else it.villeStoryDoneAt,
            )
        }
        credits = null
        bossVolume = 0.5f
        story = null
        mode = VilleMode.PLAY
        setLevel("reception", 680.0, 1)
        openingTransition(VilleTransKind.IRIS, 1.4)
    }

    /* ============ ENTRÉES / SORTIES ============ */
    /** Point d'entrée unique de l'écran Ville. */
    fun enter(entry: VilleEntry) {
        when (entry) {
            VilleEntry.ARRIVAL -> startArrival()
            VilleEntry.FLY_IN -> flyIn()
            VilleEntry.AIRPORT -> enterHub("airport")
            VilleEntry.RECEPTION -> enterHub("reception")
            VilleEntry.ELEVATOR -> enterHub("elevator")
        }
    }

    fun enterHub(where: String) {
        stopCoroutine()
        mode = VilleMode.PLAY
        credits = null
        when (where) {
            "airport" -> setLevel("city", 520.0, 1)
            "elevator" -> {
                setLevel("reception", 680.0, 1)
                levels.reception.arriveByElevator()
            }
            else -> setLevel("reception", 190.0, 1)
        }
        openingTransition(VilleTransKind.IRIS, 1.2)
    }

    fun flyIn() {
        mode = VilleMode.PLAY
        arriveInCity(first = true)
        openingTransition(VilleTransKind.IRIS, 1.6)
    }

    fun exitToMenu() {
        stopCoroutine()
        transition = null
        story = null
        footsteps(false)
        closePopup()
        choices = null
        choiceCallback = null
        hideMessage()
        flushStats()
        host.exitToMenu()
    }

    fun leaveVille(destination: String) {
        flushStats()
        stopCoroutine()
        footsteps(false)
        host.leaveVille(destination)
    }

    /** La boutique s'ouvre par-dessus : tout s'arrête jusqu'à sa fermeture. */
    fun pauseForShop() {
        paused = true
        footsteps(false)
        input.releaseAll()
        flushStats()
    }

    fun resumeFromShop() {
        paused = false
    }

    /** `openVilleShop()` : refusée pendant une coupure de courant. */
    fun openShopFromReception(): Boolean {
        if (isOutage()) {
            showMessage("⚡ Coupure de courant : la boutique est fermée.", 3.0)
            host.sfx(SfxCatalog.ERROR)
            return false
        }
        host.openShop()
        return true
    }

    /** Le bouton "← Menu". */
    fun onBackButton() {
        if (mode == VilleMode.CREDITS) return
        exitToMenu()
    }

    /** Bouton d'attaque visible : seulement épée en main. */
    val attackAvailable: Boolean get() = player.sword

    /** Recharge du dash : 1 = prêt. */
    val dashReady: Double get() = 1 - player.dashCd / DASH_CD

    companion object {
        const val SPEED = 300.0
        const val GRAV = 2300.0
        const val JUMP = 820.0
        const val DASH_V = 950.0
        const val DASH_T = 0.17
        const val DASH_CD = 3.0

        const val ARRIVAL_CLOUDS = 2.3
        const val ARRIVAL_DESCENT = 7.2

        const val STAIRS_RING_DURATION = 1.15
        const val STAIRS_RING_START = 120.0
        const val STAIRS_COUNT = 7
        const val STAIRS_TOLERANCE = 14.0

        fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
        fun c01(v: Double): Double = v.coerceIn(0.0, 1.0)
        fun easeInOut(t: Double): Double = if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).pow(2) / 2
        fun easeOut(t: Double): Double = 1 - (1 - t).pow(3)
        fun easeIn(t: Double): Double = t * t * t

        /** `sr()` de ville-art.js : le même bruit déterministe que seededRand(). */
        fun sr(n: Double): Double = CourDecor.seededRand(n)
    }
}
