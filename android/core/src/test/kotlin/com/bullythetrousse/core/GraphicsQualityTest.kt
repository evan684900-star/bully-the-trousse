package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsQualityTest {
    @Test
    fun `un identifiant inconnu retombe sur le niveau par defaut`() {
        // Cas réel : une sauvegarde écrite par une version plus récente de
        // l'app, avec un niveau qui n'existe pas encore ici.
        assertEquals(GraphicsQuality.DEFAULT, GraphicsQuality.fromId("ultra"))
        assertEquals(GraphicsQuality.DEFAULT, GraphicsQuality.fromId(""))
    }

    @Test
    fun `chaque identifiant se relit tel quel`() {
        for (quality in GraphicsQuality.entries) {
            assertEquals(quality, GraphicsQuality.fromId(quality.id))
        }
    }

    @Test
    fun `le bouton fait defiler les trois niveaux en boucle`() {
        assertEquals(GraphicsQuality.MEDIUM, GraphicsQuality.next(GraphicsQuality.LOW))
        assertEquals(GraphicsQuality.HIGH, GraphicsQuality.next(GraphicsQuality.MEDIUM))
        assertEquals(GraphicsQuality.LOW, GraphicsQuality.next(GraphicsQuality.HIGH))
    }

    @Test
    fun `monter d un niveau ne retire jamais de detail`() {
        val levels = GraphicsQuality.entries.map { it.profile }
        for (i in 0 until levels.size - 1) {
            val lower = levels[i]
            val higher = levels[i + 1]
            assertTrue(higher.particleScale >= lower.particleScale)
            assertTrue(higher.maxParticles >= lower.maxParticles)
            assertTrue(higher.cloudLayers >= lower.cloudLayers)
            assertTrue(higher.trailPoints >= lower.trailPoints)
            assertTrue(higher.trailPasses >= lower.trailPasses)
        }
    }

    @Test
    fun `le niveau bas coupe bien les effets decoratifs`() {
        assertFalse(GraphicsQuality.LOW.profile.ambientEffects)
        assertFalse(GraphicsQuality.LOW.profile.vignette)
        assertTrue(GraphicsQuality.MEDIUM.profile.ambientEffects)
    }

    @Test
    fun `le reglage survit a un aller-retour par la sauvegarde`() {
        val save = GameSave(graphicsQuality = GraphicsQuality.LOW.id)
        val reloaded = SaveCodec.decodeOrDefault(SaveCodec.encode(save))
        assertEquals(GraphicsQuality.LOW, GraphicsQuality.fromId(reloaded.graphicsQuality))
    }

    @Test
    fun `une sauvegarde ecrite avant ce reglage reste lisible`() {
        // Le champ est absent du JSON : il doit prendre sa valeur par défaut
        // au lieu de faire échouer la lecture (ignoreUnknownKeys/defaults).
        val legacy = """{"money":1200,"equippedSkin":"doree"}"""
        val save = SaveCodec.decodeOrDefault(legacy)
        assertEquals(1200, save.money)
        assertEquals(GraphicsQuality.DEFAULT.id, save.graphicsQuality)
    }
}
