package com.bullythetrousse.core

/**
 * Le résumé d'une partie affiché dans le choix « quelle partie garder ? »
 * (`summarizeSaveStats()` côté site) : juste de quoi reconnaître laquelle est
 * laquelle, jamais le contenu complet d'une sauvegarde.
 */
data class SaveSummary(val pseudo: String, val money: Int, val bestDistance: Double, val level: Int) {
    companion object {
        fun of(save: GameSave) = SaveSummary(
            pseudo = save.pseudo.ifBlank { "-" },
            money = save.money,
            bestDistance = save.bestDistance,
            level = save.puissanceLevel + save.vitesseLevel,
        )
    }
}

/** La partie retenue : celle de cet appareil, ou celle du code. */
enum class MergeChoice { CURRENT, OTHER }

/**
 * Les anciennes parties du site portaient d'autres noms de champs, renommés
 * depuis. Un ANCIEN code de récupération (simple copie de partie dans
 * `recoveryCodes`) peut encore les ramener : `migrateLegacyVolcanFields()`
 * les reprend à leur nouvelle place, sans jamais écraser une valeur déjà là.
 */
object LegacySave {
    fun migrate(save: GameSave, raw: Map<String, Any?>): GameSave {
        var s = save
        if (!s.volcanUnlocked && raw["volcanoUnlocked"] == true) s = s.copy(volcanUnlocked = true)
        if (!s.volcanTutorialSeen && raw["volcanoTutorialSeen"] == true) s = s.copy(volcanTutorialSeen = true)
        if (!s.volcanHelpAvailable && raw["volcanoEverFailed"] == true && raw["volcanoUnlocked"] != true) {
            s = s.copy(volcanHelpAvailable = true)
        }
        if (s.volcanFailedUntil == 0L) {
            (raw["volcanoFailUntil"] as? Number)?.toLong()?.let { s = s.copy(volcanFailedUntil = it) }
        }
        if (s.playTime == 0L) {
            (raw["playtimeSeconds"] as? Number)?.toLong()?.let { s = s.copy(playTime = it) }
        }
        return s
    }
}
