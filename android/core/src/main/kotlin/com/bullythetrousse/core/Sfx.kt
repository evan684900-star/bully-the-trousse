package com.bullythetrousse.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Les formes d'onde de `OscillatorNode.type` utilisées par le site. */
enum class Waveform { SINE, SQUARE, SAWTOOTH, TRIANGLE }

/**
 * Un bip, équivalent d'un appel à `beep(freq, dur, type, vol)` côté web.
 *
 * @param frequencyHz Hauteur du son.
 * @param durationSeconds Durée totale, fondu de sortie compris.
 * @param waveform Forme d'onde de l'oscillateur.
 * @param volume Gain de départ, de 0.0 à 1.0.
 */
data class Beep(
    val frequencyHz: Double,
    val durationSeconds: Double,
    val waveform: Waveform,
    val volume: Double,
)

/**
 * Les bruitages du jeu, portés depuis les fonctions `sfxXxx()` du site.
 *
 * Aucun n'est un fichier audio : le site les synthétise à la volée avec des
 * oscillateurs Web Audio, donc la version Android les synthétise aussi (voir
 * [Synth]) plutôt que d'embarquer des mp3 qui sonneraient différemment.
 *
 * Les bips d'une même liste démarrent TOUS en même temps (le site les
 * enchaîne sans délai) : ce sont des accords, pas des mélodies.
 */
object SfxCatalog {
    val CHARGE = listOf(Beep(300.0, 0.08, Waveform.SQUARE, 0.05))
    val LAUNCH = listOf(
        Beep(180.0, 0.25, Waveform.SAWTOOTH, 0.12),
        Beep(500.0, 0.15, Waveform.SINE, 0.08),
    )
    val LAND = listOf(Beep(90.0, 0.3, Waveform.TRIANGLE, 0.18))
    val BUY = listOf(
        Beep(700.0, 0.12, Waveform.SINE, 0.1),
        Beep(900.0, 0.15, Waveform.SINE, 0.1),
    )
    val ERROR = listOf(Beep(140.0, 0.15, Waveform.SQUARE, 0.1))
    val RECORD = listOf(
        Beep(600.0, 0.1, Waveform.SINE, 0.12),
        Beep(800.0, 0.1, Waveform.SINE, 0.12),
        Beep(1000.0, 0.2, Waveform.SINE, 0.12),
    )
    val SPACE = listOf(
        Beep(200.0, 0.5, Waveform.SINE, 0.1),
        Beep(900.0, 0.4, Waveform.SINE, 0.06),
    )
    val CRASH = listOf(
        Beep(60.0, 0.4, Waveform.SAWTOOTH, 0.2),
        Beep(40.0, 0.5, Waveform.SQUARE, 0.15),
    )

    /** Tintement de la Trousse Pièce, joué à chaque lancer. */
    val COIN_FLIP = listOf(
        Beep(900.0, 0.05, Waveform.SQUARE, 0.08),
        Beep(1200.0, 0.05, Waveform.SQUARE, 0.07),
        Beep(1500.0, 0.09, Waveform.SQUARE, 0.06),
    )

    /** Jingle plus marqué, quand le multiplicateur de la pièce se déclenche. */
    val COIN_BONUS = listOf(
        Beep(700.0, 0.08, Waveform.SINE, 0.12),
        Beep(1050.0, 0.08, Waveform.SINE, 0.12),
        Beep(1400.0, 0.14, Waveform.SINE, 0.12),
    )
}

/**
 * Synthétise les bruitages en PCM 16 bits mono, l'équivalent de ce que font
 * `OscillatorNode` + `GainNode` côté web.
 */
object Synth {
    /** Le gain visé en fin de fondu, comme `exponentialRampToValueAtTime(0.001, ...)`. */
    const val FADE_FLOOR = 0.001

    /**
     * Valeur de l'oscillateur à la phase [phase], exprimée en tours (0.0 à
     * 1.0 = un cycle complet). Reproduit les formes d'onde de Web Audio.
     */
    fun sample(waveform: Waveform, phase: Double): Double {
        val t = phase - kotlin.math.floor(phase)
        return when (waveform) {
            Waveform.SINE -> sin(2 * PI * t)
            // Web Audio place la transition à la moitié du cycle.
            Waveform.SQUARE -> if (t < 0.5) 1.0 else -1.0
            // Rampe montante de -1 à 1 sur le cycle.
            Waveform.SAWTOOTH -> 2.0 * t - 1.0
            // Montée sur la première moitié, descente sur la seconde.
            Waveform.TRIANGLE -> 1.0 - 4.0 * abs(t - 0.5)
        }
    }

    /**
     * Gain à l'instant [elapsedSeconds] d'un bip de volume [volume] et de
     * durée [durationSeconds].
     *
     * `exponentialRampToValueAtTime` interpole géométriquement entre le gain
     * de départ et [FADE_FLOOR] — pas linéairement, d'où la puissance.
     */
    fun gainAt(volume: Double, durationSeconds: Double, elapsedSeconds: Double): Double {
        if (durationSeconds <= 0.0 || volume <= 0.0) return 0.0
        val progress = (elapsedSeconds / durationSeconds).coerceIn(0.0, 1.0)
        return volume * (FADE_FLOOR / volume).pow(progress)
    }

    /**
     * Rend [beeps] en un seul tampon PCM mono : les bips démarrent ensemble et
     * sont additionnés, la longueur est celle du plus long.
     *
     * Le mélange est écrêté à l'amplitude maximale d'un entier 16 bits : la
     * somme des volumes du catalogue reste bien en dessous, mais mieux vaut
     * saturer que déborder silencieusement.
     */
    fun render(beeps: List<Beep>, sampleRate: Int = SAMPLE_RATE): ShortArray {
        if (beeps.isEmpty()) return ShortArray(0)
        val longest = beeps.maxOf { it.durationSeconds }
        val frames = (longest * sampleRate).roundToInt()
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            val t = i.toDouble() / sampleRate
            var mixed = 0.0
            for (beep in beeps) {
                if (t >= beep.durationSeconds) continue
                mixed += sample(beep.waveform, beep.frequencyHz * t) *
                    gainAt(beep.volume, beep.durationSeconds, t)
            }
            out[i] = (mixed.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).roundToInt().toShort()
        }
        return out
    }

    /** Fréquence d'échantillonnage : 44,1 kHz, supportée partout. */
    const val SAMPLE_RATE = 44100
}
