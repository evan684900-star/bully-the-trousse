package com.bullythetrousse.core

import com.bullythetrousse.core.VilleEngine.Companion.c01
import com.bullythetrousse.core.VilleEngine.Companion.easeInOut
import com.bullythetrousse.core.VilleEngine.Companion.easeOut
import com.bullythetrousse.core.VilleEngine.Companion.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Les niveaux jouables du monde Ville, portage de ville-levels.js :
 *   city      : aéroport (portique de sécurité, crabes policiers) + rue
 *               (passage piéton) + la Tour
 *   reception : réception chaleureuse (boutique, ascenseur, escaliers,
 *               tableau des annonces)
 *   darkroom  : pièce noire du mode histoire (interrupteur, portail)
 *   glitch    : monde bugé, les trois rencontres, le combat de boss et la fuite
 *
 * Seule la LOGIQUE est ici ; le dessin de chaque niveau est dans
 * VilleRenderer.kt (:app), qui lit l'état exposé par ces classes.
 */

/* ---- Mode histoire ---- */

class VilleBackgroundPortal(val x: Double, val alt: Double, var radius: Double, var t: Double = 0.0)

class VilleEscape(var voidX: Double)

/** Un cercle d'attaque du boss : 1,5 s pour sortir de la zone avant l'explosion. */
class BossCircle(val x: Double, val alt: Double, val radius: Double) {
    var t = 0.0
    /** 0 tant qu'il n'a pas explosé, puis le temps écoulé depuis l'explosion. */
    var boom = 0.0
}

class VilleFight(arenaCenter: Double) {
    var hp = VilleLevels.BOSS_HP
    var circles: MutableList<BossCircle> = ArrayList()
    var attackTimer = 1.6
    var moveTimer = 0.5
    var targetX = arenaCenter + 100
    var targetAlt = 60.0
    var streak = 0
    var streakTimer = 0.0
    /** Coup d'épée en cours de résolution (petit délai), -1 = aucun. */
    var pending = -1.0
    var over = false
    /** La réplique de mi-vie du boss, écrite lettre par lettre (null avant). */
    var tauntText: String? = null
    var tauntShown = 0
    var tauntTimer = 0.0
    var tauntDone = false
}

class VilleStory {
    var light = false
    var stage = 0.0
    var answer1 = ""
    var answer2 = ""
    var escaped = false
    var quake = false
    var fight: VilleFight? = null
    var boss: VilleNpc? = null
    var buildRise = 0.0
    var backgroundPortal: VilleBackgroundPortal? = null
    var escape: VilleEscape? = null
    /** L'épée abandonnée sur le sol après la victoire (x monde). */
    var droppedSwordX: Double? = null
    /** Demi-largeur de l'arène du combat (AW côté site). */
    var arenaHalfWidth = 480.0
    var introDone = false
}

/* ---- Rue : passage piéton ---- */

enum class PedestrianLight { GREEN, BLINK, RED }

data class CrosswalkLight(val pedestrian: PedestrianLight, val secondsLeft: Double)

class CityCar(
    val lane: Int,
    var u: Double,
    val speed: Double,
    val color: Int,
    val killer: Boolean = false,
    /** Seulement pour la voiture qui percute : l'endroit (x monde) de l'impact. */
    val x: Double = 0.0,
)

class CityState {
    var securityFlash = 0.0
    var lastX = 0.0
    /** Ouverture des portes coulissantes de sortie (0 → 1). */
    var door = 0.0
    val cars = ArrayList<CityCar>()
    var carTimer = 0.0
    var dying = false
    var killer: CityCar? = null
    /** "BIP ✅" au-dessus du portique. */
    var bip = 0.0
}

class VilleLevels(private val e: VilleEngine) {
    private val p get() = e.player

    val city = CityLevel()
    val reception = ReceptionLevel()
    val darkroom = DarkroomLevel()
    val glitch = GlitchLevel()

    fun byId(id: String): VilleLevel = when (id) {
        "city" -> city
        "reception" -> reception
        "darkroom" -> darkroom
        "glitch" -> glitch
        else -> error("Niveau inconnu : $id")
    }

    private fun wait(seconds: Double) = VilleWait.Seconds(seconds)
    private fun until(condition: () -> Boolean) = VilleWait.Until(condition)

    private fun glitchSfx() {
        for (i in 0 until 5) {
            e.after(i * 0.06) { e.beep(80 + e.random.nextDouble() * 900, 0.07, Waveform.SQUARE, 0.05) }
        }
    }

    private fun violentGlitch(atPeak: () -> Unit): Sequence<VilleWait> = sequence {
        glitchSfx()
        yield(e.hold(0.45) { e.glitch = max(e.glitch, 1.5); e.shake = max(e.shake, 16.0) })
        atPeak()
        glitchSfx()
        yield(e.hold(0.5) { e.glitch = max(e.glitch, 1.7); e.shake = max(e.shake, 20.0) })
    }

