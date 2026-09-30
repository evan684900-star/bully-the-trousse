package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GiftsTest {
    @Test
    fun `un montant illisible nul ou negatif est refuse`() {
        for (input in listOf("", "abc", "0", "-5", "0.4")) {
            assertEquals(GiftCheck.InvalidAmount, Gifts.check(input, 1000, 0, 100_000), input)
        }
    }

    @Test
    fun `la partie decimale est tronquee`() {
        assertEquals(GiftCheck.Ok(12), Gifts.check("12.9", 1000, 0, 100_000))
    }

    @Test
    fun `on ne peut pas offrir plus que ce qu on a`() {
        assertEquals(GiftCheck.NotEnoughMoney, Gifts.check("1001", 1000, 0, 100_000))
        assertEquals(GiftCheck.Ok(1000), Gifts.check("1000", 1000, 0, 100_000))
    }

    @Test
    fun `le delai de 30 secondes s applique a tous les envois`() {
        assertEquals(GiftCheck.Cooldown(30), Gifts.check("5", 100, lastSentAtMillis = 1_000, nowMillis = 1_001))
        assertEquals(GiftCheck.Cooldown(1), Gifts.check("5", 100, lastSentAtMillis = 1_000, nowMillis = 30_500))
        assertEquals(GiftCheck.Ok(5), Gifts.check("5", 100, lastSentAtMillis = 1_000, nowMillis = 31_000))
    }

    @Test
    fun `les boutons rapides ne depassent jamais l argent disponible`() {
        assertEquals(110, Gifts.quickAdd("10", 100, 5000))
        assertEquals(1000, Gifts.quickAdd("", 1000, 5000))
        assertEquals(300, Gifts.quickAdd("250", 100, 300))
        assertEquals(0, Gifts.max(-4))
    }

    @Test
    fun `les cadeaux d un meme expediteur sont fusionnes`() {
        val gifts = listOf(
            IncomingGift("a", "Léa", 10),
            IncomingGift("b", "Tom", 5),
            IncomingGift("a", "Léa", 30),
            IncomingGift("c", "Zoé", 0),
        )
        assertEquals(listOf("Léa" to 40, "Tom" to 5), Gifts.mergeBySender(gifts))
        assertEquals(45, Gifts.total(gifts))
    }

    @Test
    fun `les lignes des cadeaux gardent l arc-en-ciel des expediteurs Android`() {
        val lines = Gifts.linesBySender(
            listOf(
                IncomingGift("a", "Robot", 10, senderAndroid = true),
                IncomingGift("b", "Web", 5),
                IncomingGift("a", "Robot", 15),
                IncomingGift("c", "Rien", 0, senderAndroid = true),
            ),
        )
        assertEquals(listOf(GiftLine("Robot", 25, true), GiftLine("Web", 5, false)), lines)
    }

    @Test
    fun `un gros cadeau passe sans plafond et sans faire deborder l argent`() {
        // Plus de plafond à 1 000 000 : 5 millions passent tels quels.
        assertEquals(GiftCheck.Ok(5_000_000), Gifts.check("5000000", money = 6_000_000, lastSentAtMillis = 0, nowMillis = 100_000))
        assertEquals(7_000_000, Gifts.credit(2_000_000, 5_000_000))
        // Au-delà de l'Int, l'argent reste au maximum au lieu de passer en négatif.
        assertEquals(Int.MAX_VALUE, Gifts.credit(Int.MAX_VALUE - 10, 2_000_000_000))
        val gifts = listOf(
            IncomingGift("a", "A", 2_000_000_000),
            IncomingGift("a", "A", 2_000_000_000),
            IncomingGift("b", "B", -5),
        )
        assertEquals(Int.MAX_VALUE, Gifts.total(gifts))
        assertEquals(listOf(GiftLine("A", Int.MAX_VALUE, false)), Gifts.linesBySender(gifts))
    }
}
