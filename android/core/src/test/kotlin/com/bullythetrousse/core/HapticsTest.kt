package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HapticsTest {
    @Test
    fun `chaque evenement a un motif`() {
        for (event in HapticEvent.entries) {
            val pattern = HapticCatalog.forEvent(event)
            assertTrue(pattern.timingsMillis.isNotEmpty(), "$event")
        }
    }

    @Test
    fun `un motif vide est refuse`() {
        assertFailsWith<IllegalArgumentException> { HapticPattern(emptyList()) }
    }

    @Test
    fun `une duree negative est refusee`() {
        assertFailsWith<IllegalArgumentException> { HapticPattern(listOf(-1L)) }
    }

    @Test
    fun `la duree totale additionne tous les segments`() {
        val pattern = HapticPattern(listOf(10L, 20L, 30L))
        assertEquals(60L, pattern.totalMillis)
    }

    @Test
    fun `le crash est plus marque que l atterrissage normal`() {
        assertTrue(HapticCatalog.CRASH.totalMillis > HapticCatalog.LAND.totalMillis)
    }

    @Test
    fun `le succes dure plus longtemps que le record`() {
        assertTrue(HapticCatalog.ACHIEVEMENT.totalMillis > HapticCatalog.RECORD.totalMillis)
    }

    @Test
    fun `le boost vampire est attenue`() {
        val amplitude = HapticCatalog.VAMPIRE_BOOST.amplitudes?.single()
        assertTrue(amplitude != null && amplitude < 128)
    }

    @Test
    fun `l erreur a deux pulsations separees par un silence`() {
        // [vibration, silence, vibration] : trois segments, indices 0 et 2 vibrent.
        assertEquals(3, HapticCatalog.ERROR.timingsMillis.size)
    }
}
