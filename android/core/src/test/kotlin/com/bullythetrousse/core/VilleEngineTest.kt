package com.bullythetrousse.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Un hôte factice : la sauvegarde en mémoire et tout ce que le moteur a demandé. */
private class FakeHost(var now: Long = CALM_TIME) : VilleHost {
    override var save = GameSave(currentWorld = "ville", inVille = true, villeUnlocked = true)
    val sounds = ArrayList<List<Beep>>()
    var tires = 0
    var shopOpened = 0
    var skinShopClose: (() -> Unit)? = null
    var rooftop = 0
    var menu = 0
    var leftTo: String? = null

    override fun updateSave(transform: (GameSave) -> GameSave) { save = transform(save) }
    override fun nowMillis() = now
    override fun sfx(beeps: List<Beep>) { sounds += beeps }
    override fun playTires() { tires++ }
    override fun openShop() { shopOpened++ }
    override fun openSkinShop(onClose: () -> Unit) { skinShopClose = onClose }
    override fun goRooftop() { rooftop++ }
    override fun exitToMenu() { menu++ }
    override fun leaveVille(destination: String) { leftTo = destination }

    companion object {
        /** Entre deux évènements du calendrier (voir VilleEventsTest). */
        const val CALM_TIME = 1790474700000L + 60_000L
        /** En pleine coupure de courant (jour 20724). */
        const val OUTAGE_TIME = 1790563140000L + 60_000L
    }
}

class VilleEngineTest {
    private val host = FakeHost()
    private val engine = VilleEngine(host, Random(7)).apply { resize(1520.0, 1080.0) }
    private val p get() = engine.player

    /** Fait tourner [seconds] de jeu ; [each] prépare les commandes de chaque image. */
    private fun play(seconds: Double, each: () -> Unit = {}) {
        var t = 0.0
        while (t < seconds) {
            each()
            engine.frame(1 / 60.0)
            t += 1 / 60.0
        }
    }

    /** Joue jusqu'à ce que [condition] soit vraie (échoue au-delà de [limit] s). */
    private fun playUntil(limit: Double = 60.0, each: () -> Unit = {}, condition: () -> Boolean) {
        var t = 0.0
        while (!condition()) {
            assertTrue(t < limit, "condition jamais atteinte en $limit s")
            each()
            engine.frame(1 / 60.0)
            t += 1 / 60.0
        }
    }

    private fun interact() = play(1 / 60.0) { engine.input.interact = true }

    @Test
    fun `le tutoriel de la Ville s'ouvre une seule fois et fige la Ville en attendant`() {
        var shown = 0
        var close: (() -> Unit)? = null
        val tutoHost = object : VilleHost by host {
            override fun startVilleTutorial(onDone: () -> Unit) { shown++; close = onDone }
        }
        val e = VilleEngine(tutoHost, Random(7)).apply { resize(1520.0, 1080.0) }
        e.enter(VilleEntry.AIRPORT)
        assertEquals(1, shown)
        assertTrue(e.lock)
        val x0 = e.player.x
        repeat(90) { e.input.right = true; e.frame(1 / 60.0) }
        assertEquals(x0, e.player.x, "la trousse a bougé pendant le tutoriel")
        // Ce que fait l'écran à la fermeture : marquer le tutoriel comme vu.
        host.save = host.save.copy(villeTutorialSeen = true)
        close!!()
        assertFalse(e.lock)
        repeat(90) { e.input.right = true; e.frame(1 / 60.0) }
        assertTrue(e.player.x > x0)
        e.enter(VilleEntry.RECEPTION)
        assertEquals(1, shown)
    }

    @Test
    fun `le repere virtuel garde 540 px de haut`() {
        assertEquals(2.0, engine.scale)
        assertEquals(760.0, engine.viewWidth)
        assertEquals(540.0, engine.viewHeight)
        assertEquals(445.0, engine.groundY)
    }

