package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretsTest {
    @Test
    fun `les reponses du calcul suivent 1 5 1 5 et les operations sont justes`() {
        assertEquals(listOf(1, 5, 1, 5), SecretMath.QUESTIONS.map { it.answer })
        assertEquals(1, 38 * 17 - 645)
        assertEquals(5, 47 * 23 - 1076)
        assertEquals(1, 63 * 29 - 1826)
        assertEquals(5, 84 * 19 - 1591)
    }

    @Test
    fun `une reponse est comparee comme un entier`() {
        assertTrue(SecretMath.isCorrect(0, " 1 "))
        assertFalse(SecretMath.isCorrect(0, "5"))
        assertFalse(SecretMath.isCorrect(0, ""))
        assertFalse(SecretMath.isCorrect(9, "1"))
    }

    @Test
    fun `le compte a rebours arrondit au superieur`() {
        assertEquals(5, SecretMath.secondsLeft(5000))
        assertEquals(1, SecretMath.secondsLeft(1))
        assertEquals(0, SecretMath.secondsLeft(-20))
    }

    @Test
    fun `la sequence complete debloque le secret`() {
        val seq = SecretSequence()
        val steps = SecretSequence.SEQUENCE.map { seq.step(it) }
        assertEquals(List(9) { false } + true, steps)
        assertEquals(0, seq.progress)
    }

    @Test
    fun `un mauvais geste repart de zero sauf s il recommence la sequence`() {
        val seq = SecretSequence()
        seq.step(SecretGesture.DOWN)
        seq.step(SecretGesture.DOWN)
        seq.step(SecretGesture.LEFT)
        assertEquals(0, seq.progress)
        seq.step(SecretGesture.DOWN)
        seq.step(SecretGesture.UP) // attendu : ↓
        assertEquals(0, seq.progress)
        seq.step(SecretGesture.DOWN)
        seq.step(SecretGesture.DOWN)
        seq.step(SecretGesture.DOWN) // mauvais, mais c'est le premier pas
        assertEquals(1, seq.progress)
    }

    @Test
    fun `les gestes sont classes comme sur le site`() {
        assertEquals(SecretGesture.TAP, SecretSequence.classify(3f, 4f, 120))
        assertNull(SecretSequence.classify(3f, 4f, 900))
        assertNull(SecretSequence.classify(20f, 0f, 100))
        assertEquals(SecretGesture.RIGHT, SecretSequence.classify(80f, 10f, 200))
        assertEquals(SecretGesture.UP, SecretSequence.classify(5f, -60f, 200))
    }

    @Test
    fun `le drapeau secret n est ajoute qu une fois`() {
        val once = Secrets.unlock(GameSave())
        assertEquals(once, Secrets.unlock(once))
        assertTrue(Achievements.newlyUnlocked(once).any { it.id == "secretTrouve" })
    }

    @Test
    fun `le caca se touche sur l arbre numero un et nulle part ailleurs`() {
        val x = CourDecor.poopEggScreenX(0.0)
        assertTrue(CourDecor.hitsPoopEgg(x, 400.0 - 55.0, 0.0, 400.0))
        assertTrue(CourDecor.hitsPoopEgg(x + 10.0, 400.0 - 55.0, 0.0, 400.0))
        assertFalse(CourDecor.hitsPoopEgg(x + 40.0, 400.0 - 55.0, 0.0, 400.0))
        assertFalse(CourDecor.hitsPoopEgg(x, 400.0, 0.0, 400.0))
    }
}