    /* =====================================================================
       NIVEAU 1 : AÉROPORT + RUE
       ===================================================================== */
    inner class CityLevel : VilleLevel("city") {
        override val width = CITY_W
        val state = CityState()

        override fun enter() {
            state.cars.clear()
            state.dying = false
            state.killer = null
            state.lastX = p.x
            state.door = 0.0
        }

        override fun bounds() = 40.0 to CITY_W - 40

        override val interacts = listOf(
            VilleInteract(DEPARTURES_X, range = 95.0, promptAlt = 170.0, label = { "Prendre un vol" }) { openDepartures() },
            VilleInteract(SKINSHOP_X, range = 80.0, promptAlt = 200.0, label = { "Entrer à La Trousserie" }) {
                if (e.isOutage()) {
                    e.showMessage("⚡ Coupure de courant : La Trousserie est fermée.", 3.0)
                    e.host.sfx(SfxCatalog.ERROR)
                } else {
                    e.lock = true
                    e.host.openSkinShop { e.lock = false }
                }
            },
            VilleInteract(TOWER_DOOR, range = 85.0, promptAlt = 190.0, label = { "Appuie sur E pour entrer" }) {
                p.frozen = true
                e.irisTo(1.0) { e.setLevel("reception", 190.0, 1) }
            },
        )

        override fun update(dt: Double) {
            val s = state
            // Portique de sécurité : bip + voyant vert quand on passe dessous.
            if ((s.lastX < SECURITY_X) != (p.x < SECURITY_X)) {
                s.securityFlash = 1.2
                s.bip = 1.0
                e.beep(1320.0, 0.12, Waveform.SINE, 0.08)
                e.after(0.13) { e.beep(1760.0, 0.14, Waveform.SINE, 0.08) }
            }
            s.lastX = p.x
            s.securityFlash = max(0.0, s.securityFlash - dt)
            s.bip = max(0.0, s.bip - dt * 0.8)
            s.door = lerp(s.door, if (abs(p.x - (AIRPORT_END - 40)) < 190) 1.0 else 0.0, min(1.0, dt * 5))
            // Circulation perpendiculaire au passage piéton.
            val light = lightState(e.time)
            s.carTimer -= dt
            if (light.pedestrian == PedestrianLight.RED && light.secondsLeft > 1.2 && light.secondsLeft < 7.4 && s.carTimer <= 0) {
                val lane = if (e.random.nextDouble() < 0.5) -1 else 1
                s.cars += CityCar(
                    lane = lane,
                    u = if (lane < 0) 0.0 else 1.2,
                    speed = 0.8 + e.random.nextDouble() * 0.3,
                    color = CAR_COLORS[e.random.nextInt(CAR_COLORS.size)],
                )
                s.carTimer = 0.9 + e.random.nextDouble() * 1.1
            }
            s.cars.forEach { it.u += (if (it.lane < 0) 1 else -1) * it.speed * dt }
            s.cars.removeAll { it.u <= -0.05 || it.u >= 1.25 }
            s.killer?.let { it.u += 2.6 * dt }
            // Le joueur s'engage alors que le bonhomme est rouge : percuté.
            if (!s.dying && light.pedestrian == PedestrianLight.RED &&
                p.x > CROSS_A + 25 && p.x < CROSS_B - 25 && !e.transitionBusy
            ) {
                s.dying = true
                e.run(crosswalkDeath())
            }
        }

        private fun crosswalkDeath(): Sequence<VilleWait> = sequence {
            val s = state
            p.frozen = true
            e.host.playTires()
            s.killer = CityCar(lane = -1, u = 0.45, speed = 0.0, color = 0xC0392B, killer = true, x = p.x)
            yield(until { (s.killer?.u ?: 1.0) >= 0.92 })
            e.shake = 24.0
            e.host.sfx(SfxCatalog.CRASH)
            p.va = 520.0
            p.vx = 0.0
            p.rot = 0.8
            e.host.updateSave { it.copy(villeCrosswalkDeaths = it.villeCrosswalkDeaths + 1) }
            e.startTransition(VilleTransKind.BLUR, 1.8, {
                s.killer = null
                s.cars.clear()
                e.resetPlayer(RESPAWN_X, 1)
                e.snapCamera()
                e.time += 16 - e.time % 16 // on repart au vert
            })
            yield(until { !e.transitionBusy })
            s.dying = false
            p.frozen = false
            e.showMessage("🚦 Attends que le bonhomme passe au vert...", 3.0)
        }

        private fun openDepartures() {
            e.openPopup(
                "✈️ Départs",
                VillePopupBody.Text("Où veux-tu t'envoler ? Tu pourras revenir en Ville depuis la carte « Ville » du menu."),
                Ville.departures(e.host.save).map { dest ->
                    VillePopupButton(dest.label) {
                        p.frozen = true
                        e.irisTo(1.0) { e.leaveVille(dest.worldId) }
                    }
                },
            )
        }
    }