    @Test
    fun `la trousse marche, saute moins haut si on relache, et dash avec 3 s de recharge`() {
        engine.enter(VilleEntry.AIRPORT)
        play(1.3) // l'ouverture en diaphragme bloque les commandes
        val x0 = p.x
        play(1.0) { engine.input.right = true }
        assertTrue(p.x - x0 in 270.0..300.0, "≈ 300 px/s : ${p.x - x0}")
        engine.input.right = false
        play(0.5)

        var peak = 0.0
        engine.input.holdJump = true
        play(0.8) { engine.input.jump = peak == 0.0; peak = maxOf(peak, p.alt) }
        engine.input.holdJump = false
        assertTrue(peak in 130.0..150.0, "saut complet : $peak")
        play(0.5)
        var shortPeak = 0.0
        play(0.8) { if (shortPeak == 0.0 && p.ground) engine.input.jump = true; shortPeak = maxOf(shortPeak, p.alt) }
        // Relâcher le saut ajoute 90 % de gravité à la montée : ≈ la moitié de la hauteur.
        assertTrue(shortPeak < peak * 0.6, "saut relâché : $shortPeak")
        engine.flushStats()
        assertEquals(2, host.save.villeJumps)

        val beforeDash = p.x
        play(0.2) { engine.input.dash = true }
        assertTrue(p.x - beforeDash > 150, "dash : ${p.x - beforeDash}")
        play(0.6) // l'élan retombe
        val afterDash = p.x
        assertTrue(engine.dashReady < 1)
        play(0.2) { engine.input.dash = true }
        assertTrue(p.x - afterDash < 5, "le dash recharge : ${p.x - afterDash}")
        engine.flushStats()
        assertEquals(1, host.save.villeDashes)
        assertTrue(host.save.villeWalked > 400)
    }

    @Test
    fun `la cinematique d'arrivee pose la trousse a l'aeroport et debloque la Ville`() {
        host.save = GameSave(currentWorld = "plage", inPlage = true, plageUnlocked = true)
        engine.enter(VilleEntry.ARRIVAL)
        assertEquals(VilleMode.ARRIVAL, engine.mode)
        playUntil(15.0) { engine.mode == VilleMode.PLAY }
        assertEquals("city", engine.level.id)
        assertTrue(host.save.villeUnlocked)
        assertTrue(host.save.inVille)
        assertFalse(host.save.inPlage)
        assertEquals("ville", host.save.currentWorld)
        assertTrue(engine.message!!.startsWith("✈️ Bienvenue en Ville"))
        assertEquals(520.0, p.x)
    }

    @Test
    fun `le portique bipe au passage`() {
        engine.enter(VilleEntry.AIRPORT)
        engine.resetPlayer(VilleLevels.SECURITY_X - 30, 1)
        engine.levels.city.state.lastX = p.x
        host.sounds.clear()
        playUntil(3.0, each = { engine.input.right = true }) { p.x > VilleLevels.SECURITY_X + 5 }
        play(0.2)
        assertTrue(host.sounds.any { it.first().frequencyHz == 1320.0 })
        assertTrue(host.sounds.any { it.first().frequencyHz == 1760.0 }, "second bip, 130 ms plus tard")
    }

    @Test
    fun `traverser au rouge, c'est se faire percuter et repartir du trottoir`() {
        engine.enter(VilleEntry.AIRPORT)
        play(1.3)
        engine.time = 10.0 // bonhomme rouge
        assertEquals(PedestrianLight.RED, VilleLevels.lightState(engine.time).pedestrian)
        engine.resetPlayer(VilleLevels.CROSS_A + 40, 1)
        play(1 / 60.0)
        assertTrue(engine.levels.city.state.dying)
        assertEquals(1, host.tires)
        playUntil(10.0) { !engine.levels.city.state.dying }
        assertEquals(VilleLevels.RESPAWN_X, p.x, 1.0)
        assertEquals(1, host.save.villeCrosswalkDeaths)
        assertEquals(PedestrianLight.GREEN, VilleLevels.lightState(engine.time).pedestrian)
        assertEquals("🚦 Attends que le bonhomme passe au vert...", engine.message)
        assertFalse(p.frozen)
    }

    @Test
    fun `au vert on traverse sans risque`() {
        engine.enter(VilleEntry.AIRPORT)
        play(1.3)
        engine.time = 0.5
        engine.resetPlayer(VilleLevels.CROSS_A - 10, 1)
        playUntil(3.0, each = { engine.input.right = true }) { p.x > VilleLevels.CROSS_B }
        assertFalse(engine.levels.city.state.dying)
        assertEquals(0, host.tires)
    }

