package com.bullythetrousse.core

import kotlinx.serialization.Serializable

/** Un défi quotidien, porté depuis la forme exacte des objets créés par
 *  `generateDailyChallenges()` côté web. */
@Serializable
data class DailyChallenge(
    val kind: String,
    val target: Double,
    val reward: Int,
    val progress: Double = 0.0,
    val claimed: Boolean = false,
)

/**
 * Sauvegarde du joueur, portée champ par champ depuis `defaultSave()` côté
 * web (index.html). Chaque valeur par défaut ci-dessous reproduit
 * exactement celle du web, dans le même ordre et avec les mêmes
 * commentaires quand ils expliquent un piège non-évident.
 *
 * Les migrations d'anciens noms de champs (`migrateLegacyVolcanFields`
 * côté web, pour d'anciennes parties web renommées depuis) ne sont PAS
 * portées ici : ce sont des sauvegardes web historiques, qui n'existent
 * pas côté Android (application neuve, aucun ancien format à migrer).
 */
@Serializable
data class GameSave(
    val money: Int = 0,
    val puissanceLevel: Int = 0,
    val vitesseLevel: Int = 0,
    val bestDistance: Double = 0.0,
    val ownedSkins: List<String> = listOf("classique"),
    val equippedSkin: String = "classique",
    val musicMuted: Boolean = false,
    val pseudo: String = "",
    val tutorialSeen: Boolean = false,
    val milestoneReached: Int = 0,
    val ownedTrails: List<String> = listOf("blanche"),
    val equippedTrail: String = "blanche",
    val theme: String = "dark",
    val lang: String = "",
    // --- Monde Volcan ---
    val currentWorld: String = "cour",
    val volcanUnlocked: Boolean = false,
    val volcanFailedUntil: Long = 0L,
    val volcanHelpAvailable: Boolean = false,
    val volcanTutorialSeen: Boolean = false,
    val durability: Int = 100,
    val playTime: Long = 0L,
    val musicListenSeconds: Long = 0L,
    // --- Monde Plage ---
    val hasClaquettes: Boolean = false,
    val plageUnlocked: Boolean = false,
    val plageTutorialSeen: Boolean = false,
    val inPlage: Boolean = false,
    val hasTakenBusBack: Boolean = false,
    val hasForgottenMoney: Boolean = false,
    val plageThrows: Int = 0,
    val plageBestDistance: Double = 0.0,
    val plageMoneyEarned: Int = 0,
    val plageParasolBounces: Int = 0,
    val plageTowelsFound: Int = 0,
    val plageCastlesCrushed: Int = 0,
    // --- Succès ---
    val unlockedAchievements: List<String> = emptyList(),
    val totalThrows: Int = 0,
    val hasPerfectThrow: Boolean = false,
    val hasBrokenDurability: Boolean = false,
    val hasJackpot: Boolean = false,
    val hasTriggeredSpaceEgg: Boolean = false,
    val hasPerfectQte: Boolean = false,
    val hasFoundPoopEgg: Boolean = false,
    // --- Compte / profil ---
    val totalMoneyEarned: Int = 0,
    val dailyEarnings: Map<String, Int> = emptyMap(),
    val dailyBestDistance: Map<String, Double> = emptyMap(),
    val isPrivate: Boolean = false,
    val avatarEmoji: String = "",
    // --- Code de récupération ---
    val recoveryCode: String = "",
    val recoveryRetireToken: String = "",
    // --- Défis quotidiens ---
    val dailyChallengeDate: String = "",
    val dailyChallenges: List<DailyChallenge> = emptyList(),
    val totalMoneyGifted: Int = 0,
)
