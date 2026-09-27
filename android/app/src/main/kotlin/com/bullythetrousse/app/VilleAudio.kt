package com.bullythetrousse.app

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Les sons "fichiers" du monde Ville (`<audio>` côté site) : musique du
 * combat de boss, pas de la trousse sur le béton, crissement de pneus au
 * passage piéton. Les bips, eux, restent synthétisés (voir SfxPlayer).
 */

/** Crée un lecteur, ou null si le décodage échoue : le jeu continue alors sans ce son. */
private fun player(context: Context, resId: Int, looping: Boolean): MediaPlayer? =
    runCatching { MediaPlayer.create(context, resId)?.apply { isLooping = looping } }.getOrNull()

/**
 * `boss-music` : repart du début à chaque (re)départ du combat
 * ([generation]), continue jusqu'au générique, suit le bouton 🔊 comme les
 * autres musiques (`aBoss.muted = save.musicMuted`).
 */
@Composable
internal fun BossMusic(active: Boolean, generation: Int, volume: Float, muted: Boolean) {
    val context = LocalContext.current
    val player = remember { player(context, R.raw.music_boss, looping = true) }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(active, generation) {
        val p = player ?: return@LaunchedEffect
        runCatching {
            if (active) {
                if (generation > 0) p.seekTo(0)
                p.start()
            } else if (p.isPlaying) {
                p.pause()
            }
        }
    }
    LaunchedEffect(volume, muted) {
        val v = if (muted) 0f else volume
        runCatching { player?.setVolume(v, v) }
    }
}

/** Un son en boucle joué tant que [playing] (les pas sur le béton). */
@Composable
internal fun LoopingSound(resId: Int, playing: Boolean, volume: Float) {
    val context = LocalContext.current
    val player = remember(resId) { player(context, resId, looping = true)?.apply { setVolume(volume, volume) } }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(player, playing) {
        val p = player ?: return@LaunchedEffect
        runCatching { if (playing) p.start() else if (p.isPlaying) p.pause() }
    }
}

/** Un son joué d'un coup, rejouable depuis le début (`playA(a, true)`). */
internal class OneShotSound(private val player: MediaPlayer?) {
    fun play() {
        runCatching {
            player?.seekTo(0)
            player?.start()
        }
    }

    fun release() {
        player?.release()
    }
}

@Composable
internal fun rememberOneShotSound(resId: Int): OneShotSound {
    val context = LocalContext.current
    val sound = remember(resId) { OneShotSound(player(context, resId, looping = false)) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    return sound
}
