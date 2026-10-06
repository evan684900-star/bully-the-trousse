package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ThrowSpeedTest {
    @Test
    fun `chaque appui passe a la vitesse suivante et reboucle`() {
        assertEquals(2, ThrowSpeed.next(1))
        assertEquals(4, ThrowSpeed.next(2))
        assertEquals(10, ThrowSpeed.next(4))
        assertEquals(1, ThrowSpeed.next(10))
    }

    @Test
    fun `une valeur inconnue relue du stockage repart a x1`() {
        assertEquals(1, ThrowSpeed.sanitize(0))
        assertEquals(1, ThrowSpeed.sanitize(3))
        assertEquals(4, ThrowSpeed.sanitize(4))
        assertEquals(10, ThrowSpeed.sanitize(10))
        assertEquals(2, ThrowSpeed.next(ThrowSpeed.sanitize(99)))
    }
}
