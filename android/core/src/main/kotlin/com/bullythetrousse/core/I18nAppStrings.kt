package com.bullythetrousse.core

/**
 * Textes propres à l'app Android, qui n'ont pas d'équivalent dans la table
 * STRINGS du site (réglage de qualité graphique, fiche d'un succès...).
 *
 * Écrite à la main, contrairement à [I18nStrings] qui est générée : toutes
 * les clés commencent par `app.` pour qu'on ne puisse jamais les confondre
 * avec une clé du site, ni les écraser en régénérant la table.
 */
internal object I18nAppStrings {
    val TABLE: Map<String, Pair<String, String>> = mapOf(
        // Liste des succès (grille + fiche au toucher)
        "app.achvCount" to ("{n} / {total} débloqués" to "{n} / {total} unlocked"),
        "app.achvTapHint" to ("Appuie sur un succès pour savoir ce qu'il récompense." to "Tap an achievement to see what it rewards."),
        "app.achvUnlocked" to ("✅ Débloqué" to "✅ Unlocked"),
        "app.achvLocked" to ("🔒 Pas encore débloqué" to "🔒 Not unlocked yet"),
        "app.achvPlayersPct" to ("{pct} % des joueurs l'ont débloqué" to "{pct}% of players unlocked it"),

        // Réglages propres à l'app
        "app.settingsAccount" to ("Compte" to "Account"),
        "app.settingsManage" to ("☁️ Gérer" to "☁️ Manage"),
        "app.settingsMusic" to ("Musique" to "Music"),
        "app.settingsQuality" to ("Qualité graphique" to "Graphics quality"),
        "app.qualityLow" to ("Basse" to "Low"),
        "app.qualityMedium" to ("Normale" to "Normal"),
        "app.qualityHigh" to ("Élevée" to "High"),
        "app.qualityLowHint" to (
            "Effets d'ambiance coupés et particules réduites : à choisir si le jeu saccade." to
                "Ambient effects off and fewer particles: pick this if the game stutters."
            ),
        "app.qualityMediumHint" to ("Tous les effets, en quantité mesurée. Recommandé." to "Every effect, in moderation. Recommended."),
        "app.qualityHighHint" to (
            "Particules, nuages en profondeur et sillages au maximum. Pour les téléphones à l'aise." to
                "Maximum particles, layered clouds and trails. For phones that can handle it."
            ),
        // Écran Compte (propre à l'app)
        "app.accountRetry" to ("🔄 Réessayer" to "🔄 Retry"),
        "app.accountNotConfigured" to (
            "Cette version de l'app n'a pas encore le fichier de configuration Firebase. Le jeu fonctionne, mais le classement et la sauvegarde en ligne sont coupés." to
                "This version of the app doesn't have its Firebase configuration file yet. The game works, but the leaderboard and online save are off."
            ),
        "app.privateOn" to ("🔒 Activé" to "🔒 On"),
        "app.privateOff" to ("🔓 Désactivé" to "🔓 Off"),

        "app.musicSaved" to ("⬇️ Enregistrée dans Téléchargements" to "⬇️ Saved to Downloads"),
        "app.musicSaveError" to ("Impossible d'enregistrer la musique." to "Couldn't save the music."),
    )
}
