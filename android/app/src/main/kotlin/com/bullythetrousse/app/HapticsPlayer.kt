package com.bullythetrousse.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.bullythetrousse.core.HapticCatalog
import com.bullythetrousse.core.HapticEvent
import com.bullythetrousse.core.HapticPattern

/**
 * Joue les vibrations du jeu (voir [HapticCatalog], `:core`) — une pure
 * addition Android, sans équivalent côté site.
 *
 * Chaque appel est tolérant à l'échec : ni le vibreur absent (certaines
 * tablettes), ni l'utilisateur qui a coupé les vibrations système, ne
 * doivent perturber la partie.
 */
class HapticsPlayer(context: Context) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    fun play(event: HapticEvent) {
        play(HapticCatalog.forEvent(event))
    }

    /**
     * @param repeatFromIndex Index du segment où reboucler indéfiniment
     *   (-1 = pas de répétition). Sert au grondement continu du tremblement
     *   de terre (voir [HapticEvent.QUAKE]) : la vibration tourne en boucle
     *   jusqu'au prochain [play]/[cancel], pas de coup unique à répéter à la
     *   main image par image.
     */
    fun play(pattern: HapticPattern, repeatFromIndex: Int = -1) {
        val v = vibrator ?: return
        runCatching {
            // Un seul segment sans amplitude spécifique ni répétition :
            // createOneShot est strictement équivalent à createWaveform mais
            // plus direct.
            val effect = if (pattern.timingsMillis.size == 1 && pattern.amplitudes == null && repeatFromIndex < 0) {
                VibrationEffect.createOneShot(
                    pattern.timingsMillis[0].coerceAtLeast(1L),
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
            } else {
                VibrationEffect.createWaveform(
                    pattern.timingsMillis.toLongArray(),
                    amplitudesFor(pattern),
                    repeatFromIndex,
                )
            }
            v.cancel()
            v.vibrate(effect)
        }
    }

    /** `createWaveform` veut une amplitude par segment, silences compris (0
     *  pour un silence) ; [HapticPattern.amplitudes] n'en donne qu'une par
     *  segment de VIBRATION (indices pairs), donc il faut intercaler les 0. */
    private fun amplitudesFor(pattern: HapticPattern): IntArray {
        val given = pattern.amplitudes
        return IntArray(pattern.timingsMillis.size) { i ->
            when {
                i % 2 == 1 -> 0 // segment de silence
                given != null -> given.getOrElse(i / 2) { VibrationEffect.DEFAULT_AMPLITUDE }
                else -> VibrationEffect.DEFAULT_AMPLITUDE
            }
        }
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
    }
}

/**
 * Le lecteur de vibrations de la partie en cours, fourni depuis MainActivity.
 * Le lecteur par défaut n'a pas de [Context] : il ne vibre jamais, ce qui
 * évite d'avoir à en fournir un dans les prévisualisations Compose.
 */
val LocalHaptics = staticCompositionLocalOf<HapticsPlayer?> { null }

@Composable
fun rememberHapticsPlayer(): HapticsPlayer {
    val context = LocalContext.current
    return remember(context) { HapticsPlayer(context) }
}

/** Joue [event] sans effet si aucun lecteur n'est fourni (voir [LocalHaptics]). */
fun HapticsPlayer?.play(event: HapticEvent) {
    this?.play(event)
}
