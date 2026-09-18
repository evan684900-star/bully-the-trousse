package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudSaveSyncTest {
    @Test
    fun `une copie distante recue apres le dernier persist local est appliquee`() {
        assertTrue(CloudSaveSync.shouldApplyRemoteSave(lastLocalPersistAtMillis = 1000L, remoteReceivedAtMillis = 2000L))
    }

    @Test
    fun `une copie distante recue avant le dernier persist local est ignoree`() {
        assertFalse(CloudSaveSync.shouldApplyRemoteSave(lastLocalPersistAtMillis = 2000L, remoteReceivedAtMillis = 1000L))
    }

    @Test
    fun `une copie recue exactement au meme instant est appliquee (cas limite)`() {
        assertTrue(CloudSaveSync.shouldApplyRemoteSave(lastLocalPersistAtMillis = 1500L, remoteReceivedAtMillis = 1500L))
    }

    @Test
    fun `aucun persist local encore (0) laisse toute copie distante s'appliquer`() {
        assertTrue(CloudSaveSync.shouldApplyRemoteSave(lastLocalPersistAtMillis = 0L, remoteReceivedAtMillis = 1L))
    }
}
