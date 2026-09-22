package com.bullythetrousse.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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

    /**
     * La sauvegarde vue comme un dictionnaire de champs, tel que le site
     * l'écrit dans `users/{uid}` : un champ Firestore par champ de la
     * sauvegarde, et non une grosse chaîne JSON.
     *
     * C'est ce qui permet au téléphone et au site de partager le MÊME
     * document : écrire la sauvegarde sous une autre forme donnerait deux
     * modèles de données incompatibles dans un seul projet Firebase, et le
     * site relirait une sauvegarde vide.
     *
     * Les nombres sont rendus en `Long` ou en `Double` selon ce que JSON en
     * dit, pour que Firestore stocke un entier là où le site stocke un
     * entier (sans quoi un `bestDistance` de 0 arriverait en entier d'un
     * côté et en flottant de l'autre).
     */
    fun toFieldMap(save: GameSave): Map<String, Any?> =
        json.encodeToJsonElement(GameSave.serializer(), save).jsonObject.mapValues { toPlain(it.value) }

    /**
     * L'inverse : la sauvegarde reconstruite depuis le document Firestore.
     * Tolérant par construction — un champ absent garde sa valeur par
     * défaut, un champ en trop (`updatedAt`, `sessionId`, ou un champ d'une
     * version plus récente du site) est ignoré.
     */
    fun fromFieldMap(fields: Map<String, Any?>): GameSave =
        json.decodeFromJsonElement(GameSave.serializer(), toJsonElement(fields))

    private fun toPlain(element: JsonElement): Any? = when (element) {
        // JsonNull est un JsonPrimitive : il doit être testé en premier.
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" -> true
            element.content == "false" -> false
            element.content.any { it == '.' || it == 'e' || it == 'E' } -> element.content.toDouble()
            else -> element.content.toLong()
        }
        is JsonArray -> element.map { toPlain(it) }
        is JsonObject -> element.mapValues { toPlain(it.value) }
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        // Tout le reste (un Timestamp Firestore, par exemple) : rendu sous
        // forme de texte, donc ignoré à la lecture puisque aucun champ de la
        // sauvegarde ne porte ce nom.
        else -> JsonPrimitive(value.toString())
    }

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
