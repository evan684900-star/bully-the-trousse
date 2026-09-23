package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    @Test
    fun `le sinus fait un cycle complet`() {
        assertEquals(0.0, Synth.sample(Waveform.SINE, 0.0), 1e-9)
        assertEquals(1.0, Synth.sample(Waveform.SINE, 0.25), 1e-9)
        assertEquals(0.0, Synth.sample(Waveform.SINE, 0.5), 1e-9)
        assertEquals(-1.0, Synth.sample(Waveform.SINE, 0.75), 1e-9)
    }

    @Test
    fun `le carre bascule a la moitie du cycle`() {
        assertEquals(1.0, Synth.sample(Waveform.SQUARE, 0.0))
        assertEquals(1.0, Synth.sample(Waveform.SQUARE, 0.49))
        assertEquals(-1.0, Synth.sample(Waveform.SQUARE, 0.5))
        assertEquals(-1.0, Synth.sample(Waveform.SQUARE, 0.99))
    }

    @Test
    fun `la dent de scie monte de moins un a un`() {
        assertEquals(-1.0, Synth.sample(Waveform.SAWTOOTH, 0.0), 1e-9)
        assertEquals(0.0, Synth.sample(Waveform.SAWTOOTH, 0.5), 1e-9)
        assertTrue(Synth.sample(Waveform.SAWTOOTH, 0.999) > 0.99)
    }

    @Test
    fun `le triangle culmine au milieu du cycle`() {
        assertEquals(-1.0, Synth.sample(Waveform.TRIANGLE, 0.0), 1e-9)
        assertEquals(1.0, Synth.sample(Waveform.TRIANGLE, 0.5), 1e-9)
        assertEquals(0.0, Synth.sample(Waveform.TRIANGLE, 0.25), 1e-9)
    }

    @Test
    fun `les formes d onde se repetent a chaque cycle`() {
        for (w in Waveform.entries) {
            assertEquals(Synth.sample(w, 0.3), Synth.sample(w, 3.3), 1e-9, "$w")
        }
    }

    @Test
    fun `le gain part du volume et descend jusqu au plancher`() {
        assertEquals(0.12, Synth.gainAt(0.12, 0.25, 0.0), 1e-9)
        assertEquals(Synth.FADE_FLOOR, Synth.gainAt(0.12, 0.25, 0.25), 1e-9)
    }

    @Test
    fun `le fondu est decroissant sur toute la duree`() {
        var previous = Double.MAX_VALUE
        for (i in 0..20) {
            val gain = Synth.gainAt(0.18, 0.3, 0.3 * i / 20.0)
            assertTrue(gain < previous, "gain non décroissant à l'étape $i")
            previous = gain
        }
    }

    @Test
    fun `le gain est plafonne au dela de la duree`() {
        assertEquals(Synth.FADE_FLOOR, Synth.gainAt(0.1, 0.2, 5.0), 1e-9)
    }

    @Test
    fun `le rendu a la longueur du bip le plus long`() {
        val pcm = Synth.render(SfxCatalog.LAUNCH, sampleRate = 1000)
        // LAUNCH = 0.25s et 0.15s -> 250 échantillons à 1 kHz.
        assertEquals(250, pcm.size)
    }

    @Test
    fun `le rendu d un catalogue vide est vide`() {
        assertEquals(0, Synth.render(emptyList()).size)
    }

    @Test
    fun `le rendu ne sature jamais le format 16 bits`() {
        for (sfx in listOf(
            SfxCatalog.CHARGE, SfxCatalog.LAUNCH, SfxCatalog.LAND, SfxCatalog.BUY,
            SfxCatalog.ERROR, SfxCatalog.RECORD, SfxCatalog.SPACE, SfxCatalog.CRASH,
            SfxCatalog.COIN_FLIP, SfxCatalog.COIN_BONUS,
        )) {
            val pcm = Synth.render(sfx, sampleRate = 8000)
            assertTrue(pcm.isNotEmpty())
            // Les volumes du catalogue restent loin du plafond : aucun
            // échantillon ne doit atteindre l'écrêtage.
            assertTrue(pcm.all { abs(it.toInt()) < Short.MAX_VALUE.toInt() })
        }
    }

    @Test
    fun `le son s eteint a la fin du rendu`() {
        val pcm = Synth.render(SfxCatalog.LAND, sampleRate = 8000)
        val debut = pcm.take(80).maxOf { abs(it.toInt()) }
        val fin = pcm.takeLast(80).maxOf { abs(it.toInt()) }
        assertTrue(fin < debut / 10, "la fin ($fin) devrait être bien plus faible que le début ($debut)")
    }
}