    @Test
    fun `le comptoir des departs renvoie vers les mondes debloques`() {
        host.save = host.save.copy(plageUnlocked = true)
        engine.enter(VilleEntry.AIRPORT)
        play(1.3)
        engine.resetPlayer(VilleLevels.DEPARTURES_X, 1)
        play(0.1)
        interact()
        val popup = assertNotNull(engine.popup)
        assertEquals(listOf("🏫 Cour d'école", "🏖️ Plage"), popup.buttons.map { it.label })
        assertTrue(engine.lock)
        engine.pressPopupButton(1)
        playUntil(3.0) { host.leftTo != null }
        assertEquals("plage", host.leftTo)
    }

    @Test
    fun `la Trousserie bloque la trousse jusqu'a sa fermeture`() {
        engine.enter(VilleEntry.AIRPORT)
        play(1.3)
        engine.resetPlayer(VilleLevels.SKINSHOP_X, 1)
        play(0.1)
        interact()
        val close = assertNotNull(host.skinShopClose)
        assertTrue(engine.lock)
        val x = p.x
        play(0.5) { engine.input.right = true }
        assertEquals(x, p.x)
        close()
        assertFalse(engine.lock)
    }

    @Test
    fun `la porte de la Tour mene a la reception`() {
        engine.enter(VilleEntry.AIRPORT)
        play(1.3)
        engine.resetPlayer(VilleLevels.TOWER_DOOR, 1)
        play(0.1)
        interact()
        playUntil(2.0) { engine.level.id == "reception" }
        assertEquals(190.0, p.x)
    }

    @Test
    fun `la reception ouvre la boutique et debloque l'ascenseur`() {
        engine.enter(VilleEntry.RECEPTION)
        play(1.3)
        engine.resetPlayer(VilleLevels.R_ELEVATOR, 1)
        play(0.2)
        assertNull(engine.near, "l'ascenseur n'est pas encore utilisable")
        engine.resetPlayer(VilleLevels.R_DESK, 1)
        play(0.1)
        interact()
        assertTrue(host.save.villeReceptionDone)
        assertEquals(1, host.shopOpened)
        assertTrue(engine.paused)
        engine.resumeFromShop()
        engine.resetPlayer(VilleLevels.R_ELEVATOR, 1)
        play(0.2)
        assertEquals("Appeler l'ascenseur", engine.near?.label?.invoke())
        interact()
        assertEquals("Quel étage ?", engine.popup?.title)
        engine.pressPopupButton(0) // terrain de lancer
        playUntil(6.0) { host.rooftop == 1 }
        assertEquals(1, host.save.villeElevator)
    }

    @Test
    fun `le tableau des annonces liste les evenements a venir`() {
        engine.enter(VilleEntry.RECEPTION)
        play(1.3)
        engine.resetPlayer(VilleLevels.R_BOARD, 1)
        play(0.1)
        interact()
        val body = engine.popup?.body as VillePopupBody.Board
        assertTrue(body.events.isNotEmpty())
        assertTrue(body.events.all { it.endMillis > host.now })
    }

    @Test
    fun `pendant une coupure, boutique et ascenseur sont fermes et il faut monter a pied`() {
        host.now = FakeHost.OUTAGE_TIME
        host.save = host.save.copy(villeReceptionDone = true)
        engine.enter(VilleEntry.RECEPTION)
        play(1.3)
        assertTrue(engine.isOutage())
        engine.resetPlayer(VilleLevels.R_DESK, 1)
        play(0.1)
        interact()
        assertEquals(0, host.shopOpened)
        assertEquals("⚡ Coupure de courant : la boutique est fermée.", engine.message)
        engine.resetPlayer(VilleLevels.R_ELEVATOR, 1)
        play(0.2)
        assertEquals("⚡ Hors service", engine.near?.label?.invoke())
        interact()
        assertNull(engine.popup)
        engine.resetPlayer(VilleLevels.R_STAIRS, 1)
        play(0.2)
        assertEquals("Prendre les escaliers", engine.near?.label?.invoke())
        interact()
        engine.pressPopupButton(1) // mode histoire, à pied
        playUntil(5.0) { engine.mode == VilleMode.STAIRS }
        assertEquals(1, host.save.villeStairs)
        // Sept cercles ratés (aucun appui) : on arrive quand même, essoufflé.
        playUntil(20.0) { engine.level.id == "darkroom" && engine.mode == VilleMode.PLAY }
        assertEquals(7, engine.stairs.rings.size)
        assertTrue(engine.stairs.rings.none { it })
    }

