package com.bullythetrousse.app

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Musique de fond, une piste par monde — portage d'`applyWorldMusic()` côté
 * web (`<audio id="bg-music">`, `bg-music-plage`, `bg-music-volcan`, tous en
 * `loop`). Le bouton 🔊 de la barre du bas coupe le son, comme `#btn-mute`.
 *
 * La lecture s'arrête et la piste est libérée dès que le monde change, que
 * le son est coupé, ou que l'app quitte l'écran : sans ça, changer de monde
 * empilerait les musiques les unes sur les autres.
 */
@Composable
fun WorldMusic(world: String, muted: Boolean) {
    val context = LocalContext.current
    val track = when (world) {
        "volcans" -> R.raw.music_volcan
        "plage" -> R.raw.music_plage
        else -> R.raw.music_cour
    }

    DisposableEffect(track, muted) {
        val player: MediaPlayer? = if (muted) {
            null
        } else {
            // create() renvoie null si le décodage échoue : dans ce cas le jeu
            // continue simplement sans musique plutôt que de planter.
            MediaPlayer.create(context, track)?.apply {
                isLooping = true
                start()
            }
        }
        onDispose {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
    }
}