    /* =====================================================================
       NIVEAU 2 : RÉCEPTION
       ===================================================================== */
    inner class ReceptionLevel : VilleLevel("reception") {
        override val width = RECEPTION_W
        /** Ouverture des portes de l'ascenseur (0 → 1). */
        var elevator = 0.0

        override fun enter() {
            elevator = 0.0
        }

        override fun bounds() = 60.0 to RECEPTION_W - 50

        fun arriveByElevator() {
            e.run(sequence {
                p.frozen = true
                p.alpha = 0.0
                elevator = 1.0
                yield(wait(0.35))
                yield(until { p.alpha = min(1.0, p.alpha + e.dt * 3); p.alpha >= 1 })
                yield(until { e.walkTo(R_ELEVATOR + 90, 180.0) })
                yield(until { elevator = max(0.0, elevator - e.dt * 2.2); elevator <= 0 })
                p.frozen = false
            })
        }

        fun goFloor(destination: String, viaStairs: Boolean) {
            e.run(if (viaStairs) stairsSequence(destination) else elevatorSequence(destination))
        }

        private fun elevatorSequence(destination: String): Sequence<VilleWait> = sequence {
            p.frozen = true
            e.host.updateSave { it.copy(villeElevator = it.villeElevator + 1) }
            yield(until { e.walkTo(R_ELEVATOR) })
            yield(until { elevator = min(1.0, elevator + e.dt * 2.2); elevator >= 1 })
            yield(until { p.alpha = max(0.0, p.alpha - e.dt * 3); p.alpha <= 0 })
            yield(until { elevator = max(0.0, elevator - e.dt * 2.2); elevator <= 0 })
            yield(wait(0.25))
            e.startTransition(VilleTransKind.IRIS, 1.0, { e.goFloor(destination) })
        }

        private fun stairsSequence(destination: String): Sequence<VilleWait> = sequence {
            p.frozen = true
            yield(until { e.walkTo(R_STAIRS) })
            yield(until { p.alpha = max(0.0, p.alpha - e.dt * 3); p.alpha <= 0 })
            e.startTransition(VilleTransKind.IRIS, 0.8, { e.startStairs(destination) })
        }

        override val interacts = listOf(
            VilleInteract(R_EXIT, range = 70.0, promptAlt = 200.0, label = { "Sortir" }) {
                p.frozen = true
                e.irisTo(1.0) { e.setLevel("city", TOWER_DOOR - 10, -1) }
            },
            VilleInteract(R_STAIRS, range = 60.0, promptAlt = 210.0, label = { "Prendre les escaliers" }, condition = { e.isOutage() }) {
                e.openFloorPopup(viaStairs = true)
            },
            // N'apparaît qu'après être passé à la réception, et seulement une fois arrêté devant.
            VilleInteract(
                R_ELEVATOR, range = 55.0, promptAlt = 230.0,
                label = { if (e.isOutage()) "⚡ Hors service" else "Appeler l'ascenseur" },
                condition = { e.host.save.villeReceptionDone && abs(p.vx) < 25 },
            ) {
                if (e.isOutage()) {
                    e.showMessage("⚡ Coupure de courant : l'ascenseur ne marche pas. Prends les escaliers !", 3.0)
                    e.host.sfx(SfxCatalog.ERROR)
                } else {
                    e.openFloorPopup(viaStairs = false)
                }
            },
            VilleInteract(R_BOARD, range = 70.0, promptAlt = 250.0, label = { "Lire les annonces" }) { openBoard() },
            VilleInteract(R_DESK, range = 110.0, promptAlt = 60.0, label = { "Appuie sur E" }) {
                if (!e.host.save.villeReceptionDone) e.host.updateSave { it.copy(villeReceptionDone = true) }
                if (e.openShopFromReception()) e.pauseForShop()
            },
        )

        private fun openBoard() {
            val now = e.host.nowMillis()
            e.openPopup("📌 Tableau des annonces", VillePopupBody.Board(VilleEvents.upcomingEvents(now), now))
        }
    }

    /* =====================================================================
       NIVEAU 3 : PIÈCE NOIRE (mode histoire)
       ===================================================================== */
    inner class DarkroomLevel : VilleLevel("darkroom") {
        override val width = DARKROOM_W
        override val story = true

        private val st get() = e.story!!

        override fun enter() {
            if (e.story == null) e.story = VilleStory()
            e.allowDash = true
            if (!st.light) e.showMessage("Il fait tout noir... Trouve l'interrupteur.", 5.0)
        }

        override fun bounds() = 50.0 to DARKROOM_W - 50

        override val interacts = listOf(
            VilleInteract(D_DOOR, range = 70.0, promptAlt = 210.0, label = { "Sortir" }) {
                p.frozen = true
                if (st.escaped) {
                    // L'écran reste noir : générique.
                    val (sx, sy) = e.playerScreen()
                    e.startTransition(VilleTransKind.IRIS_HOLD, 1.4, { e.startCredits() }, sx, sy)
                } else {
                    e.irisTo(1.0) {
                        e.setLevel("reception", R_ELEVATOR, 1)
                        reception.arriveByElevator()
                    }
                }
            },
            VilleInteract(D_SWITCH, range = 60.0, promptAlt = 170.0, label = { "Interrupteur" }, condition = { !st.light }) {
                st.light = true
                e.hideMessage()
                e.beep(1200.0, 0.04, Waveform.SQUARE, 0.08)
                e.after(0.08) { e.beep(60.0, 0.5, Waveform.SAWTOOTH, 0.05) }
            },
            VilleInteract(
                D_PORTAL, range = 70.0, promptAlt = 330.0,
                label = { if (st.escaped) "Examiner le portail" else "Entrer dans le portail" },
                condition = { st.light },
            ) {
                if (st.escaped) {
                    e.showMessage("ça a l'air plutôt inutile maintenant...", 3.5)
                } else {
                    e.run(enterPortal())
                }
            },
        )

        override fun update(dt: Double) {
            if (st.escaped && e.random.nextDouble() < dt * 14) {
                val a = e.random.nextDouble() * PI * 2
                val smoke = e.random.nextDouble() < 0.6
                e.spawn(
                    D_PORTAL + cos(a) * D_RADIUS, D_PORTAL_ALT + sin(a) * D_RADIUS, 1,
                    if (smoke) {
                        ParticleSpec(life = 1.6, color = 0x6E6E73, size = 6.0, grow = 10.0, gravity = -40.0, up = 30.0, vx = 30.0)
                    } else {
                        ParticleSpec(life = 0.35, color = 0xFFD25A, size = 2.0, gravity = 500.0, up = 160.0, vx = 260.0)
                    },
                )
            }
        }

        private fun enterPortal(): Sequence<VilleWait> = sequence {
            p.frozen = true
            yield(until { e.walkTo(D_PORTAL) })
            var k = 0.0
            yield(until {
                k += e.dt / 0.8
                p.alt = lerp(0.0, D_PORTAL_ALT - 20, easeInOut(c01(k)))
                p.rot += e.dt * 8 * k
                p.scale = 1 - 0.8 * c01(k)
                k >= 1
            })
            p.visible = false
            e.startTransition(VilleTransKind.GLITCH, 3.0, {
                e.setLevel("glitch", 380.0, 1)
                glitch.arrive()
            })
        }
    }