    @Test
    fun `un appui pile quand les cercles se superposent est reussi`() {
        engine.enter(VilleEntry.RECEPTION)
        engine.startStairs("roof")
        playUntil(3.0) { engine.stairs.ringT >= 0 && kotlin.math.abs(engine.stairs.radius - engine.stairs.target) < 4 }
        play(1 / 60.0) { engine.input.tap = true }
        assertEquals(listOf(true), engine.stairs.rings)
    }

    @Test
    fun `le mode histoire se joue jusqu'au generique`() {
        host.save = host.save.copy(pseudo = "Evan")
        engine.enter(VilleEntry.RECEPTION)
        engine.goFloor("story")
        val story = assertNotNull(engine.story)
        assertEquals("darkroom", engine.level.id)
        assertFalse(engine.showBackButton)
        assertEquals(VilleMusic.SILENT, engine.music)
        play(1.3) // fin de l'ouverture de la réception : les commandes reviennent

        // L'interrupteur, puis le portail.
        engine.resetPlayer(VilleLevels.D_SWITCH, 1)
        play(0.1)
        assertNull(engine.near.takeIf { it?.label?.invoke() == "Entrer dans le portail" })
        interact()
        assertTrue(story.light)
        engine.resetPlayer(VilleLevels.D_PORTAL - 40, 1)
        play(0.1)
        assertEquals("Entrer dans le portail", engine.near?.label?.invoke())
        interact()
        playUntil(8.0) { engine.level.id == "glitch" }

        // Réveil : la fenêtre des commandes attend d'être fermée.
        playUntil(8.0) { engine.popup != null }
        assertEquals("🧭 Commandes", engine.popup?.title)
        engine.pressPopupButton(0)
        playUntil(1.0) { story.stage == 1.0 }

        // Première rencontre : la trousse aux couleurs inversées.
        playUntil(20.0, each = { engine.input.right = true }) { engine.choices != null }
        engine.input.right = false
        assertEquals(VilleLevels.FIRST_QUESTIONS, engine.choices)
        engine.pickChoice(1)
        playUntil(8.0) { engine.choices != null }
        engine.pickChoice(0)
        play(0.1) // la séquence reprend à l'image suivante
        assertEquals("Hein ? Réponds !", story.answer2)
        playUntil(10.0) { story.stage == 2.0 }
        assertTrue(p.invert, "après le glitch, c'est le joueur qui est inversé")

        // Deuxième rencontre : l'autre répète mot pour mot les deux réponses, dans l'ordre.
        playUntil(20.0, each = { engine.input.right = true }) { story.stage == 2.5 }
        engine.input.right = false
        playUntil(5.0) { engine.npcs.firstOrNull()?.say == "Tu es... moi ?" }
        playUntil(5.0) { engine.npcs.firstOrNull()?.say == "Hein ? Réponds !" }
        playUntil(10.0) { story.stage == 3.0 }
        assertFalse(p.invert)

        // Troisième rencontre : aucun choix, l'autre dit seulement « Et maintenant qui est qui ? ».
        playUntil(20.0, each = { engine.input.right = true }) { story.stage == 3.5 }
        engine.input.right = false
        playUntil(5.0, each = { assertNull(engine.choices) }) { engine.npcs.firstOrNull()?.say == "Et maintenant qui est qui ?" }
        playUntil(30.0, each = { assertNull(engine.choices) }) { story.fight != null }
        assertTrue(p.sword)
        assertEquals(VilleMusic.BOSS, engine.music)
        assertEquals(1, host.save.villeBossAttempts)
        assertEquals(4, engine.platforms().size)
        val boss = assertNotNull(story.boss)

        // Le combat : on se colle au boss et on frappe jusqu'à la victoire. À
        // mi-vie, il lâche sa réplique lettre par lettre.
        val bossLines = LinkedHashSet<String>()
        playUntil(120.0, each = {
            p.x = boss.x - 58
            p.alt = boss.alt + boss.size * 0.19 - 28
            p.dir = 1
            p.hurt = 1.0 // invulnérable pour le test
            engine.input.attack = true
            if (boss.say.isNotEmpty()) bossLines += boss.say
        }) { story.fight?.over == true }
        engine.flushStats()
        assertEquals(VilleLevels.BOSS_HP, host.save.villeSwordHits)
        assertEquals("J", bossLines.first())
        assertTrue(VilleLevels.HALF_LIFE_TAUNT in bossLines, "réplique de mi-vie jamais écrite en entier")

        // Le boss vaincu, sa musique s'éteint en fondu d'une seconde.
        play(0.5)
        assertEquals(VilleMusic.BOSS, engine.music)
        assertTrue(engine.bossVolume < 0.5f)
        play(0.6)
        assertEquals(VilleMusic.SILENT, engine.music)
        assertEquals(0.5f, engine.bossVolume)

        // Victoire : le boss s'effondre, parle, se désintègre ; tout tremble.
        playUntil(40.0) { story.escape != null }
        assertNull(story.boss)
        assertEquals(5.0, story.stage)
        assertTrue(story.quake)
        assertFalse(p.sword)
        assertEquals("je ferais mieux de sortir d'ici", engine.message)

        // La fuite vers le portail de gauche, dash à la clé.
        playUntil(30.0, each = { engine.input.left = true; engine.input.dash = true }) { engine.level.id == "darkroom" }
        engine.input.left = false
        assertTrue(story.escaped)
        assertEquals(VilleMusic.SILENT, engine.music, "la fuite se fait sans la musique du boss")

        // La porte : générique.
        play(1.0) // fin de la transition
        engine.resetPlayer(VilleLevels.D_DOOR, -1)
        play(0.1)
        interact()
        playUntil(3.0) { engine.mode == VilleMode.CREDITS }
        val credits = assertNotNull(engine.credits)
        assertEquals("oh Evan tu as fini mon jeu ?", credits.intro.first())
        assertEquals("1 fois", credits.stats.last().second)
        assertEquals(VilleMusic.SILENT, engine.music)
        assertEquals(0.5f, engine.bossVolume)

        engine.endCredits()
        assertTrue(host.save.villeStoryDone)
        assertEquals(1, host.save.villeStoryRuns)
        assertEquals(FakeHost.CALM_TIME, host.save.villeStoryDoneAt)
        assertEquals("reception", engine.level.id)
        assertNull(engine.story)
        assertEquals(VilleMusic.WORLD, engine.music)
    }

