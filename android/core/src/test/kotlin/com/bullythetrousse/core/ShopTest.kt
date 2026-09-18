package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ShopTest {
    @Test
    fun `acheter un niveau de puissance debite le cout exact et incremente le niveau`() {
        val cost = Economy.upgradeCost(0) // niveau 0 -> 1
        val save = GameSave(money = cost)

        val result = Shop.buyPuissance(save)

        assertIs<Shop.PurchaseResult.Success>(result)
        assertEquals(cost, result.cost)
        assertEquals(1, result.save.puissanceLevel)
        assertEquals(0, result.save.money)
    }

    @Test
    fun `acheter sans assez d'argent ne change rien`() {
        val cost = Economy.upgradeCost(0)
        val save = GameSave(money = cost - 1)

        val result = Shop.buyVitesse(save)

        assertIs<Shop.PurchaseResult.NotEnoughMoney>(result)
    }

    @Test
    fun `le cout du niveau suivant augmente apres un achat`() {
        val save = GameSave(money = 1_000_000)
        val first = Shop.buyPuissance(save) as Shop.PurchaseResult.Success
        val second = Shop.buyPuissance(first.save) as Shop.PurchaseResult.Success

        assertEquals(2, second.save.puissanceLevel)
        assertEquals(Economy.upgradeCost(0), first.cost)
        assertEquals(Economy.upgradeCost(1), second.cost)
    }

    @Test
    fun `acheter vitesse n'affecte pas le niveau de puissance`() {
        val cost = Economy.upgradeCost(0)
        val save = GameSave(money = cost, puissanceLevel = 3)

        val result = Shop.buyVitesse(save) as Shop.PurchaseResult.Success

        assertEquals(3, result.save.puissanceLevel)
        assertEquals(1, result.save.vitesseLevel)
    }
}
