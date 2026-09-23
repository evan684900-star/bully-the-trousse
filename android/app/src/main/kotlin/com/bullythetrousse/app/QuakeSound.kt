package com.bullythetrousse.app

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Le grondement du tremblement de terre de la cinématique du volcan
 * (`<audio id="sfx-quake">` côté web). Seul bruitage du jeu à être un vrai
 * fichier plutôt qu'une synthèse : c'est un bruit long et texturé, que des
 * oscillateurs ne rendraient pas.
 *
 * Toutes les opérations sont tolérantes à l'échec, comme les `try/catch` du
 * site : un son qui ne part pas ne doit jamais interrompre la cinématique.
 */
class QuakeSound(private val context: Context) {
    private var player: MediaPlayer? = null
    private var volume = MAX_VOLUME.toFloat()

    /** Démarre le grondement depuis le début, au volume maximal. */
    fun start() {
        stop()
        runCatching {
            volume = MAX_VOLUME.toFloat()
            player = MediaPlayer.create(context, R.raw.sfx_quake)?.apply {
                isLooping = true
                setVolume(volume, volume)
                start()
            }
        }
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        volume = clamped
        runCatching { player?.setVolume(clamped, clamped) }
    }

    /** Baisse le volume d'un point par seconde (`quakeAudio.volume - dt`). */
    fun fadeOut(dt: Double) {
        if (player == null) return
        setVolume(volume - dt.toFloat())
    }

    fun stop() {
        val current = player ?: return
        player = null
        runCatching {
            if (current.isPlaying) current.stop()
            current.release()
        }
    }

    companion object {
        /** `quakeAudio.volume = 0.9` côté site. */
        const val MAX_VOLUME = 0.9
    }
}

/** Crée le lecteur pour la durée de la cinématique et le libère à la sortie. */
@Composable
fun rememberQuakeSound(): QuakeSound {
    val context = LocalContext.current
    val sound = remember(context) { QuakeSound(context) }
    DisposableEffect(sound) {
        onDispose { sound.stop() }
    }
    return sound
}
