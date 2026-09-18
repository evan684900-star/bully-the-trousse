package com.bullythetrousse.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Economy
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.Shop
import com.bullythetrousse.core.Skin
import com.bullythetrousse.core.SkinShop
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.Trail
import com.bullythetrousse.core.TrailShop
import com.bullythetrousse.core.Trails

/**
 * Écran boutique : niveaux Puissance/Vitesse, skins, traînées — séparé de
 * l'écran de jeu (voir GameScreen.kt/android/README.md).
 */
@Composable
fun ShopScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onBackToMenu: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedButton(onClick = onBackToMenu) { Text("🏠 Menu") }
        Text("🛍️ Boutique", style = MaterialTheme.typography.headlineMedium)
        Text("💰 ${save.money}")

        ShopRow(save = save, onPurchase = { updated, cost -> applyPurchase(updated, cost, onSaveChange) })

        Text("Skins", style = MaterialTheme.typography.titleMedium)
        SkinsRow(save = save, onSaveChange = onSaveChange, onPurchase = { updated, cost -> applyPurchase(updated, cost, onSaveChange) })

        Text("Traînées", style = MaterialTheme.typography.titleMedium)
        TrailsRow(save = save, onSaveChange = onSaveChange, onPurchase = { updated, cost -> applyPurchase(updated, cost, onSaveChange) })
    }
}

/**
 * Boutique Puissance/Vitesse, portage des deux boutons quasi-identiques de
 * la boutique web (voir Shop.buyPuissance/buyVitesse dans :core pour la
 * logique de débit/incrément exacte).
 */
@Composable
private fun ShopRow(save: GameSave, onPurchase: (GameSave, Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                val result = Shop.buyPuissance(save)
                if (result is Shop.PurchaseResult.Success) onPurchase(result.save, result.cost)
            },
        ) {
            Text("⚡ Puissance Nv.${save.puissanceLevel} (${Economy.upgradeCost(save.puissanceLevel)})")
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                val result = Shop.buyVitesse(save)
                if (result is Shop.PurchaseResult.Success) onPurchase(result.save, result.cost)
            },
        ) {
            Text("💨 Vitesse Nv.${save.vitesseLevel} (${Economy.upgradeCost(save.vitesseLevel)})")
        }
    }
}

/**
 * Liste des trousses cosmétiques, portage de buildSkinCard() côté web :
 * bouton "Prix" si pas possédée, "Équiper" si possédée mais pas équipée,
 * "Équipée" (désactivé) sinon. Défilement horizontal plutôt qu'une vraie
 * grille (voir android/README.md).
 */
@Composable
private fun SkinsRow(save: GameSave, onSaveChange: (GameSave) -> Unit, onPurchase: (GameSave, Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Skins.ALL) { skin: Skin ->
            val owned = save.ownedSkins.contains(skin.id)
            val equipped = save.equippedSkin == skin.id
            CosmeticButton(
                label = skin.id,
                owned = owned,
                equipped = equipped,
                cost = skin.cost,
                onBuy = {
                    val result = SkinShop.buy(save, skin.id)
                    if (result is SkinShop.PurchaseResult.Success) onPurchase(result.save, skin.cost)
                },
                onEquip = { onSaveChange(SkinShop.equip(save, skin.id)) },
            )
        }
    }
}

/**
 * Liste des traînées cosmétiques, portage de buildTrailCard() côté web :
 * contrairement à un skin, l'achat équipe directement (pas d'étape
 * "Équiper" séparée pour un achat).
 */
@Composable
private fun TrailsRow(save: GameSave, onSaveChange: (GameSave) -> Unit, onPurchase: (GameSave, Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Trails.ALL) { trail: Trail ->
            val owned = save.ownedTrails.contains(trail.id)
            val equipped = save.equippedTrail == trail.id
            CosmeticButton(
                label = trail.id,
                owned = owned,
                equipped = equipped,
                cost = trail.cost,
                onBuy = {
                    val result = TrailShop.buy(save, trail.id)
                    if (result is TrailShop.PurchaseResult.Success) onPurchase(result.save, trail.cost)
                },
                onEquip = { onSaveChange(TrailShop.equip(save, trail.id)) },
            )
        }
    }
}

@Composable
private fun CosmeticButton(label: String, owned: Boolean, equipped: Boolean, cost: Int, onBuy: () -> Unit, onEquip: () -> Unit) {
    when {
        equipped -> OutlinedButton(onClick = {}, enabled = false) { Text("✅ $label") }
        owned -> Button(onClick = onEquip) { Text(label) }
        else -> Button(onClick = onBuy) { Text("$label ($cost)") }
    }
}
