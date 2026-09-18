package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SkinsTest {
    @Test
    fun `find retombe sur la trousse classique pour un id inconnu`() {
        assertEquals("classique", Skins.find("n-existe-pas").id)
    }

    @Test
    fun `totalPuissance et totalVitesse ajoutent le bonus du skin equipe`() {
        val save = GameSave(puissanceLevel = 10, vitesseLevel = 4, equippedSkin = "feu") // +3/+2
        assertEquals(13, SkinStats.totalPuissance(save))
        assertEquals(6, SkinStats.totalVitesse(save))
    }

    @Test
    fun `la trousse de fer a 300 de durabilite max mais des bonus negatifs`() {
        val save = GameSave(puissanceLevel = 5, vitesseLevel = 5, equippedSkin = "fer")
        assertEquals(300, SkinStats.maxDurability(save))
        assertEquals(4, SkinStats.totalPuissance(save))
        assertEquals(4, SkinStats.totalVitesse(save))
    }

    @Test
    fun `acheter un skin le debite, l'ajoute aux possedes et l'equipe`() {
        val save = GameSave(money = 500) // doree coûte 400
        val result = SkinShop.buy(save, "doree")
        assertIs<SkinShop.PurchaseResult.Success>(result)
        assertEquals(100, result.save.money)
        assertTrue(result.save.ownedSkins.contains("doree"))
        assertEquals("doree", result.save.equippedSkin)
    }

    @Test
    fun `acheter sans assez d'argent ne change rien`() {
        val save = GameSave(money = 399)
        val result = SkinShop.buy(save, "doree")
        assertIs<SkinShop.PurchaseResult.NotEnoughMoney>(result)
    }

    @Test
    fun `equiper un skin a moins de durabilite max plafonne la durabilite actuelle`() {
        val save = GameSave(durability = 250, equippedSkin = "fer") // 250/300
        val updated = SkinShop.equip(save, "classique") // max 100
        assertEquals(100, updated.durability)
    }

    @Test
    fun `equiper un skin ne change pas la durabilite si elle est deja sous le nouveau max`() {
        val save = GameSave(durability = 40, equippedSkin = "classique")
        val updated = SkinShop.equip(save, "fer") // max 300, mais durability reste 40
        assertEquals(40, updated.durability)
    }
}
