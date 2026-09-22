package com.bullythetrousse.app

import android.content.Context
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SaveCodec
import java.io.File

/**
 * Persistance locale de [GameSave], équivalent Android de
 * `localStorage.getItem/setItem(SAVE_KEY, ...)` côté web (voir
 * `loadSave()`/`persist()` dans index.html) : un simple fichier JSON dans
 * le stockage privé de l'app, encodé/décodé par [SaveCodec] (porté et
 * testé dans `:core`). C'est la source de vérité quand le jeu est hors
 * ligne ; la copie cloud est gérée à côté par [FirebaseBridge], qui
 * s'appuie sur l'horodatage tenu ici pour savoir qui a la version la plus
 * récente.
 */
class SaveRepository(context: Context) {
    private val saveFile = File(context.filesDir, "save.json")

    /**
     * Quand cet appareil a écrit sa sauvegarde pour la dernière fois.
     *
     * Sert à trancher au démarrage entre la partie locale et celle du cloud
     * (voir `CloudSaveSync` dans `:core`) : une copie distante plus récente
     * que notre dernière écriture vient forcément d'un autre appareil, et
     * doit gagner ; plus ancienne, elle serait une régression.
     *
     * Fichier séparé plutôt qu'un champ de la sauvegarde : c'est une
     * information propre À CET APPAREIL, qui n'a rien à faire dans le
     * document partagé avec le site.
     */
    private val stampFile = File(context.filesDir, "save-stamp")

    var lastPersistAtMillis: Long
        get() = try {
            if (stampFile.exists()) stampFile.readText().trim().toLongOrNull() ?: 0L else 0L
        } catch (e: java.io.IOException) {
            0L
        }
        private set(value) {
            try {
                stampFile.writeText(value.toString())
            } catch (e: java.io.IOException) {
                // Horodatage perdu : au pire la prochaine synchro préférera
                // le cloud. Jamais bloquant.
            }
        }

    /** Ne lance jamais d'exception : fichier absent ou illisible -> sauvegarde neuve,
     *  même filet de sécurité que le `catch` de `loadSave()` côté web. */
    fun load(): GameSave {
        val raw = try {
            if (saveFile.exists()) saveFile.readText() else null
        } catch (e: java.io.IOException) {
            null
        }
        return SaveCodec.decodeOrDefault(raw)
    }

    fun save(save: GameSave) {
        saveFile.writeText(SaveCodec.encode(save))
        lastPersistAtMillis = System.currentTimeMillis()
    }

    /** Après avoir appliqué une sauvegarde venue du cloud : on l'écrit en
     *  local sans prétendre que CET appareil vient de jouer, sinon la
     *  prochaine comparaison croirait que le local est plus récent. */
    fun saveFromCloud(save: GameSave, cloudUpdatedAtMillis: Long) {
        saveFile.writeText(SaveCodec.encode(save))
        lastPersistAtMillis = cloudUpdatedAtMillis
    }
}
