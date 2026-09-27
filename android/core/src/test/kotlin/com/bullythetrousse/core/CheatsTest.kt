package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheatsTest {
    @Test
    fun `un skin inconnu est refuse et un skin connu est equipe`() {
        assertNull(Cheats.unlockSkin(GameSave(), "nexistepas"))
        val s = Cheats.unlockSkin(GameSave(), "vampire")!!
        assertTrue("vampire" in s.ownedSkins)
        assertEquals("vampire", s.equippedSkin)
        assertEquals(s.ownedSkins.size, Cheats.unlockSkin(s, "vampire")!!.ownedSkins.size)
    }

    @Test
    fun `tous les skins sans doublon`() {
        val s = Cheats.unlockAllSkins(GameSave())
        assertEquals(s.ownedSkins.toSet().size, s.ownedSkins.size)
        assertTrue(Skins.ALL.all { it.id in s.ownedSkins })
    }

    @Test
    fun `la durabilite reste dans ses bornes`() {
        assertEquals(0, Cheats.setDurability(GameSave(), -5).durability)
        assertEquals(SkinStats.maxDurability(GameSave()), Cheats.setDurability(GameSave(), 99999).durability)
    }

    @Test
    fun `les niveaux absents sont gardes`() {
        val s = Cheats.setLevels(GameSave(puissanceLevel = 3, vitesseLevel = 4), 10, null)
        assertEquals(10, s.puissanceLevel)
        assertEquals(4, s.vitesseLevel)
    }

    @Test
    fun `aller a la plage puis reprendre le bus`() {
        val plage = Cheats.goPlage(GameSave(money = 50))
        assertTrue(plage.inPlage)
        assertEquals("plage", plage.currentWorld)
        assertFalse(Cheats.retourBus(plage)!!.inPlage)
        assertNull(Cheats.retourBus(GameSave()))
    }

    @Test
    fun `remettre le volcan a zero quitte le monde volcan`() {
        val s = Cheats.resetVolcan(GameSave(volcanUnlocked = true, currentWorld = "volcans", volcanFailedUntil = 9L))
        assertFalse(s.volcanUnlocked)
        assertEquals("cour", s.currentWorld)
        assertEquals(0L, s.volcanFailedUntil)
    }

    @Test
    fun `les defis sont regeneres`() {
        val s = Cheats.resetChallenges(GameSave(dailyChallengeDate = "2026-09-24"), "2026-09-24")
        assertEquals(3, s.dailyChallenges.size)
    }

    @Test
    fun `le message de notification suit le site`() {
        assertEquals("🕹️ Léa a utilisé cheats.addMoney() (500)", Cheats.notifyMessage("Léa", "addMoney", listOf("500")))
        assertEquals("🕹️ (pseudo vide) a utilisé cheats.show()", Cheats.notifyMessage("", "show", emptyList()))
    }
}
