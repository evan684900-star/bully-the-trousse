package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinEarningsTest {
    private val piece = Skins.find("piece")
    private val vampire = Skins.find("vampire")
    private val classique = Skins.find("classique")

    @Test
    fun `un skin normal ne change rien au gain de base`() {
        val result = SkinEarnings.apply(baseEarn = 100, skin = classique, distanceMeters = 50.0) { 0.0 }
        assertEquals(100, result.finalEarn)
        assertNull(result.coinMultiplier)
        assertEquals(0, result.vampireStolen)
    }

    @Test
    fun `la trousse piece ne se declenche pas au-dela de la chance de 50 pourcent`() {
        // premier random() = 0.5 -> pile la limite, non déclenché (random() < CHANCE, pas <=)
        val result = SkinEarnings.apply(baseEarn = 100, skin = piece, distanceMeters = 0.0) { 0.5 }
        assertNull(result.coinMultiplier)
        assertEquals(100, result.finalEarn)
    }

    @Test
    fun `la trousse piece applique le multiplicateur x1,6 sur le premier palier`() {
        // premier random() < 0.5 -> déclenché ; deuxième random() = 0.0 -> tombe dans le 1er palier (x1.6)
        var call = 0
        val result = SkinEarnings.apply(baseEarn = 100, skin = piece, distanceMeters = 0.0) {
            call++
            if (call == 1) 0.0 else 0.0
        }
        assertEquals(1.6, result.coinMultiplier)
        assertEquals(160, result.finalEarn) // round(100 * 1.6)
        assertFalse(result.hasJackpot)
    }

    @Test
    fun `un multiplicateur de 5 ou plus declenche le jackpot`() {
        var call = 0
        // deuxième random() juste au-dessus de 0.60+0.29+0.10 = 0.99 -> palier x5
        val result = SkinEarnings.apply(baseEarn = 100, skin = piece, distanceMeters = 0.0) {
            call++
            if (call == 1) 0.0 else 0.991
        }
        assertEquals(5.0, result.coinMultiplier)
        assertTrue(result.hasJackpot)
    }

    @Test
    fun `la trousse vampire vole un pourcentage minimum de 5 pourcent meme sur un lancer court`() {
        val result = SkinEarnings.apply(baseEarn = 100, skin = vampire, distanceMeters = 0.0) { 1.0 }
        assertEquals(5.0, result.vampireStealPct)
        assertEquals(5, result.vampireStolen) // round(100 * 5%)
        assertEquals(95, result.finalEarn)
    }

    @Test
    fun `le pourcentage vole augmente avec la distance jusqu'au plafond de 40 pourcent`() {
        // 5 + (500/100)*1 = 10%
        val mid = SkinEarnings.apply(baseEarn = 1000, skin = vampire, distanceMeters = 500.0) { 1.0 }
        assertEquals(10.0, mid.vampireStealPct)

        // 5 + (10000/100)*1 = 105% -> plafonné à 40%
        val capped = SkinEarnings.apply(baseEarn = 1000, skin = vampire, distanceMeters = 10000.0) { 1.0 }
        assertEquals(40.0, capped.vampireStealPct)
    }

    @Test
    fun `le multiplicateur piece s'applique avant le vol vampire (skin theorique combinant les deux)`() {
        // Un skin fictif avec isCoin ET isVampire pour vérifier l'ordre exact des opérations.
        val both = piece.copy(isVampire = true)
        var call = 0
        val result = SkinEarnings.apply(baseEarn = 100, skin = both, distanceMeters = 0.0) {
            call++
            if (call == 1) 0.0 else 0.0 // coin: déclenché, x1.6 -> earn=160
        }
        // vol vampire à 5% sur 160, pas sur 100
        assertEquals(160, (result.finalEarn + result.vampireStolen))
        assertEquals(8, result.vampireStolen) // round(160 * 5%)
        assertEquals(152, result.finalEarn)
    }
}
