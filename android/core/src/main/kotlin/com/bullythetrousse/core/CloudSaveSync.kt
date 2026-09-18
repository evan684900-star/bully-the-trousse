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
}
