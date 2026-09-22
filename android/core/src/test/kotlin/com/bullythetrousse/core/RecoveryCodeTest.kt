package com.bullythetrousse.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoveryCodeTest {
    @Test
    fun `la derivation du compte est identique a celle du site`() {
        // Si ces deux chaînes changent, le téléphone ouvre un compte DIFFÉRENT
        // de celui du site avec le même code, sans le moindre message d'erreur.
        val code = "1234567890123456"
        assertEquals("c1234567890123456@players.bullythetrousse.app", RecoveryCode.emailFor(code))
        assertEquals("bt-1234567890123456", RecoveryCode.passwordFor(code))
    }

    @Test
    fun `le code s affiche par groupes de quatre`() {
        assertEquals("1234 5678 9012 3456", RecoveryCode.format("1234567890123456"))
    }

    @Test
    fun `un code colle avec des espaces ou des tirets reste utilisable`() {
        assertEquals("1234567890123456", RecoveryCode.normalize("1234 5678 9012 3456"))
        assertEquals("1234567890123456", RecoveryCode.normalize("1234-5678-9012-3456"))
        assertEquals("1234567890123456", RecoveryCode.normalize(" 1234567890123456 "))
    }

    @Test
    fun `un code doit faire exactement seize chiffres`() {
        assertTrue(RecoveryCode.isValid("1234567890123456"))
        assertFalse(RecoveryCode.isValid("123456789012345"))
        assertFalse(RecoveryCode.isValid("12345678901234567"))
        assertFalse(RecoveryCode.isValid("123456789012345a"))
        assertFalse(RecoveryCode.isValid(""))
    }

    @Test
    fun `un code genere est toujours valide`() {
        val random = Random(1234)
        repeat(50) {
            val code = RecoveryCode.generate(random)
            assertTrue(RecoveryCode.isValid(code), "code invalide : $code")
        }
    }

    @Test
    fun `un code normalise puis formate redonne la saisie propre`() {
        val typed = "9876 5432 1098 7654"
        val code = RecoveryCode.normalize(typed)
        assertTrue(RecoveryCode.isValid(code))
        assertEquals(typed, RecoveryCode.format(code))
    }
}
