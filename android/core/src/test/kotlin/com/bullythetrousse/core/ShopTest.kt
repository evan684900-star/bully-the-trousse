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

    @Test
    fun `acheter 10 niveaux paie chaque niveau a son propre prix`() {
        val expected = (0 until 10).sumOf { Economy.upgradeCost(it) }
        val save = GameSave(money = expected + 7)

        val result = assertIs<Shop.PurchaseResult.Success>(Shop.buyPuissance(save, count = 10))

        assertEquals(10, result.levels)
        assertEquals(expected, result.cost)
        assertEquals(10, result.save.puissanceLevel)
        assertEquals(7, result.save.money)
    }

    @Test
    fun `sans assez d'argent pour tout on achete autant de niveaux que possible`() {
        val firstFour = (0 until 4).sumOf { Economy.upgradeCost(it) }
        val save = GameSave(money = firstFour + Economy.upgradeCost(4) - 1)

        val result = assertIs<Shop.PurchaseResult.Success>(Shop.buyVitesse(save, count = 100))

        assertEquals(4, result.levels)
        assertEquals(firstFour, result.cost)
        assertEquals(4, result.save.vitesseLevel)
        assertEquals(Economy.upgradeCost(4) - 1, result.save.money)
    }

    @Test
    fun `un lot sans de quoi payer le premier niveau ne change rien`() {
        val save = GameSave(money = Economy.upgradeCost(0) - 1)
        assertIs<Shop.PurchaseResult.NotEnoughMoney>(Shop.buyPuissance(save, count = 100))
    }

    @Test
    fun `cent niveaux de haut niveau ne font pas deborder l'argent`() {
        // Au-delà du niveau 25 le prix est linéaire : 100 niveaux à partir de
        // 100 coûtent ~ 70 millions, bien au-dessus de ce qu'un Int sommé à
        // l'envers laisserait passer si on se trompait de type.
        val save = GameSave(puissanceLevel = 100, money = Int.MAX_VALUE)
        val result = assertIs<Shop.PurchaseResult.Success>(Shop.buyPuissance(save, count = 100))
        assertEquals(100, result.levels)
        assertEquals(200, result.save.puissanceLevel)
        assertEquals(Int.MAX_VALUE - result.cost, result.save.money)
        assertEquals((100 until 200).sumOf { Economy.upgradeCost(it).toLong() }.toInt(), result.cost)
    }

    @Test
    fun `en Ville chaque niveau du lot coute 20 pourcent de plus`() {
        val inCity = GameSave(currentWorld = Ville.WORLD_ID, money = 1_000_000)
        val result = assertIs<Shop.PurchaseResult.Success>(Shop.buyPuissance(inCity, count = 10))
        assertEquals((0 until 10).sumOf { Ville.price(Economy.upgradeCost(it), inCity) }, result.cost)
    }
}
