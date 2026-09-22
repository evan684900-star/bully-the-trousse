package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepairTest {
    @Test
    fun `trousse intacte ne coute rien`() {
        val save = GameSave(durability = 100)
        assertEquals(0, Repair.cost(save))
        assertIs<Repair.Result.AlreadyFull>(Repair.repair(save))
    }

    @Test
    fun `arrondit au superieur par tranche de 10`() {
        // 1 point manquant coûte déjà une tranche entière, comme Math.ceil côté web.
        assertEquals(500, Repair.cost(GameSave(durability = 99)))
        assertEquals(500, Repair.cost(GameSave(durability = 90)))
        assertEquals(1000, Repair.cost(GameSave(durability = 89)))
        assertEquals(5000, Repair.cost(GameSave(durability = 0)))
    }

    @Test
    fun `repare et debite le bon montant`() {
        val save = GameSave(durability = 40, money = 10_000)
        val result = Repair.repair(save)
        assertIs<Repair.Result.Success>(result)
        assertEquals(3000, result.cost) // 60 manquants -> 6 tranches
        assertEquals(100, result.save.durability)
        assertEquals(7000, result.save.money)
    }

    @Test
    fun `refuse si l argent manque`() {
        val save = GameSave(durability = 0, money = 100)
        assertIs<Repair.Result.NotEnoughMoney>(Repair.repair(save))
    }

    @Test
    fun `tient compte de la durabilite du skin equipe`() {
        // La Trousse de Fer monte à 300 de durabilité (voir Skins).
        val save = GameSave(durability = 100, equippedSkin = "fer", ownedSkins = listOf("fer"), money = 100_000)
        assertEquals(SkinStats.maxDurability(save), 300)
        assertEquals(10_000, Repair.cost(save)) // 200 manquants -> 20 tranches
        val result = Repair.repair(save)
        assertIs<Repair.Result.Success>(result)
        assertEquals(300, result.save.durability)
    }
}
