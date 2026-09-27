package com.bullythetrousse.core

/**
 * Compteur de temps de jeu, portage du `setInterval(..., 1000)` de
 * l'initialisation du site : +1 s de `playTime` par seconde, et la série
 * d'écoute musicale `musicListenSeconds` qui ne progresse que si la musique
 * joue sans interruption — toute coupure la remet à 0 (succès « Adorateur
 * de la musique », 3 h d'affilée).
 *
 * Les secondes s'accumulent en mémoire et ne sont écrites dans la sauvegarde
 * que toutes les [PERSIST_EVERY_SECONDS], comme le `if (save.playTime % 30
 * === 0) persist()` du site.
 */
class PlayTimeTracker(initialMusicStreak: Long) {
    /** Secondes de jeu pas encore reportées dans la sauvegarde. */
    var pendingSeconds: Int = 0
        private set

    /** Série d'écoute musicale en cours, en secondes. */
    var musicStreak: Long = initialMusicStreak
        private set

    /** Une seconde de plus au premier plan. Renvoie vrai quand il est temps
     *  d'écrire la sauvegarde. */
    fun tick(musicPlaying: Boolean): Boolean {
        pendingSeconds++
        musicStreak = if (musicPlaying) musicStreak + 1 else 0L
        return pendingSeconds >= PERSIST_EVERY_SECONDS
    }

    /** Reporte les secondes accumulées dans [save] et remet le compteur à zéro. */
    fun flushInto(save: GameSave): GameSave {
        val flushed = save.copy(
            playTime = save.playTime + pendingSeconds,
            musicListenSeconds = musicStreak,
        )
        pendingSeconds = 0
        return flushed
    }

    companion object {
        const val PERSIST_EVERY_SECONDS = 30
    }
}
