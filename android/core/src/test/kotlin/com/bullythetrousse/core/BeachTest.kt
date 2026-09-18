package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BeachTest {
    @Test
    fun `rollEvents ne declenche rien en dessous du seuil de distance`() {
        val events = Beach.rollEvents(predictedDistanceMeters = 99.0) { 0.0 }
        assertEquals(BeachEvents(), events)
    }

    @Test
    fun `rollEvents tire chaque evenement independamment au-dessus du seuil`() {
        val events = Beach.rollEvents(predictedDistanceMeters = 500.0) { 0.6 } // < 0.65 et 0.70, >= 0.55
        assertTrue(events.parasol)
        assertTrue(events.towel)
        assertFalse(events.castle)
    }

    @Test
    fun `landingOutcome priorise le parasol sur la serviette et le chateau`() {
        val events = BeachEvents(parasol = true, towel = true, castle = true)
        assertEquals(BeachLandingOutcome.PARASOL_BOUNCE, Beach.landingOutcome(events, BeachEvents(), inSpaceMode = false))
    }

    @Test
    fun `landingOutcome passe a la serviette une fois le parasol deja utilise`() {
        val events = BeachEvents(parasol = true, towel = true, castle = true)
        val used = BeachEvents(parasol = true)
        assertEquals(BeachLandingOutcome.TOWEL_FOUND, Beach.landingOutcome(events, used, inSpaceMode = false))
    }

    @Test
    fun `landingOutcome passe au chateau une fois parasol et serviette utilises`() {
        val events = BeachEvents(parasol = true, towel = true, castle = true)
        val used = BeachEvents(parasol = true, towel = true)
        assertEquals(BeachLandingOutcome.CASTLE_CRUSHED, Beach.landingOutcome(events, used, inSpaceMode = false))
    }

    @Test
    fun `landingOutcome ne declenche jamais rien en apesanteur`() {
        val events = BeachEvents(parasol = true, towel = true, castle = true)
        assertEquals(BeachLandingOutcome.NORMAL, Beach.landingOutcome(events, BeachEvents(), inSpaceMode = true))
    }

    @Test
    fun `applyParasolBounce respecte le plancher de vitesse verticale de 240`() {
        val state = FlightState(worldX = 100.0, worldY = 0.0, vx = 500.0, vy = -50.0)
        val bounced = Beach.applyParasolBounce(state)
        assertClose(1.0, bounced.worldY)
        assertClose(240.0, bounced.vy) // |−50|*0.62=31 < 240 -> plancher
        assertClose(470.0, bounced.vx) // 500*0.94
    }

    @Test
    fun `applyParasolBounce garde la vitesse calculee si elle depasse le plancher`() {
        val state = FlightState(worldX = 100.0, worldY = 0.0, vx = 500.0, vy = -1000.0)
        val bounced = Beach.applyParasolBounce(state)
        assertClose(620.0, bounced.vy) // 1000*0.62
    }

    @Test
    fun `enter remet l'argent a 0 une seule fois dans la vie de la sauvegarde`() {
        val firstTime = Beach.enter(GameSave(money = 5000))
        assertEquals(0, firstTime.money)
        assertTrue(firstTime.hasForgottenMoney)
        assertTrue(firstTime.inPlage)
        assertEquals("plage", firstTime.currentWorld)

        val alreadyForgotten = Beach.enter(GameSave(money = 5000, hasForgottenMoney = true))
        assertEquals(5000, alreadyForgotten.money)
    }

    @Test
    fun `enter ne fait rien si deja sur la plage`() {
        val save = GameSave(money = 5000, inPlage = true, currentWorld = "plage")
        assertEquals(save, Beach.enter(save))
    }

    @Test
    fun `leave revient au monde cour`() {
        val save = GameSave(inPlage = true, currentWorld = "plage")
        val left = Beach.leave(save)
        assertFalse(left.inPlage)
        assertEquals("cour", left.currentWorld)
    }

    @Test
    fun `buyClaquettes debite et donne acces a la cinematique`() {
        val result = Beach.buyClaquettes(GameSave(money = Beach.CLAQUETTES_COST))
        assertIs<Beach.PurchaseResult.Success>(result)
        assertEquals(0, result.save.money)
        assertTrue(result.save.hasClaquettes)
        assertFalse(result.save.plageUnlocked) // l'achat seul ne débloque pas le monde
    }

    @Test
    fun `buyClaquettes refuse sans assez d'argent`() {
        assertIs<Beach.PurchaseResult.NotEnoughMoney>(Beach.buyClaquettes(GameSave(money = Beach.CLAQUETTES_COST - 1)))
    }

    @Test
    fun `buyBusTicket debite, marque le succes et renvoie au monde normal`() {
        val save = GameSave(money = Beach.BUS_TICKET_COST, inPlage = true, currentWorld = "plage")
        val result = Beach.buyBusTicket(save)
        assertIs<Beach.PurchaseResult.Success>(result)
        assertEquals(0, result.save.money)
        assertTrue(result.save.hasTakenBusBack)
        assertFalse(result.save.inPlage)
        assertEquals("cour", result.save.currentWorld)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
