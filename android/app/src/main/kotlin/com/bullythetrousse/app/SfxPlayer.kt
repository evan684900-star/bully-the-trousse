package com.bullythetrousse.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.bullythetrousse.core.Beep
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.Synth
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Joue les bruitages synthétisés du jeu (voir [SfxCatalog], `:core`).
 *
 * Le site les fabrique à la volée avec des oscillateurs Web Audio ; ici, le
 * PCM de chaque bruitage est rendu UNE fois puis mis en cache, et rejoué via
 * un [AudioTrack] jetable. Rendre 0,3 s de son à 44,1 kHz à chaque tir se
 * verrait sur un téléphone d'entrée de gamme, d'où le cache.
 *
 * La lecture part sur des fils à part : attendre la fin d'un son sur le fil
 * principal ferait tomber des images.
 */
class SfxPlayer {
    private val cache = ConcurrentHashMap<List<Beep>, ShortArray>()
    // Plusieurs fils, pas un seul : le site joue certains bruitages EN MÊME
    // TEMPS (sfxLaunch() puis sfxCoinFlip() au même clic, sfxLaunch() +
    // sfxCrash() à l'éruption). Un fil unique les mettrait à la queue leu leu,
    // le second ne démarrant qu'après la fin du premier.
    //
    // `by lazy` : rien n'est créé tant qu'aucun son n'est joué, pour que le
    // lecteur par défaut du CompositionLocal ne coûte rien.
    private val executor by lazy {
        Executors.newFixedThreadPool(MAX_CONCURRENT_SOUNDS) { runnable ->
            Thread(runnable, "sfx").apply { isDaemon = true }
        }
    }

    @Volatile
    private var released = false

    fun play(sfx: List<Beep>) {
        if (released || sfx.isEmpty()) return
        val pcm = cache.getOrPut(sfx) { Synth.render(sfx) }
        if (pcm.isEmpty()) return
        // Un bruitage raté ne doit jamais interrompre une partie : le site
        // enveloppe aussi ses lectures audio dans un try/catch. `execute()`
        // lui-même peut échouer si release() vient d'être appelé.
        runCatching {
            executor.execute { playBlocking(pcm) }
        }
    }

    /** Joue [pcm] jusqu'au bout. À n'appeler que depuis le pool de lecture. */
    private fun playBlocking(pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(Synth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                // STATIC : le tampon entier est écrit avant la lecture, donc
                // le son démarre sans latence d'amorçage.
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            // `finally` : une piste audio non libérée est une ressource
            // système qui fuit, y compris si l'écriture ou la lecture échoue.
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                // En mode STATIC, play() rend la main tout de suite : il faut
                // attendre la fin de la lecture avant de libérer la piste,
                // sinon le son est coupé net.
                Thread.sleep((pcm.size * 1000L / Synth.SAMPLE_RATE) + TAIL_MARGIN_MS)
                track.stop()
            } finally {
                track.release()
            }
        }
    }

    fun release() {
        released = true
        executor.shutdown()
        cache.clear()
    }

    private companion object {
        /** Assez pour les superpositions du jeu (deux ou trois bruitages au
         *  même instant) sans immobiliser des fils pour rien. */
        const val MAX_CONCURRENT_SOUNDS = 4

        /** Petite marge après la durée théorique, le temps que la piste ait
         *  vraiment fini de sortir du tampon. */
        const val TAIL_MARGIN_MS = 50L
    }
}

/**
 * Le lecteur de bruitages de la partie en cours. Un seul pour toute l'app :
 * le cache PCM n'a d'intérêt que s'il est partagé entre les écrans.
 *
 * Le lecteur par défaut évite d'avoir à en fournir un dans les
 * prévisualisations Compose ; il ne consomme rien tant qu'aucun son n'est
 * joué (voir `executor`).
 */
val LocalSfx = staticCompositionLocalOf { SfxPlayer() }

/** Libère le lecteur quand l'app quitte l'écran. */
@Composable
fun rememberSfxPlayer(): SfxPlayer {
    val player = remember { SfxPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}
