package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Vrai tant que l'app est au premier plan (entre ON_RESUME et ON_PAUSE).
 *
 * Sert à ce qui ne doit tourner que quand le joueur a l'app sous les yeux :
 * la musique (qui continuerait sinon à jouer téléphone verrouillé) et le
 * compteur de temps de jeu.
 */
@Composable
fun rememberAppInForeground(): State<Boolean> {
    val owner = LocalLifecycleOwner.current
    val resumed = remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed.value = true
                Lifecycle.Event.ON_PAUSE -> resumed.value = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}
