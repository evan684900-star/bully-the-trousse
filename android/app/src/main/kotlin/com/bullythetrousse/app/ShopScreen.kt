package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Beach
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.Repair
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.HapticEvent
import com.bullythetrousse.core.Shop
import com.bullythetrousse.core.SkinShop
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.TrailShop
import com.bullythetrousse.core.Trails

/**
 * Boutique, portée de `#screen-shop` (index.html) : bouton retour à gauche,
 * "Retour au jeu" à droite, l'en-tête centré (argent + bonus de gains), le
 * rail d'onglets `.tabs` puis le corps `.shop-body` rempli de cartes
 * `.upgrade-card` / `.skin-card`.
 */
@Composable
fun ShopScreen(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onBackToMenu: () -> Unit,
    onBackToGame: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    val sfx = LocalSfx.current
    val haptics = LocalHaptics.current

    /** Achat réussi : sfxBuy() + le toast de confirmation du site. */
    fun purchase(updated: GameSave, cost: Int, message: String) {
        applyPurchase(updated, cost, onSaveChange)
        sfx.play(SfxCatalog.BUY)
        haptics.play(HapticEvent.BUY)
        toast = message
    }

    /** Achat refusé faute d'argent : sfxError() + le toast du site. */
    fun notEnoughMoney(message: String) {
        sfx.play(SfxCatalog.ERROR)
        haptics.play(HapticEvent.ERROR)
        toast = message
    }

    /** Équiper joue aussi sfxBuy() côté site, avec son propre toast. */
    fun equip(updated: GameSave, message: String) {
        onSaveChange(updated)
        sfx.play(SfxCatalog.BUY)
        haptics.play(HapticEvent.BUY)
        toast = message
    }

    val noMoney = tr("notEnoughMoney")
    val actions = ShopActions(::purchase, { notEnoughMoney(noMoney) }, ::equip)

    Box(modifier = Modifier.fillMaxSize().background(ShopBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            // .back-btn / .forward-btn : même hauteur, aux deux coins.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GameButton(tr("btnBackMenu"), secondary = true, small = true, onClick = onBackToMenu)
                GameButton(tr("btnBackGame"), small = true, onClick = onBackToGame)
            }

            // .shop-header : centré, argent + bonus de gains cumulé.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoneyPill("💰 ${save.money} $")
                val bonus = (SkinStats.totalPuissance(save) + SkinStats.totalVitesse(save)) * 3
                MoneyPill("📈 +$bonus%", small = true)
            }

            Box(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)) {
                ShopTabs(
                    tabs = listOf(tr("shopTabUpgrades"), tr("shopTabSkins"), tr("shopTabTrails")),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            // .shop-body : la liste défile, en laissant la place à la barre du bas.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> UpgradesTab(save, actions)
                    1 -> SkinsTab(save, actions)
                    else -> TrailsTab(save, actions)
                }
            }
        }
        Toast(message = toast, onDismiss = { toast = null })
    }
}

/** Les trois issues d'un clic en boutique, communes à tous les onglets. */
private class ShopActions(
    val purchase: (updated: GameSave, cost: Int, message: String) -> Unit,
    val notEnoughMoney: () -> Unit,
    val equip: (updated: GameSave, message: String) -> Unit,
)

/**
 * Onglet "Améliorations" (voir renderShopTab côté web) : Puissance, Vitesse,
 * puis la réparation (monde Volcan découvert, jamais sur la plage où rien
 * n'abîme la trousse), puis le billet de bus sur la plage ou, ailleurs, la
 * Trousse à Claquettes tant qu'elle n'est pas achetée.
 */
