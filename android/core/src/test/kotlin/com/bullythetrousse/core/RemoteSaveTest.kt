package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteSaveTest {
    @Test
    fun `le temps de jeu seul ne compte pas comme un changement`() {
        val a = GameSave(money = 10, playTime = 100L, musicListenSeconds = 5L)
        assertFalse(CloudSaveSync.differsMeaningfully(a, a.copy(playTime = 900L, musicListenSeconds = 0L)))
        assertTrue(CloudSaveSync.differsMeaningfully(a, a.copy(money = 11)))
    }

    @Test
    fun `la fusion garde le temps de jeu le plus avance`() {
        val merged = CloudSaveSync.mergeRemote(GameSave(money = 50, playTime = 10L), GameSave(money = 1, playTime = 500L))
        assertEquals(50, merged.money)
        assertEquals(500L, merged.playTime)
        assertEquals(700L, CloudSaveSync.mergeRemote(GameSave(playTime = 700L), GameSave(playTime = 5L)).playTime)
    }
}
