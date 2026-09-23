package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayTimeTest {
    @Test
    fun `la sauvegarde n est ecrite que toutes les 30 secondes`() {
        val tracker = PlayTimeTracker(0L)
        repeat(29) { assertFalse(tracker.tick(musicPlaying = true)) }
        assertTrue(tracker.tick(musicPlaying = true))
    }

    @Test
    fun `le report ajoute le temps accumule puis repart de zero`() {
        val tracker = PlayTimeTracker(0L)
        repeat(12) { tracker.tick(musicPlaying = false) }
        val s = tracker.flushInto(GameSave(playTime = 100L))
        assertEquals(112L, s.playTime)
        assertEquals(0, tracker.pendingSeconds)
        assertEquals(112L, tracker.flushInto(s).playTime)
    }

    @Test
    fun `une coupure de musique remet la serie a zero`() {
        val tracker = PlayTimeTracker(initialMusicStreak = 500L)
        tracker.tick(musicPlaying = true)
        assertEquals(501L, tracker.musicStreak)
        tracker.tick(musicPlaying = false)
        assertEquals(0L, tracker.musicStreak)
        tracker.tick(musicPlaying = true)
        assertEquals(1L, tracker.flushInto(GameSave()).musicListenSeconds)
    }
}