@Composable
private fun UpgradesTab(save: GameSave, actions: ShopActions) {
    val level = tr("levelShort")
    val puissanceName = tr("upgradePuissanceName")
    ShopCard(
        title = puissanceName,
        description = tr("upgradePuissanceDesc"),
        levelBadge = "$level${save.puissanceLevel}",
        leading = { Text("💪", fontSize = 30.sp) },
    ) {
        GameButton("${Economy.upgradeCost(save.puissanceLevel)} $", small = true) {
            when (val result = Shop.buyPuissance(save)) {
                is Shop.PurchaseResult.Success ->
                    actions.purchase(result.save, result.cost, "✅ $puissanceName ($level${result.save.puissanceLevel})")
                else -> actions.notEnoughMoney()
            }
        }
    }
    val vitesseName = tr("upgradeVitesseName")
    ShopCard(
        title = vitesseName,
        description = tr("upgradeVitesseDesc"),
        levelBadge = "$level${save.vitesseLevel}",
        leading = { Text("⚡", fontSize = 30.sp) },
    ) {
        GameButton("${Economy.upgradeCost(save.vitesseLevel)} $", small = true) {
            when (val result = Shop.buyVitesse(save)) {
                is Shop.PurchaseResult.Success ->
                    actions.purchase(result.save, result.cost, "✅ $vitesseName ($level${result.save.vitesseLevel})")
                else -> actions.notEnoughMoney()
            }
        }
    }

    if (save.volcanUnlocked && !save.inPlage) {
        val repairCost = Repair.cost(save)
        val done = tr("repairDone")
        val partial = tr("repairPartialDone")
        ShopCard(
            title = tr("repairName"),
            description = tr("repairDesc"),
            levelBadge = "${save.durability} / ${SkinStats.maxDurability(save)}",
            leading = { Text("🔧", fontSize = 30.sp) },
        ) {
            if (repairCost == 0) {
                GameButton(tr("repairFull"), secondary = true, small = true) {}
            } else {
                GameButton("$repairCost $", small = true) {
                    when (val result = Repair.repair(save)) {
                        is Repair.Result.Success -> actions.purchase(result.save, result.cost, done)
                        is Repair.Result.Partial -> actions.purchase(result.save, result.cost, partial)
                        Repair.Result.AlreadyFull -> Unit
                        Repair.Result.NotEnoughMoney -> actions.notEnoughMoney()
                    }
                }
            }
        }
    }

    if (save.inPlage) {
        // Billet de bus : la seule sortie de la plage.
        val free = save.hasTakenBusBack
        val bought = tr("busTicketBought")
        ShopCard(
            title = tr("busTicketName"),
            description = tr(if (free) "busTicketFreeDesc" else "busTicketDesc"),
            leading = { Text("🚌", fontSize = 30.sp) },
        ) {
            GameButton(
                if (free) tr("busTicketFreeBtn") else "${Beach.BUS_TICKET_COST} $",
                secondary = free,
                small = true,
            ) {
                when (val result = Beach.buyBusTicket(save)) {
                    is Beach.PurchaseResult.Success -> actions.purchase(result.save, Beach.busTicketCost(save), bought)
                    Beach.PurchaseResult.NotEnoughMoney -> actions.notEnoughMoney()
                }
            }
        }
    } else if (!save.hasClaquettes) {
        // Trousse à Claquettes : pas de bonus, c'est le ticket d'entrée de la Plage.
        val bought = tr("claquettesBought")
        ShopCard(
            title = tr("claquettesName"),
            description = tr("claquettesDesc"),
            leading = { Text("🩴", fontSize = 30.sp) },
        ) {
            GameButton("${Beach.CLAQUETTES_COST} $", small = true) {
                when (val result = Beach.buyClaquettes(save)) {
                    is Beach.PurchaseResult.Success -> actions.purchase(result.save, Beach.CLAQUETTES_COST, bought)
                    Beach.PurchaseResult.NotEnoughMoney -> actions.notEnoughMoney()
                }
            }
        }
    }
}

