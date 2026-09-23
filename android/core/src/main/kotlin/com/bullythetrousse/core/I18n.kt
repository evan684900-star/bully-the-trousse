package com.bullythetrousse.core

/** Les deux langues du jeu, stockées dans `save.lang` comme côté site. */
enum class Lang(val id: String) {
    FR("fr"),
    EN("en"),
    ;

    companion object {
        /** `save.lang` vide ou inconnu : français, comme `entry[save.lang] || entry.fr`. */
        fun fromId(id: String): Lang = entries.firstOrNull { it.id == id } ?: FR

        /**
         * Langue de départ d'une toute nouvelle sauvegarde, portage de
         * `navigator.language.startsWith("fr") ? "fr" : "en"` côté site :
         * un téléphone réglé en français joue en français, tous les autres
         * en anglais.
         */
        fun forDeviceLanguage(deviceLanguage: String?): Lang =
            if (deviceLanguage?.lowercase()?.startsWith("fr") == true) FR else EN
    }
}

/**
 * Traductions de l'interface, portage de `tr(key)` côté site : la même table
 * (générée depuis `STRINGS`, voir [I18nStrings]) et la même règle de repli —
 * une clé inconnue renvoie la clé elle-même, jamais une chaîne vide, pour
 * qu'un oubli se voie à l'écran au lieu de faire disparaître un libellé.
 */
object I18n {
    fun has(key: String): Boolean = I18nStrings.TABLE.containsKey(key) || I18nAppStrings.TABLE.containsKey(key)

    /** Le texte brut, placeholders `{nom}` compris. */
    fun raw(key: String, lang: Lang): String {
        val entry = I18nStrings.TABLE[key] ?: I18nAppStrings.TABLE[key] ?: return key
        return when (lang) {
            Lang.FR -> entry.first
            Lang.EN -> entry.second.ifEmpty { entry.first }
        }
    }

    /**
     * Le texte avec ses placeholders remplacés : `{amount}`, `{n}`,
     * `{pseudo}`... — l'équivalent des `.replace("{amount}", amount)`
     * enchaînés côté site.
     */
    fun tr(key: String, lang: Lang, vararg args: Pair<String, Any?>): String {
        var text = raw(key, lang)
        for ((name, value) in args) text = text.replace("{$name}", value.toString())
        return text
    }

    /** Lettre du jour de la semaine (`weekdayLetter()` côté site), 1 = lundi … 7 = dimanche (ISO). */
    fun weekdayLetter(isoDayOfWeek: Int, lang: Lang): String {
        // Site : index Date.getDay(), 0 = dimanche.
        val jsIndex = isoDayOfWeek % 7
        val fr = listOf("D", "L", "M", "M", "J", "V", "S")
        val en = listOf("S", "M", "T", "W", "T", "F", "S")
        return (if (lang == Lang.EN) en else fr)[jsIndex]
    }
}

/** Nom traduit d'un succès (clé `nameKey` du site). */
fun Achievement.name(lang: Lang): String =
    ACHIEVEMENT_KEYS[id]?.let { I18n.tr(it.first, lang) } ?: id

/** Description traduite d'un succès (clé `descKey` du site). */
fun Achievement.description(lang: Lang): String =
    ACHIEVEMENT_KEYS[id]?.let { I18n.tr(it.second, lang) } ?: ""
