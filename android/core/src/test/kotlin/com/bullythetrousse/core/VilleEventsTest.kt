package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le calendrier doit être IDENTIQUE à celui du site : toutes les valeurs
 * attendues ci-dessous sortent du JS de ville.js (mulberry32 et
 * eventsForDay copiés tels quels, exécutés sous Node).
 */
class VilleEventsTest {
    private fun day(vararg events: Triple<String, Long, Long>) =
        events.map { (type, start, end) -> VilleEvent(VilleEventType.entries.first { it.id == type }, start, end) }

    @Test
    fun `mulberry32 reproduit le generateur du site`() {
        val rnd = VilleEvents.mulberry32(12345)
        assertEquals(0.9797282677609473, rnd(), 1e-15)
        assertEquals(0.3067522644996643, rnd(), 1e-15)
        assertEquals(0.484205421525985, rnd(), 1e-15)
    }

    @Test
    fun `le jour 0 donne le meme calendrier que le site`() {
        val expected = day(
            Triple("coupure", 2040000L, 2460000L),
            Triple("fuite", 16080000L, 17160000L),
            Triple("fuite", 20820000L, 21000000L),
            Triple("fuite", 45960000L, 46560000L),
            Triple("pluie", 75420000L, 75840000L),
            Triple("coupure", 77100000L, 77940000L),
        )
        assertEquals(expected, VilleEvents.eventsForDay(0))
    }

    @Test
    fun `un jour recent donne le meme calendrier que le site`() {
        val expected = day(
            Triple("fuite", 1790474100000L, 1790474700000L),
            Triple("pluie", 1790480580000L, 1790481360000L),
            Triple("pluie", 1790484660000L, 1790485080000L),
            Triple("pluie", 1790500380000L, 1790501460000L),
            Triple("pluie", 1790512380000L, 1790513580000L),
            Triple("canicule", 1790540940000L, 1790541300000L),
            Triple("fuite", 1790548740000L, 1790549820000L),
        )
        assertEquals(expected, VilleEvents.eventsForDay(20723))
    }

    @Test
    fun `les jours suivants aussi`() {
        assertEquals(
            day(
                Triple("coupure", 1728032100000L, 1728033300000L),
                Triple("fuite", 1728040380000L, 1728041340000L),
                Triple("canicule", 1728048240000L, 1728049020000L),
                Triple("pluie", 1728055020000L, 1728055320000L),
                Triple("coupure", 1728058020000L, 1728058200000L),
                Triple("coupure", 1728067260000L, 1728067920000L),
                Triple("canicule", 1728069360000L, 1728070140000L),
                Triple("canicule", 1728073680000L, 1728074460000L),
            ),
            VilleEvents.eventsForDay(20000),
        )
        assertEquals(
            day(
                Triple("coupure", 1790641860000L, 1790642160000L),
                Triple("coupure", 1790646420000L, 1790647200000L),
                Triple("coupure", 1790648280000L, 1790648940000L),
                Triple("fuite", 1790668620000L, 1790669400000L),
                Triple("coupure", 1790677920000L, 1790678280000L),
            ),
            VilleEvents.eventsForDay(20725),
        )
    }

    @Test
    fun `activeEvent trouve l'evenement en cours, debut inclus et fin exclue`() {
        assertEquals(VilleEventType.FUITE, VilleEvents.activeEvent(1790474100000L)?.type)
        assertEquals(VilleEventType.FUITE, VilleEvents.activeEvent(1790474699999L)?.type)
        assertNull(VilleEvents.activeEvent(1790474700000L))
        assertTrue(VilleEvents.isOutage(1790563140000L + 1000))
    }

    @Test
    fun `les evenements respectent 10 minutes de calme et restent dans leur journee`() {
        for (d in 20000L..20060L) {
            val events = VilleEvents.eventsForDay(d)
            assertTrue(events.isNotEmpty())
            events.zipWithNext().forEach { (a, b) -> assertTrue(b.startMillis - a.endMillis >= 10 * 60_000L) }
            events.forEach {
                assertTrue(it.startMillis >= d * VilleEvents.DAY_MILLIS)
                assertTrue(it.endMillis <= (d + 1) * VilleEvents.DAY_MILLIS)
                assertTrue(it.endMillis - it.startMillis in 2 * 60_000L..20 * 60_000L)
            }
        }
    }

    @Test
    fun `upcomingEvents couvre l'evenement en cours et les 5 jours suivants`() {
        val now = 1790474100000L + 60_000L
        val list = VilleEvents.upcomingEvents(now)
        assertEquals(VilleEventType.FUITE, list.first().type)
        assertTrue(list.all { it.endMillis > now && it.startMillis < now + 5 * VilleEvents.DAY_MILLIS })
        assertTrue(list.size > 20)
    }
}