/** Onglet "Skins" : le sprite de la trousse, le nom et la description du site. */
@Composable
private fun SkinsTab(save: GameSave, actions: ShopActions) {
    for (skin in Skins.ALL) {
        val (name, desc) = skinLabel(skin.id)
        // Résolus ici : tr() ne peut pas être appelé depuis un clic.
        val boughtMsg = tr("app.bought", "name" to name)
        val equippedMsg = tr("app.equippedToast", "name" to name)
        val owned = save.ownedSkins.contains(skin.id)
        val equipped = save.equippedSkin == skin.id
        CosmeticCard(
            preview = {
                TrousseSprite(
                    skinId = skin.id,
                    contentDescription = name,
                    modifier = Modifier.size(46.dp),
                )
            },
            title = name,
            description = desc,
            owned = owned,
            equipped = equipped,
            cost = skin.cost,
            onBuy = {
                when (val result = SkinShop.buy(save, skin.id)) {
                    // Textes en dur côté site (pas dans STRINGS), comme le nom des skins.
                    is SkinShop.PurchaseResult.Success -> actions.purchase(result.save, skin.cost, boughtMsg)
                    else -> actions.notEnoughMoney()
                }
            },
            onEquip = { actions.equip(SkinShop.equip(save, skin.id), equippedMsg) },
        )
    }
}

/** Onglet "Traînées" : la pastille `.trail-swatch` tient lieu d'aperçu. */
@Composable
private fun TrailsTab(save: GameSave, actions: ShopActions) {
    for (trail in Trails.ALL) {
        val (name, desc) = trailLabel(trail.id)
        // Résolus ici : tr() ne peut pas être appelé depuis un clic.
        val boughtMsg = tr("app.bought", "name" to name)
        val equippedMsg = tr("app.equippedToast", "name" to name)
        val owned = save.ownedTrails.contains(trail.id)
        val equipped = save.equippedTrail == trail.id
        CosmeticCard(
            preview = { TrailSwatch(trail.rgb) },
            title = name,
            description = desc,
            owned = owned,
            equipped = equipped,
            cost = trail.cost,
            onBuy = {
                when (val result = TrailShop.buy(save, trail.id)) {
                    is TrailShop.PurchaseResult.Success -> actions.purchase(result.save, trail.cost, boughtMsg)
                    else -> actions.notEnoughMoney()
                }
            },
            onEquip = { actions.equip(TrailShop.equip(save, trail.id), equippedMsg) },
        )
    }
}

/**
 * Carte d'un cosmétique : bouton "Prix" si non possédé, "Équiper" si
 * possédé, "Équipée" (inerte) si déjà en place — comme buildSkinCard() et
 * buildTrailCard() côté web.
 */
@Composable
private fun CosmeticCard(
    preview: @Composable () -> Unit,
    title: String,
    description: String,
    owned: Boolean,
    equipped: Boolean,
    cost: Int,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    ShopCard(title = title, description = description, leading = preview) {
        when {
            equipped -> Text("✅ ${tr("equipped")}", color = Money, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            owned -> GameButton(tr("equip"), secondary = true, small = true, onClick = onEquip)
            else -> GameButton("$cost $", small = true, onClick = onBuy)
        }
    }
}

/** `.trail-swatch` : pastille de 46x26 qui montre la couleur de la traînée. */
@Composable
private fun TrailSwatch(rgb: String) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(shape)
            .background(trailBrush(rgb))
            .border(2.dp, PanelBorder, shape),
    )
}

/**
 * `.trail-swatch` : un dégradé qui part de la couleur du ruban et s'estompe,
 * comme le sillage en vol. "rainbow" est le cas spécial de la Traînée
 * Arc-en-ciel (voir `.trail-swatch.rainbow` côté web).
 */
private fun trailBrush(rgb: String): Brush {
    if (rgb == "rainbow") {
        return Brush.horizontalGradient(
            listOf(
                Color(0xFFFF5A5A), Color(0xFFFFD23F), Color(0xFF6BFFB0),
                Color(0xFF63C8FF), Color(0xFFC07BFF),
            ),
        )
    }
    val parts = rgb.split(",").mapNotNull { it.trim().toIntOrNull() }
    val color = if (parts.size == 3) Color(parts[0], parts[1], parts[2]) else TextColor
    return Brush.horizontalGradient(listOf(color.copy(alpha = 0.15f), color))
}