    /* =====================================================================
       NIVEAU 4 : MONDE BUGÉ + BOSS
       ===================================================================== */
    inner class GlitchLevel : VilleLevel("glitch") {
        override val width = GLITCH_W
        override val story = true

        private val st get() = e.story!!

        override fun enter() {
            p.invert = st.stage >= 2 && st.stage < 3
            e.npcs = ArrayList()
        }

        fun arrive() {
            if (!st.introDone) {
                e.run(intro())
                return
            }
            e.resetPlayer(300.0, 1)
            e.snapCamera()
            p.invert = st.stage >= 2 && st.stage < 3
        }

        private val arenaLocked: Boolean get() = st.fight != null || (st.stage >= 3.5 && st.stage < 5)

        override fun bounds(): Pair<Double, Double> =
            if (arenaLocked) (ARENA_X - st.arenaHalfWidth + 46) to (ARENA_X + st.arenaHalfWidth - 46) else 60.0 to GLITCH_W - 60

        override fun platforms(): List<VillePlatform> {
            if (!(st.buildRise > 0.5 && st.stage < 5)) return emptyList()
            val aw = st.arenaHalfWidth
            return listOf(
                VillePlatform(ARENA_X, 110.0, 190.0),
                VillePlatform(ARENA_X - aw * 0.55, 220.0, 170.0),
                VillePlatform(ARENA_X + aw * 0.55, 220.0, 170.0),
                VillePlatform(ARENA_X, 330.0, 170.0),
            )
        }

        override fun update(dt: Double) {
            if (e.random.nextDouble() < dt * 0.3) e.glitch = max(e.glitch, 0.22)
            if (!e.busy && !e.transitionBusy) {
                when {
                    st.stage == 1.0 && p.x > E1 - 240 -> e.run(encounter1())
                    st.stage == 2.0 && p.x > E2 - 240 -> e.run(encounter2())
                    st.stage == 3.0 && p.x > ARENA_X - min(e.viewWidth, 1040.0) / 2 * 0.55 -> e.run(encounter3())
                    st.stage < 3.5 && st.stage >= 1 && p.x < RETURN_X + 60 -> {
                        p.frozen = true
                        e.startTransition(VilleTransKind.GLITCH_QUICK, 1.2, { e.setLevel("darkroom", D_PORTAL + 110, 1) })
                    }
                }
            }
            if (st.fight != null) updateFight(dt)
            if (st.escape != null) updateEscape(dt)
            st.backgroundPortal?.let { it.t += dt }
            e.camLock = if (arenaLocked) ARENA_X - e.viewWidth / 2 else null
        }

        private fun intro(): Sequence<VilleWait> = sequence {
            p.frozen = true
            p.rot = PI // allongée sur le dos
            yield(wait(1.3))
            p.bang = 1.0
            e.beep(880.0, 0.08, Waveform.SQUARE, 0.08)
            yield(wait(0.8))
            p.va = 480.0
            var k = 0.0
            yield(until { k += e.dt / 0.5; p.rot = lerp(PI, 0.0, easeOut(c01(k))); k >= 1 })
            p.rot = 0.0
            yield(until { p.bang = max(0.0, p.bang - e.dt * 2); p.bang <= 0 })
            var closed = false
            e.openPopup(
                "🧭 Commandes",
                VillePopupBody.Controls,
                listOf(VillePopupButton("C'est parti") { closed = true }),
                onClose = { closed = true },
            )
            yield(until { closed })
            st.introDone = true
            st.stage = 1.0
            p.frozen = false
        }

        private fun fadeNpc(npc: VilleNpc, to: Double, speed: Double = 3.0): Sequence<VilleWait> = sequence {
            yield(until {
                npc.alpha = if (to > npc.alpha) min(to, npc.alpha + e.dt * speed) else max(to, npc.alpha - e.dt * speed)
                npc.alpha == to
            })
        }

        /** Choix proposé au joueur, dont la réponse est rendue par [onPick]. */
        private fun choose(options: List<String>, onPick: (String) -> Unit): Sequence<VilleWait> = sequence {
            var pick = -1
            e.showChoices(options) { pick = it }
            yield(until { pick >= 0 })
            onPick(options[pick])
        }

        private fun encounter1(): Sequence<VilleWait> = sequence {
            st.stage = 1.5
            p.frozen = true
            p.vx = 0.0
            val n = VilleNpc(x = E1, alt = 0.0, dir = -1, variant = TrousseVariant.INVERTED, alpha = 0.0)
            e.npcs = mutableListOf(n)
            e.glitch = 0.7
            yieldAll(fadeNpc(n, 1.0))
            yield(wait(0.6))
            yieldAll(choose(FIRST_QUESTIONS) { st.answer1 = it; p.say = it; p.sayT = 2.0 })
            yield(wait(2.1))
            n.say = "..."
            yield(wait(1.8))
            n.say = ""
            yieldAll(choose(SECOND_QUESTIONS) { st.answer2 = it; p.say = it; p.sayT = 2.0 })
            yield(wait(2.1))
            n.say = "..."
            yield(wait(1.6))
            n.say = ""
            yieldAll(violentGlitch { p.invert = true; e.npcs = ArrayList() })
            st.stage = 2.0
            p.frozen = false
        }

        private fun encounter2(): Sequence<VilleWait> = sequence {
            st.stage = 2.5
            p.frozen = true
            p.vx = 0.0
            val n = VilleNpc(x = E2, alt = 0.0, dir = -1, variant = TrousseVariant.BASE, alpha = 0.0)
            e.npcs = mutableListOf(n)
            e.glitch = 0.7
            yieldAll(fadeNpc(n, 1.0))
            yield(wait(0.5))
            // Mot pour mot ce que le joueur lui a dit : ses deux réponses, dans l'ordre.
            n.say = st.answer1
            yield(wait(2.6))
            n.say = ""
            yield(wait(0.4))
            n.say = st.answer2
            yield(wait(2.6))
            n.say = ""
            yield(wait(1.1)) // le joueur ne répond rien
            yieldAll(violentGlitch { p.invert = false; e.npcs = ArrayList() })
            st.stage = 3.0
            p.frozen = false
        }

        private fun encounter3(): Sequence<VilleWait> = sequence {
            st.stage = 3.5
            st.arenaHalfWidth = min(e.viewWidth, 1040.0) / 2
            p.frozen = true
            p.vx = 0.0
            val n = VilleNpc(x = ARENA_X + 70, alt = 0.0, dir = -1, variant = TrousseVariant.PLAYER, alpha = 0.0)
            e.npcs = mutableListOf(n)
            yieldAll(fadeNpc(n, 1.0))
            // Troisième rencontre : pas de choix de dialogue, le joueur ne dit
            // rien ; l'autre lâche seulement « Et maintenant qui est qui ? ».
            yield(wait(0.8))
            n.say = "Et maintenant qui est qui ?"
            yield(wait(2.8))
            n.say = ""
            // Un portail grandit en arrière-plan et l'aspire doucement.
            val portal = VilleBackgroundPortal(x = ARENA_X + 70, alt = 170.0, radius = 0.0)
            st.backgroundPortal = portal
            e.beep(90.0, 1.2, Waveform.SINE, 0.08)
            yield(e.hold(1.2) { portal.radius = lerp(portal.radius, 140.0, min(1.0, e.dt * 3)) })
            var k = 0.0
            yield(until {
                k += e.dt / 2.4
                val eased = easeInOut(c01(k))
                n.alt = lerp(0.0, portal.alt - 10, eased)
                n.rot += e.dt * (2 + k * 10)
                n.size = VILLE_TS * (1 - 0.9 * eased)
                if (e.random.nextDouble() < 0.5) {
                    e.spawn(n.x, n.alt + 12, 1, ParticleSpec(life = 0.6, color = 0x78FFA0, size = 3.0, gravity = 0.0, up = 40.0, vx = 80.0))
                }
                k >= 1
            })
            e.npcs = ArrayList()
            yield(wait(2.0)) // disparue
            // Elle ressort, trois fois plus grande, un building de chaque côté.
            e.glitch = 1.3
            e.shake = 18.0
            glitchSfx()
            val boss = VilleNpc(
                x = ARENA_X + 70, alt = 30.0, baseAlt = 30.0, dir = -1, variant = TrousseVariant.PLAYER, alpha = 1.0,
                size = VILLE_TS * 3, glow = BOSS_GLOW, big = true, dark = true,
            )
            st.boss = boss
            e.npcs = mutableListOf(boss)
            var g = 0.0
            yield(until {
                g += e.dt / 0.9
                boss.size = VILLE_TS * 3 * easeOut(c01(g))
                st.buildRise = easeOut(c01(g * 1.1))
                st.backgroundPortal?.let { it.radius *= 1 - e.dt * 2 }
                g >= 1
            })
            st.backgroundPortal = null
            yield(wait(0.4))
            val text = "ON VA VOIR ÇA"
            for (i in 1..text.length) {
                boss.say = text.substring(0, i)
                if (text[i - 1] != ' ') e.beep(140.0 + i * 12, 0.05, Waveform.SQUARE, 0.05)
                yield(wait(0.13))
            }
            yield(wait(1.2))
            boss.say = ""
            // L'épée apparaît dans la main, les cœurs et la barre de vie aussi.
            p.sword = true
            e.spawn(p.x + p.dir * 26, p.alt + 30, 24, ParticleSpec(life = 0.8, color = 0xFFF0A0, size = 3.0, gravity = -20.0, up = 90.0, vx = 160.0))
            e.host.sfx(SfxCatalog.BUY)
            startFight()
            p.frozen = false
        }

        private fun startFight() {
            st.fight = VilleFight(ARENA_X)
            p.hearts = 5
            e.host.updateSave { it.copy(villeBossAttempts = it.villeBossAttempts + 1) }
            e.playBossMusic()
            e.bossMusicStarts++
        }

        /** Rayon des cercles d'attaque (dessin ET zone de dégâts), un tiers plus petits qu'à l'origine. */
        private fun circleRadius(): Double = (e.viewWidth / 9).coerceIn(75.0, 125.0)

        private fun hurtPlayer() {
            val f = st.fight ?: return
            if (p.hurt > 0 || f.over) return
            p.hearts--
            p.hurt = 1.1
            p.va = 380.0
            e.shake = 14.0
            e.countHeartLost()
            e.host.sfx(SfxCatalog.CRASH)
            if (p.hearts <= 0) {
                f.over = true
                e.run(defeat())
            }
        }

        private fun updateFight(dt: Double) {
            val f = st.fight ?: return
            val b = st.boss ?: return
            val aw = st.arenaHalfWidth
            b.hurt = max(0.0, b.hurt - dt)
            b.flash = max(0.0, b.flash - dt * 5)
            if (!f.over) {
                // Déplacement : le boss choisit où aller (surtout près du joueur).
                f.moveTimer -= dt
                if (f.moveTimer <= 0) {
                    if (e.random.nextDouble() < 0.65) {
                        f.targetX = p.x + (e.random.nextDouble() - 0.5) * 160
                        f.targetAlt = (p.alt + 20 + e.random.nextDouble() * 60).coerceIn(20.0, 300.0)
                    } else {
                        f.targetX = ARENA_X + (e.random.nextDouble() - 0.5) * aw * 1.3
                        f.targetAlt = 30 + e.random.nextDouble() * 270
                    }
                    f.targetX = f.targetX.coerceIn(ARENA_X - aw + 110, ARENA_X + aw - 110)
                    f.moveTimer = 2 + e.random.nextDouble() * 1.6
                }
                b.x = lerp(b.x, f.targetX, min(1.0, dt * 1.4))
                b.baseAlt = lerp(b.baseAlt, f.targetAlt, min(1.0, dt * 1.4))
                b.alt = b.baseAlt + sin(e.time * 2.2) * 8
                b.dir = if (p.x < b.x) -1 else 1
                // Attaques : cercles rouges (1,5 s pour sortir de la zone).
                f.attackTimer -= dt
                if (f.attackTimer <= 0) {
                    val ratio = f.hp.toDouble() / BOSS_HP
                    val count = if (ratio > 0.65) 1 else if (ratio > 0.3) 2 else 3
                    val r = circleRadius()
                    f.circles.add(BossCircle(p.x, p.alt + 20, r))
                    for (i in 1 until count) {
                        val rx = if (i == 1) {
                            p.x + p.vx * 0.9 + (e.random.nextDouble() - 0.5) * 120
                        } else {
                            ARENA_X + (e.random.nextDouble() - 0.5) * aw * 1.6
                        }
                        f.circles.add(BossCircle(rx.coerceIn(ARENA_X - aw, ARENA_X + aw), 20 + e.random.nextDouble() * 260, r))
                    }
                    e.beep(220.0, 0.2, Waveform.SAWTOOTH, 0.05)
                    f.attackTimer = lerp(1.1, 2.0, ratio)
                }
            }
            // Copie : une défaite pendant ce tour remplace la liste des cercles.
            for (ci in f.circles.toList()) {
                ci.t += dt
                if (ci.t >= CIRCLE_T && ci.boom == 0.0) {
                    ci.boom = 0.001
                    e.beep(70.0, 0.25, Waveform.SQUARE, 0.08)
                    if (hypot(p.x - ci.x, p.alt + 20 - ci.alt) < ci.radius && !f.over) hurtPlayer()
                    e.spawn(ci.x, ci.alt, 14, ParticleSpec(life = 0.5, color = 0xFF463C, size = 4.0, gravity = 200.0, up = 200.0, vx = 300.0))
                }
                if (ci.boom > 0) ci.boom += dt
            }
            f.circles = f.circles.filterTo(ArrayList()) { it.boom == 0.0 || it.boom < 0.3 }
            // Coup d'épée (bouton ⚔️ ou appui sur l'écran).
            if (e.input.attack && p.sword && p.swingCd <= 0 && !p.frozen) {
                p.swing = 0.22
                p.swingCd = 0.32
                f.pending = 0.06
                e.beep(900.0, 0.06, Waveform.TRIANGLE, 0.05)
            }
            if (f.pending > 0) {
                f.pending -= dt
                if (f.pending <= 0) {
                    f.pending = -1.0
                    val sx = p.x + p.dir * 58
                    val sa = p.alt + 28
                    val bx = b.x
                    val ba = b.alt + b.size * 0.19
                    if (abs(sx - bx) < 60 + b.size * 0.37 && abs(sa - ba) < 58 + b.size * 0.21 && b.hurt <= 0 && !f.over) {
                        f.hp--
                        b.hurt = 0.16
                        b.flash = 1.0
                        b.x += p.dir * 26
                        e.countSwordHit()
                        e.shake = max(e.shake, 5.0)
                        e.beep(300.0, 0.08, Waveform.SQUARE, 0.07)
                        e.spawn(bx, ba, 10, ParticleSpec(life = 0.5, color = 0xFF5A5A, size = 3.0, gravity = 300.0, up = 180.0, vx = 260.0, square = true))
                        f.streak++
                        f.streakTimer = 1.3
                        if (f.streak >= 5) {
                            // Trop de coups d'affilée : il se téléporte de l'autre côté.
                            f.streak = 0
                            e.glitch = 0.8
                            glitchSfx()
                            b.x = if (p.x < ARENA_X) ARENA_X + aw * 0.6 else ARENA_X - aw * 0.6
                            b.baseAlt = 230.0
                            f.targetX = b.x
                            f.targetAlt = 230.0
                            f.moveTimer = 1.6
                        }
                        if (f.hp <= 0) {
                            f.over = true
                            f.circles = ArrayList()
                            e.run(victory())
                        }
                    }
                }
            }
            f.streakTimer -= dt
            if (f.streakTimer <= 0) f.streak = 0
            updateTaunt(f, b, dt)
        }

        /**
         * À la moitié de sa vie, le boss lâche une réplique lettre par lettre
         * (même voix que son « ON VA VOIR ÇA »), sans arrêter le combat. Géré à
         * chaque image plutôt qu'en séquence : une défaite ou une victoire en
         * plein milieu ne laisse pas une phrase à moitié écrite dans sa bulle.
         */
        private fun updateTaunt(f: VilleFight, b: VilleNpc, dt: Double) {
            if (f.tauntText == null && !f.over && f.hp > 0 && f.hp <= BOSS_HP / 2) f.tauntText = HALF_LIFE_TAUNT
            val text = f.tauntText ?: return
            if (f.tauntDone) return
            if (f.over) {
                f.tauntDone = true
                b.say = ""
                return
            }
            f.tauntTimer += dt
            if (f.tauntShown < text.length) {
                while (f.tauntShown < text.length && f.tauntTimer >= TAUNT_LETTER_SECONDS) {
                    f.tauntTimer -= TAUNT_LETTER_SECONDS
                    f.tauntShown++
                    if (text[f.tauntShown - 1] != ' ') e.beep(140.0 + f.tauntShown * 8, 0.05, Waveform.SQUARE, 0.05)
                }
                b.say = text.substring(0, f.tauntShown)
            } else if (f.tauntTimer >= TAUNT_HOLD_SECONDS) {
                f.tauntDone = true
                b.say = ""
            }
        }

        private fun defeat(): Sequence<VilleWait> = sequence {
            p.frozen = true
            st.fight?.circles = ArrayList()
            yield(e.hold(0.8) { e.glitch = max(e.glitch, 1.2) })
            e.showMessage("Réessaie !", 2.0)
            yield(wait(1.2))
            e.resetPlayer(ARENA_X - st.arenaHalfWidth * 0.6, 1)
            p.sword = true
            st.boss?.let {
                it.x = ARENA_X + st.arenaHalfWidth * 0.4
                it.baseAlt = 60.0
            }
            startFight()
            p.frozen = false
        }

        private fun victory(): Sequence<VilleWait> = sequence {
            val b = st.boss ?: return@sequence
            // Boss vaincu : sa musique s'éteint en fondu pendant qu'il tombe.
            e.fadeOutBossMusic()
            p.frozen = true
            b.glow = DEFEATED_GLOW
            yield(until {
                b.alt = max(0.0, b.alt - 520 * e.dt)
                b.rot = lerp(b.rot, PI, min(1.0, e.dt * 4))
                b.alt <= 0
            })
            b.rot = PI
            e.shake = 18.0
            e.host.sfx(SfxCatalog.LAND)
            yield(wait(1.0))
            b.big = false
            val lines = listOf(
                "comment ?...",
                "j'étais pourtant sûr...",
                "mais tu as gagné je te l'accorde",
                "Adieu ${e.pseudo}...",
            )
            for (line in lines) {
                for (i in 1..line.length) {
                    b.say = line.substring(0, i)
                    val ch = line[i - 1]
                    // Lettre par lettre, irrégulier.
                    yield(wait(if (ch == '.') 0.3 else if (ch == ' ') 0.1 else 0.04 + e.random.nextDouble() * 0.2))
                }
                yield(wait(1.4))
            }
            b.say = ""
            // Désintégration.
            var k = 0.0
            yield(until {
                k += e.dt / 2.2
                b.alpha = 1 - c01(k)
                repeat(4) {
                    e.spawn(
                        b.x + (e.random.nextDouble() - 0.5) * b.size * 0.7,
                        b.alt + b.size * 0.19 + (e.random.nextDouble() - 0.5) * 40, 1,
                        ParticleSpec(
                            life = 1.4, color = if (e.random.nextDouble() < 0.5) 0xD2D2D7 else 0xFF505A,
                            size = 5.0, gravity = -90.0, up = 50.0, vx = 60.0, square = true,
                        ),
                    )
                }
                k >= 1
            })
            e.npcs = ArrayList()
            st.boss = null
            yield(wait(0.8))
            // Tout se met à trembler : il faut sortir d'ici, l'épée reste là.
            st.quake = true
            e.beep(50.0, 1.5, Waveform.SAWTOOTH, 0.08)
            e.showMessage("je ferais mieux de sortir d'ici", 0.0)
            p.sword = false
            st.droppedSwordX = p.x + p.dir * 30
            st.fight = null
            st.stage = 5.0
            st.escape = VilleEscape(ARENA_X + st.arenaHalfWidth + 140)
            p.frozen = false
        }

        private fun updateEscape(dt: Double) {
            val esc = st.escape ?: return
            val d = esc.voidX - p.x
            val speed = if (d > 620) 600.0 else if (d > 330) 330.0 else 285.0
            esc.voidX -= speed * dt
            if (e.random.nextDouble() < dt * 6) {
                e.spawn(
                    e.camX + e.random.nextDouble() * e.viewWidth, 430.0, 1,
                    ParticleSpec(life = 1.4, color = 0x5A5078, size = 7.0, gravity = 500.0, up = 0.0, vx = 20.0, square = true),
                )
            }
            if (e.busy || e.transitionBusy) return
            if (esc.voidX < p.x + 24) {
                // Rattrapée par le vide : on recommence la fuite depuis l'arène.
                e.run(sequence {
                    p.frozen = true
                    yield(e.hold(0.6) { e.glitch = max(e.glitch, 1.4) })
                    e.resetPlayer(ARENA_X - 60, -1)
                    esc.voidX = ARENA_X + st.arenaHalfWidth + 220
                    e.snapCamera()
                    p.frozen = false
                })
            } else if (p.x < RETURN_X + 70) {
                // De justesse !
                p.frozen = true
                e.startTransition(VilleTransKind.GLITCH_QUICK, 1.0, {
                    st.quake = false
                    st.escaped = true
                    st.escape = null
                    st.light = true
                    e.hideMessage()
                    e.setLevel("darkroom", D_PORTAL - 60, -1)
                    p.va = 520.0
                    p.vx = -260.0
                    e.shake = 22.0
                    e.host.sfx(SfxCatalog.CRASH)
                    repeat(30) {
                        e.spawn(D_PORTAL, D_PORTAL_ALT, 1, ParticleSpec(life = 0.6, color = 0xFFD25A, size = 2.0, gravity = 500.0, up = 260.0, vx = 520.0))
                    }
                })
            }
        }
    }

