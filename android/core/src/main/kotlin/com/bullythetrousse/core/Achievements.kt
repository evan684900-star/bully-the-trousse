package com.bullythetrousse.core

/**
 * Un succès, porté depuis les entrées du tableau `ACHIEVEMENTS` côté web
 * (index.html). Pas de `name`/`desc` ici (ce sont des clés de traduction
 * côté web, `nameKey`/`descKey` — la présentation reste une préoccupation
 * de `:app`/UI, `:core` ne porte que l'id, l'émoji et la condition).
 */
data class Achievement(val id: String, val emoji: String, val check: (GameSave) -> Boolean)

/**
 * Les 47 succès du jeu, portage exact (mêmes ids, mêmes émojis, mêmes
 * conditions, même ordre) du tableau `ACHIEVEMENTS` côté web.
 */
object Achievements {
    val ALL: List<Achievement> = listOf(
        Achievement("premierLancer", "🚀") { it.totalThrows >= 1 },
        Achievement("cent100m", "📏") { it.bestDistance >= 100 },
        Achievement("cinq500m", "🏹") { it.bestDistance >= 500 },
        Achievement("mille1000m", "🌠") { it.bestDistance >= 1000 },
        Achievement("mille1500m", "🌌") { it.bestDistance >= 1500 },
        Achievement("deuxMille2000m", "🪐") { it.bestDistance >= 2000 },
        Achievement("quatreMille4000m", "🕳️") { it.bestDistance >= 4000 },
        Achievement("dixMille10000m", "🌀") { it.bestDistance >= 10000 },
        Achievement("lancerParfait", "✨") { it.hasPerfectThrow },
        Achievement("volcanDebloque", "🌋") { it.volcanUnlocked },
        Achievement("claquettes", "🩴") { it.hasClaquettes },
        Achievement("plageDebloquee", "🏖️") { it.plageUnlocked },
        Achievement("retourDeVacances", "🚌") { it.hasTakenBusBack },
        Achievement("plageLancers", "🦀") { it.plageThrows >= 100 },
        Achievement("plage500m", "📏") { it.plageBestDistance >= 500 },
        Achievement("plage2000m", "🌊") { it.plageBestDistance >= 2000 },
        Achievement("plageEconomies", "💸") { it.plageMoneyEarned >= 250000 },
        Achievement("parasols25", "⛱️") { it.plageParasolBounces >= 25 },
        Achievement("serviettes20", "🧺") { it.plageTowelsFound >= 20 },
        Achievement("chateaux15", "🏰") { it.plageCastlesCrushed >= 15 },
        Achievement("trousseCassee", "💥") { it.hasBrokenDurability },
        // Skins.ALL.all(...) plutôt que "ownedSkins.size >= Skins.ALL.size" :
        // un skin retiré du catalogue mais encore présent dans une vieille
        // sauvegarde fausserait un simple comptage de tailles.
        Achievement("collectionneur", "🎨") { save -> Skins.ALL.all { save.ownedSkins.contains(it.id) } },
        Achievement("riche5000", "💰") { it.money >= 5000 },
        Achievement("jackpotX5", "🪙") { it.hasJackpot },
        Achievement("joueurAssidu", "⏱️") { it.playTime >= 3600 },
        Achievement("cinquanteLancers", "💪") { it.totalThrows >= 50 },
        Achievement("centLancers", "🦵") { it.totalThrows >= 100 },
        Achievement("cacaCache", "💩") { it.hasFoundPoopEgg },
        Achievement("toutesTrainees", "🌈") { it.ownedTrails.size >= Trails.ALL.size },
        Achievement("easterEggEspace", "🛸") { it.hasTriggeredSpaceEgg },
        Achievement("qteParfait", "🎯") { it.hasPerfectQte },
        Achievement("millionnaire", "🤑") { it.totalMoneyEarned >= 1000000 },
        Achievement("cinqCentsLancers", "🏋️") { it.totalThrows >= 500 },
        Achievement("marathonien", "🏃") { it.playTime >= 18000 },
        Achievement("niveau50", "💯") { it.puissanceLevel + it.vitesseLevel >= 50 },
        Achievement("defisAccomplis", "📅") { it.dailyChallenges.size >= 3 && it.dailyChallenges.all { c -> c.claimed } },
        Achievement("grandDonateur", "🎁") { it.totalMoneyGifted >= 1000000 },
        Achievement("adorateurMusique", "🎧") { it.musicListenSeconds >= 10800 },
        Achievement("etourdi", "🤦") { it.hasForgottenMoney },
        Achievement("vingtMille20000m", "🌍") { it.bestDistance >= 20000 },
        Achievement("cinquanteMille50000m", "🛰️") { it.bestDistance >= 50000 },
        Achievement("multiMillionnaire", "💎") { it.totalMoneyEarned >= 10000000 },
        Achievement("changeDeTete", "🖼️") { it.avatarEmoji.isNotEmpty() },
        Achievement("plage5000", "🐚") { it.plageBestDistance >= 5000 },
        Achievement("milleLancers", "🔥") { it.totalThrows >= 1000 },
        Achievement("niveau100", "👑") { it.puissanceLevel + it.vitesseLevel >= 100 },
        Achievement("secretTrouve", "🔓") { it.ownedSkins.contains("secret") },
    )

    /** Portage de checkAchievements() côté web : les succès qui viennent de
     *  se déclencher (condition vraie, pas encore dans unlockedAchievements). */
    fun newlyUnlocked(save: GameSave): List<Achievement> =
        ALL.filter { !save.unlockedAchievements.contains(it.id) && it.check(save) }

    /** Ajoute tous les succès nouvellement débloqués à la sauvegarde (aucun
     *  effet si rien de nouveau). */
    fun apply(save: GameSave): GameSave {
        val newly = newlyUnlocked(save)
        if (newly.isEmpty()) return save
        return save.copy(unlockedAchievements = save.unlockedAchievements + newly.map { it.id })
    }
}
