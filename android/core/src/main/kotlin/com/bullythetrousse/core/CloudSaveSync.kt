package com.bullythetrousse.core

/**
 * Résolution de conflit entre la sauvegarde locale et une copie reçue du
 * cloud (Firestore), portage de `flushPendingRemoteSave()` côté web :
 * une copie distante reçue APRÈS le dernier `persist()` local est
 * appliquée sans discussion (elle vient d'un autre appareil, ou c'est
 * l'état initial reçu à la connexion) ; une copie reçue AVANT (ou en
 * même temps, cas limite) le dernier `persist()` local est ignorée — elle
 * ne contient pas les gains/records du lancer qui vient juste de se
 * terminer sur CET appareil, l'appliquer serait une régression.
 *
 * Volontairement pure et découplée de tout SDK Firebase : c'est la seule
 * partie de la synchronisation cloud qui a une vraie logique à porter/
 * tester ; le reste (authentification, lecture/écriture Firestore) est de
 * la plomberie réseau écrite côté `:app` (voir android/README.md — non
 * vérifiable dans ce bac à sable, à terminer dans Android Studio avec un
 * vrai projet Firebase).
 */
object CloudSaveSync {
    /** `lastPersistAt > receivedAt` côté web ⇒ ignorer. Ici on renvoie
     *  directement "faut-il appliquer ?", donc la négation. */
    fun shouldApplyRemoteSave(lastLocalPersistAtMillis: Long, remoteReceivedAtMillis: Long): Boolean =
        lastLocalPersistAtMillis <= remoteReceivedAtMillis

    /**
     * `meaningfulSaveFingerprint()` : deux parties qui ne diffèrent que par
     * le temps de jeu (ou la série d'écoute) ne sont pas « différentes » pour
     * le joueur — chaque appareil compte le sien, inutile de tout recharger
     * et d'afficher « synchronisée » pour ça.
     */
    fun differsMeaningfully(a: GameSave, b: GameSave): Boolean =
        a.copy(playTime = 0L, musicListenSeconds = 0L) != b.copy(playTime = 0L, musicListenSeconds = 0L)

    /**
     * `applyRemoteSave()` : la partie de l'autre appareil remplace la locale,
     * mais le temps de jeu ne recule jamais — on garde le plus avancé.
     */
    fun mergeRemote(remote: GameSave, local: GameSave): GameSave =
        remote.copy(playTime = maxOf(remote.playTime, local.playTime))
}
