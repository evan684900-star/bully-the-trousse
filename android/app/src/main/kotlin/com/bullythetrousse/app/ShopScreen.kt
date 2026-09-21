package com.bullythetrousse.app

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.GameSave
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

    fun purchase(updated: GameSave, cost: Int) {
        applyPurchase(updated, cost, onSaveChange)
        toast = null
    }

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
                GameButton("← Menu", secondary = true, small = true, onClick = onBackToMenu)
                GameButton("🎮 Retour au jeu", small = true, onClick = onBackToGame)
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
                    tabs = listOf("⚙️ Améliorations", "🎨 Skins", "🌈 Traînées"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
            }

            toast?.let {
                Text(
                    it,
                    color = Accent2,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                    0 -> UpgradesTab(save, ::purchase) { toast = it }
                    1 -> SkinsTab(save, onSaveChange, ::purchase) { toast = it }
                    else -> TrailsTab(save, onSaveChange, ::purchase) { toast = it }
                }
            }
        }
    }
}

/** Onglet "Améliorations" : Puissance et Vitesse (voir renderShopTab côté web). */
@Composable
private fun UpgradesTab(save: GameSave, onPurchase: (GameSave, Int) -> Unit, onToast: (String) -> Unit) {
    ShopCard(
        title = "Puissance",
        description = "Augmente la force maximale de ton lancer, et l'argent gagné à chaque lancer (+3%/niveau).",
        levelBadge = "Niv. ${save.puissanceLevel}",
        leading = { Text("💪", fontSize = 30.sp) },
    ) {
        GameButton("${Economy.upgradeCost(save.puissanceLevel)} $", small = true) {
            when (val result = Shop.buyPuissance(save)) {
                is Shop.PurchaseResult.Success -> onPurchase(result.save, result.cost)
                else -> onToast("💸 Pas assez d'argent !")
            }
        }
    }
    ShopCard(
        title = "Vitesse",
        description = "Réduit la résistance (vole plus loin), et augmente l'argent gagné à chaque lancer (+3%/niveau).",
        levelBadge = "Niv. ${save.vitesseLevel}",
        leading = { Text("⚡", fontSize = 30.sp) },
    ) {
        GameButton("${Economy.upgradeCost(save.vitesseLevel)} $", small = true) {
            when (val result = Shop.buyVitesse(save)) {
                is Shop.PurchaseResult.Success -> onPurchase(result.save, result.cost)
                else -> onToast("💸 Pas assez d'argent !")
            }
        }
    }
}

/** Onglet "Skins" : le sprite de la trousse, le nom et la description du site. */
@Composable
private fun SkinsTab(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onPurchase: (GameSave, Int) -> Unit,
    onToast: (String) -> Unit,
) {
    for (skin in Skins.ALL) {
        val (name, desc) = SKIN_LABELS[skin.id] ?: (skin.id to "")
        val owned = save.ownedSkins.contains(skin.id)
        val equipped = save.equippedSkin == skin.id
        CosmeticCard(
            preview = {
                Image(
                    painter = painterResource(R.drawable.trousse_skin_1),
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
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
                    is SkinShop.PurchaseResult.Success -> onPurchase(result.save, skin.cost)
                    else -> onToast("💸 Pas assez d'argent !")
                }
            },
            onEquip = { onSaveChange(SkinShop.equip(save, skin.id)) },
        )
    }
}

/** Onglet "Traînées" : la pastille `.trail-swatch` tient lieu d'aperçu. */
@Composable
private fun TrailsTab(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onPurchase: (GameSave, Int) -> Unit,
    onToast: (String) -> Unit,
) {
    for (trail in Trails.ALL) {
        val (name, desc) = TRAIL_LABELS[trail.id] ?: (trail.id to "")
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
                    is TrailShop.PurchaseResult.Success -> onPurchase(result.save, trail.cost)
                    else -> onToast("💸 Pas assez d'argent !")
                }
            },
            onEquip = { onSaveChange(TrailShop.equip(save, trail.id)) },
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
            equipped -> Text("✅ Équipée", color = Money, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            owned -> GameButton("Équiper", secondary = true, small = true, onClick = onEquip)
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
