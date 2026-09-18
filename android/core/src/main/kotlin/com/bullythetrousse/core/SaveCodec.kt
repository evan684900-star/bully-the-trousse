package com.bullythetrousse.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * (Dé)sérialisation JSON de [GameSave], portage de `loadSave()` côté web :
 * un champ absent du JSON garde sa valeur par défaut (`ignoreUnknownKeys`
 * fait l'inverse : un champ du JSON inconnu de [GameSave], ex. venant d'une
 * version plus récente de l'app, est ignoré sans faire planter la lecture)
 * — le même esprit que `Object.assign(defaultSave(), parsed)` côté web,
 * obtenu ici directement par kotlinx.serialization plutôt qu'à la main.
 */
object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(save: GameSave): String = json.encodeToString(GameSave.serializer(), save)

    /** Ne renvoie jamais d'exception : un JSON absent/corrompu redonne une
     *  sauvegarde neuve, comme le `catch (e) { return defaultSave(); }` de
     *  `loadSave()` côté web. */
    fun decodeOrDefault(rawJson: String?): GameSave {
        if (rawJson.isNullOrBlank()) return GameSave()
        return try {
            json.decodeFromString(GameSave.serializer(), rawJson)
        } catch (e: SerializationException) {
            GameSave()
        } catch (e: IllegalArgumentException) {
            GameSave()
        }
    }
}