    companion object {
        // Aéroport + rue
        const val SKINSHOP_X = 3320.0
        const val DEPARTURES_X = 260.0
        const val AIRPORT_END = 2900.0
        const val SECURITY_X = 1610.0
        const val CROSS_A = 3900.0
        const val CROSS_B = 4260.0
        const val TOWER_A = 4880.0
        const val TOWER_DOOR = 5150.0
        const val CITY_W = 5520.0
        const val RESPAWN_X = 3050.0
        val CAR_COLORS = intArrayOf(0xD64541, 0x2F6FB5, 0xF1C40F, 0xECF0F1, 0x27AE60, 0x34495E, 0xE67E22)

        // Réception
        const val RECEPTION_W = 1800.0
        const val R_EXIT = 110.0
        const val R_STAIRS = 440.0
        const val R_ELEVATOR = 680.0
        const val R_BOARD = 930.0
        const val R_DESK = 1330.0

        // Pièce noire
        const val DARKROOM_W = 1100.0
        const val D_DOOR = 90.0
        const val D_SWITCH = 1010.0
        const val D_PORTAL = 550.0
        const val D_PORTAL_ALT = 175.0
        const val D_RADIUS = 118.0

        // Monde bugé
        const val GLITCH_W = 5600.0
        const val E1 = 1700.0
        const val E2 = 3200.0
        const val ARENA_X = 4700.0
        const val RETURN_X = 140.0
        const val BOSS_HP = 60
        const val CIRCLE_T = 1.5

        /** rgba(255,40,70,0.95), puis rgba(120,120,130,0.6) une fois vaincu. */
        const val BOSS_GLOW = 0xF2FF2846L
        const val DEFEATED_GLOW = 0x99787882L

        /** La réplique du boss à la moitié de sa vie (voir updateTaunt). */
        const val HALF_LIFE_TAUNT = "JE NE ME LAISSERAI PAS FAIRE !"
        const val TAUNT_LETTER_SECONDS = 0.1
        const val TAUNT_HOLD_SECONDS = 1.4
        val FIRST_QUESTIONS = listOf("Qui es-tu ?", "Tu es... moi ?")
        val SECOND_QUESTIONS = listOf("Hein ? Réponds !", "Tu parles ?")

        /** `lightState()` : un cycle de 16 s, vert 6,5 s puis clignotant 1,5 s puis rouge 8 s. */
        fun lightState(time: Double): CrosswalkLight {
            val t = time % 16
            return when {
                t < 6.5 -> CrosswalkLight(PedestrianLight.GREEN, 8 - t)
                t < 8 -> CrosswalkLight(PedestrianLight.BLINK, 8 - t)
                else -> CrosswalkLight(PedestrianLight.RED, 16 - t)
            }
        }
    }
}
