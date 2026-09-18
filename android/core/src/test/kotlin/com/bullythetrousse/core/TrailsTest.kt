package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrailsTest {
    @Test
    fun `find retombe sur la trainee blanche pour un id inconnu`() {
        assertEquals("blanche", Trails.find("n-existe-pas").id)
    }

    @Test
    fun `acheter une trainee la debite, l'ajoute aux possedees et l'equipe directement`() {
        val save = GameSave(money = 500) // dorée coûte 300
        val result = TrailShop.buy(save, "doree")
        assertIs<TrailShop.PurchaseResult.Success>(result)
        assertEquals(200, result.save.money)
        assertTrue(result.save.ownedTrails.contains("doree"))
        assertEquals("doree", result.save.equippedTrail)
    }

    @Test
    fun `acheter sans assez d'argent ne change rien`() {
        val save = GameSave(money = 299)
        val result = TrailShop.buy(save, "doree")
        assertIs<TrailShop.PurchaseResult.NotEnoughMoney>(result)
    }

    @Test
    fun `equiper une trainee deja possedee ne coute rien`() {
        val save = GameSave(ownedTrails = listOf("blanche", "doree"), equippedTrail = "blanche")
        val updated = TrailShop.equip(save, "doree")
        assertEquals("doree", updated.equippedTrail)
        assertEquals(save.money, updated.money)
    }
}
