package com.bullythetrousse.core

/**
 * Une trousse cosmétique, portée champ par champ depuis les entrées du
 * tableau `SKINS` côté web (index.html). Les flags `is*` sélectionnent une
 * mécanique spéciale (portée plus tard, un skin à la fois — voir
 * android/README.md) ; ils ne sont pas encore branchés au gameplay ici,
 * seuls les bonus de stats et `maxDurability` le sont (voir [SkinStats]).
 */
data class Skin(
    val id: String,
    val cost: Int,
    val bonusPuissance: Int = 0,
    val bonusVitesse: Int = 0,
    val maxDurability: Int = 100,
    val isCoin: Boolean = false,
    val isBasket: Boolean = false,
    val isLunar: Boolean = false,
    val isIron: Boolean = false,
    val isClaude: Boolean = false,
    val isRocket: Boolean = false,
    val isPlane: Boolean = false,
    val isVampire: Boolean = false,
)

/** Portage du tableau `SKINS` côté web, dans le même ordre. */
object Skins {
    val ALL: List<Skin> = listOf(
        Skin(id = "classique", cost = 0),
        Skin(id = "doree", cost = 400, bonusPuissance = 2),
        Skin(id = "glacee", cost = 1200, bonusVitesse = 3),
        Skin(id = "feu", cost = 3200, bonusPuissance = 3, bonusVitesse = 2),
        Skin(id = "arcenciel", cost = 9000, bonusPuissance = 5, bonusVitesse = 5),
        Skin(id = "piece", cost = 2500, isCoin = true),
        Skin(id = "basket", cost = 36000, bonusPuissance = 3, bonusVitesse = 6, isBasket = true),
        Skin(id = "lunaire", cost = 25000, isLunar = true),
        Skin(id = "fer", cost = 5000, bonusPuissance = -1, bonusVitesse = -1, maxDurability = 300, isIron = true),
        Skin(id = "claude", cost = 4500, isClaude = true),
        Skin(id = "fusee", cost = 50000, isRocket = true),
        Skin(id = "avion", cost = 100000, isPlane = true),
        Skin(id = "fantome", cost = 100000, bonusVitesse = 8),
        Skin(id = "vampire", cost = 60000, bonusPuissance = 5, bonusVitesse = 6, isVampire = true),
    )

    /** `getSkin(id)` côté web : introuvable -> la trousse classique, jamais d'exception. */
    fun find(id: String): Skin = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/**
 * Statistiques dérivées du skin équipé, portées depuis totalPuissance()/
 * totalVitesse()/maxDurability() côté web.
 */
object SkinStats {
    fun totalPuissance(save: GameSave): Int = save.puissanceLevel + Skins.find(save.equippedSkin).bonusPuissance
    fun totalVitesse(save: GameSave): Int = save.vitesseLevel + Skins.find(save.equippedSkin).bonusVitesse
    fun maxDurability(save: GameSave): Int = Skins.find(save.equippedSkin).maxDurability
}

/**
 * Achat/équipement d'un skin, portage du gestionnaire de clic de
 * buildSkinCard() (bouton "Prix") et d'equipSkin() côté web.
 */
object SkinShop {
    sealed interface PurchaseResult {
        data class Success(val save: GameSave) : PurchaseResult
        data object NotEnoughMoney : PurchaseResult
    }

    fun buy(save: GameSave, skinId: String): PurchaseResult {
        val skin = Skins.find(skinId)
        if (save.money < skin.cost) return PurchaseResult.NotEnoughMoney
        val debited = save.copy(money = save.money - skin.cost, ownedSkins = save.ownedSkins + skinId)
        return PurchaseResult.Success(equip(debited, skinId))
    }

    /** `save.durability = Math.min(save.durability, maxDurability())` : plafonne la
     *  durabilité au max du nouveau skin (ex: 250/300 -> 100/100 en repassant sur
     *  une trousse normale), plutôt que de la remettre à fond. */
    fun equip(save: GameSave, skinId: String): GameSave {
        val newMax = Skins.find(skinId).maxDurability
        return save.copy(equippedSkin = skinId, durability = minOf(save.durability, newMax))
    }
}
