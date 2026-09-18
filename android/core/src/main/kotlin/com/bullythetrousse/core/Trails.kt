package com.bullythetrousse.core

/**
 * Une traînée cosmétique, portée depuis les entrées du tableau `TRAILS`
 * côté web. `rgb` reste une chaîne "r,g,b" (ou "rainbow"/"aurora" pour les
 * cas spéciaux gérés par le rendu) exactement comme côté web plutôt que
 * d'introduire un type couleur ici, puisque `:core` n'a aucune dépendance
 * de rendu.
 */
data class Trail(val id: String, val cost: Int, val rgb: String)

/** Portage du tableau `TRAILS` côté web, dans le même ordre. */
object Trails {
    val ALL: List<Trail> = listOf(
        Trail(id = "blanche", cost = 0, rgb = "255,255,255"),
        Trail(id = "doree", cost = 300, rgb = "255,210,63"),
        Trail(id = "glacee", cost = 800, rgb = "120,200,255"),
        Trail(id = "toxique", cost = 1200, rgb = "140,240,70"),
        Trail(id = "feu", cost = 2000, rgb = "255,110,60"),
        Trail(id = "sakura", cost = 3500, rgb = "255,175,205"),
        Trail(id = "arcenciel", cost = 6000, rgb = "rainbow"),
        Trail(id = "fantome", cost = 9000, rgb = "175,250,220"),
        Trail(id = "foudre", cost = 15000, rgb = "150,205,255"),
        Trail(id = "neon", cost = 25000, rgb = "80,240,255"),
        Trail(id = "stellaire", cost = 40000, rgb = "190,140,255"),
        Trail(id = "encre", cost = 55000, rgb = "140,130,255"),
        Trail(id = "confetti", cost = 70000, rgb = "255,235,180"),
        Trail(id = "aurore", cost = 90000, rgb = "aurora"),
        Trail(id = "trounoir", cost = 120000, rgb = "150,90,255"),
    )

    /** `getTrail(id)` côté web : introuvable -> la traînée blanche, jamais d'exception. */
    fun find(id: String): Trail = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/**
 * Achat/équipement d'une traînée, portage du gestionnaire de clic de
 * buildTrailCard() côté web : contrairement à un skin, acheter une traînée
 * l'équipe toujours dans la foulée (pas d'étape "équiper" séparée pour un
 * achat).
 */
object TrailShop {
    sealed interface PurchaseResult {
        data class Success(val save: GameSave) : PurchaseResult
        data object NotEnoughMoney : PurchaseResult
    }

    fun buy(save: GameSave, trailId: String): PurchaseResult {
        val trail = Trails.find(trailId)
        if (save.money < trail.cost) return PurchaseResult.NotEnoughMoney
        return PurchaseResult.Success(
            save.copy(money = save.money - trail.cost, ownedTrails = save.ownedTrails + trailId, equippedTrail = trailId),
        )
    }

    fun equip(save: GameSave, trailId: String): GameSave = save.copy(equippedTrail = trailId)
}
