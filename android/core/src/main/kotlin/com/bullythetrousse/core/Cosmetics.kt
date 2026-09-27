package com.bullythetrousse.core

/** Un cosmétique de La Trousserie : purement décoratif, se porte sur n'importe quel skin. */
data class Cosmetic(val id: String, val name: String, val nameEn: String, val cost: Int) {
    /** `villeCosmeticName(cm)` côté site. */
    fun name(lang: Lang): String = if (lang == Lang.EN) nameEn else name
}

/**
 * La Trousserie, la boutique de la rue du monde Ville : portage de
 * `COSMETICS`/`buildCosmeticCard()` (index.html). Un seul cosmétique porté
 * à la fois ; le prix suit la règle de la Ville (+20 %, voir [Ville.price]).
 */
object Cosmetics {
    val ALL: List<Cosmetic> = listOf(
        Cosmetic("noeud", "Nœud papillon", "Bow tie", 1200),
        Cosmetic("casquette", "Casquette", "Cap", 1800),
        Cosmetic("lunettes", "Lunettes de soleil", "Sunglasses", 2500),
        Cosmetic("chapeau", "Haut-de-forme", "Top hat", 4000),
        Cosmetic("moustache", "Moustache distinguée", "Fancy moustache", 5000),
        Cosmetic("antennes", "Antennes d'alien", "Alien antennae", 8000),
        Cosmetic("aureole", "Auréole", "Halo", 12000),
        Cosmetic("couronne", "Couronne", "Crown", 20000),
    )

    fun find(id: String): Cosmetic? = ALL.firstOrNull { it.id == id }

    fun price(cosmetic: Cosmetic, save: GameSave): Int = Ville.price(cosmetic.cost, save)

    sealed interface Result {
        /** [spent] vaut 0 pour un simple changement (porter/retirer). */
        data class Success(val save: GameSave, val spent: Int) : Result
        data object NotEnoughMoney : Result
    }

    /**
     * Le bouton de la carte : "Retirer" si porté, "Porter" si possédé, le
     * prix sinon — un achat le porte aussitôt.
     */
    fun press(save: GameSave, id: String): Result {
        val cosmetic = find(id) ?: return Result.Success(save, 0)
        return when {
            save.equippedCosmetic == id -> Result.Success(save.copy(equippedCosmetic = ""), 0)
            id in save.ownedCosmetics -> Result.Success(save.copy(equippedCosmetic = id), 0)
            else -> {
                val price = price(cosmetic, save)
                if (save.money < price) return Result.NotEnoughMoney
                Result.Success(
                    save.copy(
                        money = save.money - price,
                        ownedCosmetics = save.ownedCosmetics + id,
                        equippedCosmetic = id,
                    ),
                    price,
                )
            }
        }
    }
}
