package com.bullythetrousse.app

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Musique de fond, une piste par monde — portage d'`applyWorldMusic()` côté
 * web (`<audio id="bg-music">`, `bg-music-plage`, `bg-music-volcan`, tous en
 * `loop`). Le bouton 🔊 de la barre du bas coupe le son, comme `#btn-mute`.
 *
 * Une piste par monde : elle est créée au changement de monde et libérée au
 * suivant, sans quoi les musiques s'empileraient. Couper le son ou passer
 * l'app en arrière-plan la met en PAUSE plutôt que de la détruire, pour
 * qu'elle reprenne là où elle s'était arrêtée.
 *
 * @return vrai tant que la musique joue réellement — le compteur d'écoute
 *   du succès « Adorateur de la musique » en dépend.
 */
@Composable
fun WorldMusic(world: String, muted: Boolean, inForeground: Boolean): Boolean {
    val context = LocalContext.current
    val track = when (world) {
        "volcans" -> R.raw.music_volcan
        "plage" -> R.raw.music_plage
        else -> R.raw.music_cour
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(track) {
        // create() renvoie null si le décodage échoue : dans ce cas le jeu
        // continue simplement sans musique plutôt que de planter.
        val created = runCatching { MediaPlayer.create(context, track) }.getOrNull()?.apply { isLooping = true }
        player = created
        onDispose {
            player = null
            created?.let {
                runCatching {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            }
        }
    }

    val shouldPlay = !muted && inForeground
    LaunchedEffect(player, shouldPlay) {
        val p = player ?: return@LaunchedEffect
        runCatching { if (shouldPlay) p.start() else if (p.isPlaying) p.pause() }
    }
    return shouldPlay && player != null
}
