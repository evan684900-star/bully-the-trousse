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
 * testé dans `:core`). Pas encore de synchronisation cloud (voir
 * android/README.md, étape Firebase) : purement local pour l'instant,
 * comme la sauvegarde web avant connexion à un compte.
 */
class SaveRepository(context: Context) {
    private val saveFile = File(context.filesDir, "save.json")

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
    }
}
