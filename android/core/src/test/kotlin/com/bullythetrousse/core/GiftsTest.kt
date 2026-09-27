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
}
