package com.bullythetrousse.app

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Musique de fond, une piste par monde — portage d'`applyWorldMusic()` côté
 * web (`<audio id="bg-music">`, `bg-music-plage`, `bg-music-volcan`,
 * `bg-music-ville`, tous en `loop`). Le bouton 🔊 de la barre du bas coupe
 * le son, comme `#btn-mute`.
 *
 * Changer de monde libère la piste précédente (sans ça, les musiques
 * s'empileraient). Couper le son ou mettre en [paused] — le mode histoire
 * de la Ville fait taire le monde, `pauseWorldMusic()` côté site — ne fait
 * que suspendre la lecture : elle reprend là où elle s'était arrêtée, comme
 * un `<audio>` qu'on remet en `play()`.
 */
@Composable
fun WorldMusic(world: String, muted: Boolean, paused: Boolean = false) {
    val context = LocalContext.current
    val track = when (world) {
        "volcans" -> R.raw.music_volcan
        "plage" -> R.raw.music_plage
        "ville" -> R.raw.music_ville
        else -> R.raw.music_cour
    }

    // create() renvoie null si le décodage échoue : dans ce cas le jeu
    // continue simplement sans musique plutôt que de planter.
    val player: MediaPlayer? = remember(track) {
        runCatching { MediaPlayer.create(context, track)?.apply { isLooping = true } }.getOrNull()
    }
    DisposableEffect(player) {
        onDispose {
            player?.let {
                runCatching { if (it.isPlaying) it.stop() }
                it.release()
            }
        }
    }
    LaunchedEffect(player, muted, paused) {
        val p = player ?: return@LaunchedEffect
        runCatching {
            if (muted || paused) {
                if (p.isPlaying) p.pause()
            } else if (!p.isPlaying) {
                p.start()
            }
        }
    }
}