    @Test
    fun `perdre ses 5 coeurs relance le combat`() {
        engine.enter(VilleEntry.RECEPTION)
        engine.goFloor("story")
        val story = engine.story!!
        story.light = true
        story.introDone = true
        story.stage = 3.0
        engine.setLevel("glitch", VilleLevels.ARENA_X - 100, 1)
        play(1.3)
        playUntil(40.0, each = { engine.choices?.let { engine.pickChoice(0) } }) { story.fight != null }
        val attempts = host.save.villeBossAttempts
        // On reste planté : les cercles finissent par tout emporter.
        playUntil(120.0) { host.save.villeBossAttempts > attempts }
        assertEquals(5, p.hearts)
        engine.flushStats()
        assertEquals(5, host.save.villeHeartsLost)
        assertEquals(2, engine.bossMusicStarts)
    }

    @Test
    fun `revenir vers le portail de gauche ramene a la piece noire`() {
        engine.enter(VilleEntry.RECEPTION)
        engine.goFloor("story")
        val story = engine.story!!
        story.light = true
        story.introDone = true
        story.stage = 1.0
        engine.setLevel("glitch", VilleLevels.RETURN_X + 40, 1)
        play(1.5)
        assertEquals("darkroom", engine.level.id)
        assertEquals(VilleLevels.D_PORTAL + 110, p.x)
    }

    @Test
    fun `le bouton menu quitte la Ville, sauf pendant le generique`() {
        engine.enter(VilleEntry.AIRPORT)
        engine.startCredits()
        engine.onBackButton()
        assertEquals(0, host.menu)
        engine.endCredits()
        engine.onBackButton()
        assertEquals(1, host.menu)
    }

    @Test
    fun `revenir du toit fait sortir de l'ascenseur`() {
        engine.enter(VilleEntry.ELEVATOR)
        assertEquals("reception", engine.level.id)
        assertTrue(p.frozen)
        playUntil(5.0) { !p.frozen }
        assertEquals(VilleLevels.R_ELEVATOR + 90, p.x, 1.0)
        assertEquals(1.0, p.alpha)
    }
}
