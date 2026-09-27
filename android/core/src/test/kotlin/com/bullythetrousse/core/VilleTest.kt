package com.bullythetrousse.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VilleTest {
    private val inCity = GameSave(currentWorld = "ville", inVille = true, villeUnlocked = true)

    @Test
    fun `tout est 20 pour cent plus cher en Ville, et seulement en Ville`() {
        assertEquals(1200, Ville.price(1000, inCity))
        assertEquals(1000, Ville.price(1000, GameSave()))
        // Math.round : 25 * 1.2 = 30, 33 * 1.2 = 39.6 -> 40
        assertEquals(40, Ville.price(33, inCity))
    }

    @Test
    fun `les gains sont majores de 20 pour cent en Ville`() {
        assertEquals(120, Ville.applyEarnMultiplier(100, inCity))
        assertEquals(100, Ville.applyEarnMultiplier(100, GameSave()))
    }

    @Test
    fun `les achats de la boutique suivent le prix de la Ville`() {
        val rich = inCity.copy(money = 100_000)
        val upgrade = assertIs<Shop.PurchaseResult.Success>(Shop.buyPuissance(rich))
        assertEquals(30, upgrade.cost) // 25 x 1,2
        val skin = assertIs<SkinShop.PurchaseResult.Success>(SkinShop.buy(rich, "doree"))
        assertEquals(100_000 - 480, skin.save.money)
        val trail = assertIs<TrailShop.PurchaseResult.Success>(TrailShop.buy(rich, Trails.ALL[1].id))
        assertEquals(100_000 - Ville.price(Trails.ALL[1].cost, rich), trail.save.money)
    }

    @Test
    fun `arrive debloque la Ville et y enferme le joueur`() {
        val save = Ville.arrive(GameSave(inPlage = true, currentWorld = "plage"), nowMillis = 42L)
        assertTrue(save.villeUnlocked)
        assertTrue(save.inVille)
        assertFalse(save.inPlage)
        assertEquals("ville", save.currentWorld)
        assertEquals(42L, save.villeArrivedAt)
        assertEquals(42L, Ville.arrive(save, 99L).villeArrivedAt) // première arrivée conservée
    }

    @Test
    fun `leave rend la liberte, et partir vers la Plage y enferme`() {
        val cour = Ville.leave(inCity, "cour")
        assertFalse(cour.inVille)
        assertEquals("cour", cour.currentWorld)
        val plage = Ville.leave(inCity, "plage")
        assertTrue(plage.inPlage)
        assertEquals("plage", plage.currentWorld)
    }

    @Test
    fun `au chargement on reste en Ville, ou on retombe sur la Cour`() {
        assertEquals("ville", Ville.normalizeOnLoad(GameSave(inVille = true, inPlage = true)).currentWorld)
        assertFalse(Ville.normalizeOnLoad(GameSave(inVille = true, inPlage = true)).inPlage)
        assertEquals("cour", Ville.normalizeOnLoad(GameSave(currentWorld = "ville")).currentWorld)
        assertEquals("plage", Ville.normalizeOnLoad(GameSave(currentWorld = "plage", inPlage = true)).currentWorld)
    }

    @Test
    fun `le comptoir des departs ne propose que les mondes debloques`() {
        assertEquals(listOf("cour"), Ville.departures(inCity).map { it.worldId })
        val all = inCity.copy(volcanUnlocked = true, plageUnlocked = true)
        assertEquals(listOf("cour", "volcans", "plage"), Ville.departures(all).map { it.worldId })
    }

    @Test
    fun `un gain compte pour le monde ou il est fait`() {
        assertEquals(50, Ville.recordWorldEarning(inCity, 50).villeMoneyEarned)
        assertEquals(50, Ville.recordWorldEarning(GameSave(inPlage = true, currentWorld = "plage"), 50).plageMoneyEarned)
        assertEquals(GameSave(), Ville.recordWorldEarning(GameSave(), 50))
    }

    @Test
    fun `reclamer un defi en Ville compte dans l'argent gagne en Ville`() {
        val save = inCity.copy(dailyChallenges = listOf(DailyChallenge("throws", 1.0, 300, progress = 1.0)))
        val claimed = assertIs<DailyChallenges.ClaimResult.Success>(DailyChallenges.claim(save, 0))
        assertEquals(300, claimed.save.villeMoneyEarned)
    }

    @Test
    fun `les champs de la Ville font l'aller-retour Firestore`() {
        val save = inCity.copy(villeWalked = 1234.56, villeHeatRestUntil = 1790474100000L, ownedCosmetics = listOf("noeud"), equippedCosmetic = "noeud")
        val back = SaveCodec.fromFieldMap(SaveCodec.toFieldMap(save))
        assertEquals(save, back)
    }

    /* ---- Toit : flaques ---- */

    @Test
    fun `pas de flaque sans fuite ni pluie`() {
        assertTrue(VilleRooftop.puddlesInRange(0.0, 50_000.0, null, 1.0).isEmpty())
        assertTrue(VilleRooftop.puddlesInRange(0.0, 50_000.0, VilleEventType.CANICULE, 1.0).isEmpty())
    }

    @Test
    fun `les flaques de pluie tombent au meme endroit que sur le site`() {
        val puddles = VilleRooftop.puddlesInRange(0.0, 3000.0, VilleEventType.PLUIE, 123.456)
        val expected = listOf(
            1236.4892579881882 to 74.84283162735665,
            1582.6240349075524 to 97.80900262270734,
            1965.0251033880631 to 85.4717134654129,
            2556.7685000066995 to 104.26018964491959,
            2917.433020139602 to 113.5328269681122,
        )
        assertEquals(expected.size, puddles.size)
        expected.zip(puddles).forEach { (e, p) ->
            assertEquals(e.first, p.centerX, 1e-6)
            assertEquals(e.second, p.halfWidth, 1e-6)
        }
    }

    @Test
    fun `les flaques de la fuite tombent au meme endroit que sur le site`() {
        val puddles = VilleRooftop.puddlesInRange(0.0, 5000.0, VilleEventType.FUITE, 42.5)
        val expected = listOf(346.49331492913194, 1141.0794801005395, 4015.8072671459086, 4865.420167154982)
        assertEquals(expected.size, puddles.size)
        expected.zip(puddles).forEach { (e, p) -> assertTrue(abs(e - p.centerX) < 1e-6) }
    }

    @Test
    fun `puddleAt detecte l'interieur d'une flaque et pas le bord exterieur`() {
        assertTrue(VilleRooftop.puddleAt(1236.0, VilleEventType.PLUIE, 123.456))
        assertTrue(VilleRooftop.puddleAt(1236.4892579881882 + 74.8, VilleEventType.PLUIE, 123.456))
        assertFalse(VilleRooftop.puddleAt(1236.4892579881882 + 75.0, VilleEventType.PLUIE, 123.456))
        assertFalse(VilleRooftop.puddleAt(100.0, VilleEventType.PLUIE, 123.456))
    }

    @Test
    fun `jamais de flaque au pied du lanceur`() {
        for (seed in 0 until 200) {
            val first = VilleRooftop.puddlesInRange(0.0, 400.0, VilleEventType.PLUIE, seed.toDouble()).firstOrNull() ?: continue
            assertTrue(first.centerX - first.halfWidth >= 12 * PhysicsConstants.SCALE)
        }
    }

    @Test
    fun `la glissade part a au moins 700 et ralentit`() {
        assertEquals(700.0, VilleRooftop.slideStartSpeed(-100.0))
        assertEquals(1200.0, VilleRooftop.slideStartSpeed(-1000.0))
        val (x, v) = VilleRooftop.slideStep(0.0, 1000.0, 0.5)
        assertEquals(500.0, x)
        assertEquals(910.0, v, 1e-9)
    }

    @Test
    fun `la trousse se couche a plat en glissant`() {
        var rotation = 2.9
        repeat(200) { rotation = VilleRooftop.slideRotation(rotation, 1 / 60.0) }
        assertEquals(PI, rotation, 1e-3)
    }

    @Test
    fun `la relance est plus rapide que le lancer et peut etre parfaite`() {
        val centered = VilleRooftop.relaunch(0.0, lastLaunchSpeed = 1000.0, perfectWindow = 0.08, wasPerfect = false)
        assertEquals(1300.0, centered.initialSpeed)
        assertTrue(centered.isPerfect)
        assertEquals(centered.vx, centered.vy, 1e-9) // 45°
        val weak = VilleRooftop.relaunch(1.0, lastLaunchSpeed = 100.0, perfectWindow = 0.08, wasPerfect = false)
        assertEquals(600.0, weak.initialSpeed)
        assertFalse(weak.isPerfect)
        assertTrue(weak.vy < weak.vx) // 29° : plus rasant
        assertTrue(VilleRooftop.relaunch(1.0, 100.0, 0.08, wasPerfect = true).isPerfect)
    }

    /* ---- Toit : canicule ---- */

    @Test
    fun `la canicule impose 2 minutes de repos tous les 5 lancers`() {
        var save = inCity
        repeat(4) {
            val (s, rest) = VilleRooftop.onThrowCounted(save, VilleEventType.CANICULE, 1000L)
            assertFalse(rest)
            save = s
        }
        assertEquals(4, save.villeHeatThrows)
        val (after, rest) = VilleRooftop.onThrowCounted(save, VilleEventType.CANICULE, 1000L)
        assertTrue(rest)
        assertEquals(0, after.villeHeatThrows)
        assertEquals(5, after.villeThrows)
        assertEquals(1000L + 120_000L, after.villeHeatRestUntil)
        assertTrue(VilleRooftop.isResting(after, 1000L))
        assertFalse(VilleRooftop.isResting(after, 121_000L))
        assertEquals(120L, VilleRooftop.restSecondsLeft(after, 1000L))
        assertEquals("2:00", VilleRooftop.formatRest(120))
        assertEquals("0:09", VilleRooftop.formatRest(9))
    }

    @Test
    fun `hors canicule on compte juste le lancer`() {
        val (save, rest) = VilleRooftop.onThrowCounted(inCity, VilleEventType.PLUIE, 0L)
        assertFalse(rest)
        assertEquals(1, save.villeThrows)
        assertEquals(0, save.villeHeatThrows)
    }

    @Test
    fun `le repos ne bloque que le toit`() {
        val resting = inCity.copy(villeHeatRestUntil = 10_000L)
        assertTrue(VilleRooftop.isResting(resting, 0L))
        assertFalse(VilleRooftop.isResting(resting.copy(currentWorld = "cour"), 0L))
    }

    /* ---- Crash sur l'avion ---- */

    @Test
    fun `le crash ne part que d'un lancer parfait sur la Plage, juste avant l'apogee`() {
        val nearApex = FlightState(worldX = 500.0, worldY = 200.0, vx = 400.0, vy = 150.0)
        assertTrue(PlaneCrash.shouldStart("plage", isPerfect = true, inSpaceMode = false, flight = nearApex))
        assertFalse(PlaneCrash.shouldStart("cour", isPerfect = true, inSpaceMode = false, flight = nearApex))
        assertFalse(PlaneCrash.shouldStart("plage", isPerfect = false, inSpaceMode = false, flight = nearApex))
        assertFalse(PlaneCrash.shouldStart("plage", isPerfect = true, inSpaceMode = false, flight = nearApex.copy(vy = 300.0)))
        assertFalse(PlaneCrash.shouldStart("plage", isPerfect = true, inSpaceMode = false, flight = nearApex.copy(vy = -1.0)))
        assertFalse(PlaneCrash.shouldStart("plage", isPerfect = true, inSpaceMode = false, flight = nearApex.copy(worldY = 30.0)))
    }

    @Test
    fun `l'avion rattrape la trousse, l'emporte puis passe la main aux nuages`() {
        val flight = FlightState(worldX = 2000.0, worldY = 300.0, vx = 400.0, vy = 150.0)
        var state = PlaneCrash.start(flight, rotationSpeed = 10.0, screenWidth = 1000.0)
        assertEquals(2000.0 - 300 - 520, state.px)
        var impacts = 0
        var handOvers = 0
        var time = 0.0
        while (time < 6.0) {
            val step = PlaneCrash.step(state, 1 / 60.0, gravity = 950.0)
            if (step.impact) impacts++
            if (step.handOver) handOvers++
            state = step.state
            time += 1 / 60.0
        }
        assertEquals(1, impacts)
        assertEquals(1, handOvers)
        assertTrue(state.attached)
        assertTrue(state.handed)
        // Encastrée sur le flanc : la trousse suit l'avion.
        assertEquals(state.px + PlaneCrash.STICK_X * PlaneCrash.PLANE_SCALE, state.flight.worldX, 1e-9)
        assertEquals(state.py - 2, state.flight.worldY, 1e-9)
        assertNotNull(state.sweepStart)
        assertTrue(state.sweepStart!! - state.impactT >= PlaneCrash.SWEEP_DELAY - 1e-9)
    }

    /* ---- La Trousserie ---- */

    @Test
    fun `acheter un cosmetique le porte, au prix de la Ville`() {
        val rich = inCity.copy(money = 10_000)
        val bought = assertIs<Cosmetics.Result.Success>(Cosmetics.press(rich, "noeud"))
        assertEquals(1440, bought.spent)
        assertEquals(10_000 - 1440, bought.save.money)
        assertEquals(listOf("noeud"), bought.save.ownedCosmetics)
        assertEquals("noeud", bought.save.equippedCosmetic)
        val removed = assertIs<Cosmetics.Result.Success>(Cosmetics.press(bought.save, "noeud"))
        assertEquals("", removed.save.equippedCosmetic)
        assertEquals(0, removed.spent)
        val worn = assertIs<Cosmetics.Result.Success>(Cosmetics.press(removed.save, "noeud"))
        assertEquals("noeud", worn.save.equippedCosmetic)
        assertEquals(bought.save.money, worn.save.money)
    }

    @Test
    fun `pas assez d'argent pour la couronne`() {
        assertEquals(Cosmetics.Result.NotEnoughMoney, Cosmetics.press(inCity.copy(money = 20_000), "couronne"))
        assertNull(Cosmetics.find("inconnu"))
    }
}
