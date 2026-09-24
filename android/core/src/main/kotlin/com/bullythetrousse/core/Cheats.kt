package com.bullythetrousse.core

/**
 * Les triches de `window.cheats` côté site : des raccourcis pour tester sans
 * jouer des heures. Seules celles qui modifient la sauvegarde sont ici ; les
 * autres (rejouer une cinématique, afficher une popup...) ne font que
 * naviguer et vivent côté `:app`.
 *
 * Chaque fonction renvoie la sauvegarde modifiée, ou `null` quand l'argument
 * est invalide (skin ou traînée inconnus...), comme les `console.warn` du site.
 */
object Cheats {
    fun addMoney(save: GameSave, amount: Int): GameSave = save.copy(money = save.money + amount)

    /** Débloque ET équipe un skin. */
    fun unlockSkin(save: GameSave, skinId: String): GameSave? {
        if (Skins.ALL.none { it.id == skinId }) return null
        val owned = if (skinId in save.ownedSkins) save else save.copy(ownedSkins = save.ownedSkins + skinId)
        return SkinShop.equip(owned, skinId)
    }

    fun unlockAllSkins(save: GameSave): GameSave =
        save.copy(ownedSkins = save.ownedSkins + Skins.ALL.map { it.id }.filter { it !in save.ownedSkins })

    /** Débloque ET équipe une traînée. */
    fun unlockTrail(save: GameSave, trailId: String): GameSave? {
        if (Trails.ALL.none { it.id == trailId }) return null
        val owned = if (trailId in save.ownedTrails) save else save.copy(ownedTrails = save.ownedTrails + trailId)
        return owned.copy(equippedTrail = trailId)
    }

    fun setLevels(save: GameSave, puissance: Int?, vitesse: Int?): GameSave = save.copy(
        puissanceLevel = puissance ?: save.puissanceLevel,
        vitesseLevel = vitesse ?: save.vitesseLevel,
    )

    /** Bornée entre 0 et la durabilité max du skin équipé. */
    fun setDurability(save: GameSave, value: Int): GameSave =
        save.copy(durability = value.coerceIn(0, SkinStats.maxDurability(save)))

    /** Force la régénération des défis du jour, sans attendre minuit. */
    fun resetChallenges(save: GameSave, todayKey: String): GameSave =
        DailyChallenges.ensure(
            save.copy(dailyChallengeDate = "", dailyChallenges = emptyList()),
            todayKey,
            SkinStats.totalPuissance(save),
            SkinStats.totalVitesse(save),
        )

    /** Remet la Plage à « pas encore découverte » (et ramène au monde normal si on y était). */
    fun resetPlage(save: GameSave): GameSave = Beach.leave(save).copy(
        plageUnlocked = false,
        plageTutorialSeen = false,
        hasClaquettes = false,
        hasForgottenMoney = false,
    )

    /** Part à la plage sans cinématique. */
    fun goPlage(save: GameSave): GameSave = Beach.enter(save.copy(hasClaquettes = true, plageUnlocked = true))

    /** Reprend le bus du retour sans payer ; null si on n'est pas sur la plage. */
    fun retourBus(save: GameSave): GameSave? = if (save.inPlage) Beach.leave(save) else null

    /** Remet le monde Volcan à « pas encore découvert ». */
    fun resetVolcan(save: GameSave): GameSave = save.copy(
        volcanUnlocked = false,
        volcanTutorialSeen = false,
        volcanHelpAvailable = false,
        volcanFailedUntil = 0L,
        currentWorld = if (save.currentWorld == "volcans") "cour" else save.currentWorld,
    )

    /** `cinematique()` : lève le délai d'attente avant de rejouer. */
    fun clearVolcanoCooldown(save: GameSave): GameSave = save.copy(volcanFailedUntil = 0L)

    fun toggleTheme(save: GameSave): GameSave = save.copy(theme = if (save.theme == "dark") "light" else "dark")

    /** Topic ntfy.sh qui reçoit une notification à chaque triche utilisée
     *  (voir `notifyCheatUsed()` côté site). À ne jamais afficher. */
    const val NOTIFY_TOPIC = "bully-trousse-cheats-7bca40f00c4da444a1002656"

    /** Le message envoyé : « 🕹️ pseudo a utilisé cheats.nom(args) ». */
    fun notifyMessage(pseudo: String, name: String, args: List<String>): String {
        val who = pseudo.ifBlank { "(pseudo vide)" }
        val argsStr = if (args.isEmpty()) "" else " (" + args.joinToString(", ") + ")"
        return "🕹️ $who a utilisé cheats.$name()$argsStr"
    }
}
