package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PresenceTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `sans horodatage le statut est inconnu, pas hors ligne`() {
        assertEquals(PresenceStatus.Unknown, Presence.of(null, now))
    }

    @Test
    fun `vu il y a moins de deux minutes vaut en ligne`() {
        assertEquals(PresenceStatus.Online, Presence.of(now, now))
        assertEquals(PresenceStatus.Online, Presence.of(now - 119_000L, now))
        // Pile sur le seuil : encore en ligne (<=, comme côté web).
        assertEquals(PresenceStatus.Online, Presence.of(now - Presence.ONLINE_THRESHOLD_MILLIS, now))
    }

    @Test
    fun `au dela du seuil on bascule en minutes puis heures puis jours`() {
        assertEquals(PresenceStatus.MinutesAgo(5), Presence.of(now - 5 * 60_000L, now))
        assertEquals(PresenceStatus.MinutesAgo(59), Presence.of(now - 59 * 60_000L, now))
        assertEquals(PresenceStatus.HoursAgo(1), Presence.of(now - 60 * 60_000L, now))
        assertEquals(PresenceStatus.HoursAgo(23), Presence.of(now - 23 * 3_600_000L, now))
        assertEquals(PresenceStatus.DaysAgo(1), Presence.of(now - 24 * 3_600_000L, now))
        assertEquals(PresenceStatus.DaysAgo(3), Presence.of(now - 3 * 24 * 3_600_000L, now))
    }
}
