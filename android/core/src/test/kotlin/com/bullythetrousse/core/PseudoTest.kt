package com.bullythetrousse.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PseudoTest {
    @Test
    fun `le pseudo par defaut suit le format du site`() {
        val random = Random(7)
        repeat(50) {
            val pseudo = Pseudo.generateDefault(random)
            assertTrue(pseudo.startsWith("Trousse-"), pseudo)
            val digits = pseudo.removePrefix("Trousse-")
            assertEquals(4, digits.length, pseudo)
            assertTrue(digits.all { it.isDigit() }, pseudo)
            // 1000..9999 côté web : jamais de zéro en tête, jamais 5 chiffres.
            assertTrue(digits.toInt() in 1000..9999, pseudo)
        }
    }

    @Test
    fun `une saisie est rognee et plafonnee a 24 caracteres`() {
        assertEquals("Evan", Pseudo.sanitize("  Evan  "))
        val long = "a".repeat(40)
        assertEquals(24, Pseudo.sanitize(long)?.length)
    }

    @Test
    fun `une saisie vide ne remplace pas le pseudo courant`() {
        assertNull(Pseudo.sanitize(""))
        assertNull(Pseudo.sanitize("    "))
    }
}
