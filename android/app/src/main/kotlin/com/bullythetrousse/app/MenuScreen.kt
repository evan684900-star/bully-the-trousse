package com.bullythetrousse.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.Beach
import com.bullythetrousse.core.DailyChallenge
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SkinStats

/**
 * Écran d'accueil : résumé (argent, record, succès), sélecteur de monde
 * (portage de buildWorldsRow()/handleWorldCardClick() côté web) et défis du
 * jour. Sert de point de départ vers l'écran Jeu et l'écran Boutique.
 */
@Composable
fun MenuScreen(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onPlay: () -> Unit,
    onOpenShop: () -> Unit,
    onStartVolcanoCinematic: () -> Unit,
    onStartBeachCinematic: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("🎒 Bully the Trousse", style = MaterialTheme.typography.headlineMedium)

        val displayedRecord = if (save.currentWorld == "plage") save.plageBestDistance else save.bestDistance
        Text("💰 ${save.money}   Record : ${"%.1f".format(displayedRecord)} m")
        Text("Puissance ${SkinStats.totalPuissance(save)}   Vitesse ${SkinStats.totalVitesse(save)}")
        Text("🏆 ${save.unlockedAchievements.size} / ${Achievements.ALL.size} succès débloqués")

        WorldSelector(
            save = save,
            onSaveChange = onSaveChange,
            onStartVolcanoCinematic = onStartVolcanoCinematic,
            onStartBeachCinematic = onStartBeachCinematic,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = onPlay) { Text("▶️ Jouer") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onOpenShop) { Text("🛍️ Boutique") }
        }

        DailyChallengesPanel(save = save, onSaveChange = onSaveChange)
    }
}

/**
 * Sélecteur de monde, portage de buildWorldsRow()/handleWorldCardClick()
 * côté web : Cour toujours accessible, Volcan/Plage verrouillés tant que
 * leur cinématique n'est pas réussie, et impossible de changer de monde
 * tant qu'on est "coincé" sur la plage sans avoir payé le bus du retour.
 */
@Composable
private fun WorldSelector(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onStartVolcanoCinematic: () -> Unit,
    onStartBeachCinematic: () -> Unit,
) {
    var message by remember(save.inPlage, save.currentWorld) { mutableStateOf<String?>(null) }

    fun selectWorld(worldId: String) {
        message = null
        if (save.inPlage && worldId != "plage") {
            message = "🚌 Tu es coincé sur la plage tant que tu n'as pas repris le bus."
            return
        }
        when (worldId) {
            "plage" -> when {
                !save.hasClaquettes -> message = "🩴 Achète d'abord la Trousse à Claquettes ci-dessous."
                !save.plageUnlocked -> onStartBeachCinematic()
                else -> onSaveChange(Beach.enter(save))
            }
            "volcans" -> when {
                !save.volcanUnlocked -> {
                    val cooldownMs = save.volcanFailedUntil - System.currentTimeMillis()
                    if (cooldownMs > 0) {
                        val minutes = cooldownMs / 60_000
                        val seconds = (cooldownMs % 60_000) / 1000
                        message = "🌋 Le volcan gronde encore... réessaie dans $minutes min $seconds s"
                    } else {
                        onStartVolcanoCinematic()
                    }
                }
                else -> onSaveChange(save.copy(currentWorld = "volcans"))
            }
            else -> onSaveChange(save.copy(currentWorld = "cour"))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorldButton("🏫 Cour", selected = save.currentWorld == "cour", modifier = Modifier.weight(1f)) { selectWorld("cour") }
            WorldButton(
                if (save.volcanUnlocked) "🌋 Volcan" else "🔒 Volcan",
                selected = save.currentWorld == "volcans",
                modifier = Modifier.weight(1f),
            ) { selectWorld("volcans") }
            WorldButton(
                if (save.plageUnlocked) "🏖️ Plage" else "🔒 Plage",
                selected = save.currentWorld == "plage",
                modifier = Modifier.weight(1f),
            ) { selectWorld("plage") }
        }
        message?.let { Text(it) }

        if (save.inPlage) {
            Button(
                onClick = {
                    val result = Beach.buyBusTicket(save)
                    if (result is Beach.PurchaseResult.Success) applyPurchase(result.save, Beach.BUS_TICKET_COST, onSaveChange)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("🚌 Reprendre le bus (${Beach.BUS_TICKET_COST}$)") }
        } else if (!save.hasClaquettes) {
            Button(
                onClick = {
                    val result = Beach.buyClaquettes(save)
                    if (result is Beach.PurchaseResult.Success) applyPurchase(result.save, Beach.CLAQUETTES_COST, onSaveChange)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("🩴 Trousse à Claquettes (${Beach.CLAQUETTES_COST}$)") }
        }
    }
}

@Composable
private fun WorldButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

/**
 * Panneau des défis quotidiens, portage minimal de la carte "défis" du menu
 * web : un défi par ligne (progression, cible, bouton "Réclamer" une fois
 * la cible atteinte). Les défis se génèrent tout seuls au premier lancer/
 * achat de la journée (voir DailyChallenges.ensure(), appelé dans
 * applyPurchase()/l'effet de résultat du lancer) — cette liste peut donc
 * être vide tant qu'aucun des deux n'a encore eu lieu aujourd'hui.
 */
@Composable
private fun DailyChallengesPanel(save: GameSave, onSaveChange: (GameSave) -> Unit) {
    if (save.dailyChallenges.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📅 Défis du jour", style = MaterialTheme.typography.titleSmall)
        save.dailyChallenges.forEachIndexed { index, challenge: DailyChallenge ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${challenge.kind} : ${challenge.progress.toInt()}/${challenge.target.toInt()} (+${challenge.reward}$)",
                    modifier = Modifier.weight(1f),
                )
                when {
                    challenge.claimed -> Text("✅")
                    challenge.progress >= challenge.target -> Button(onClick = {
                        val result = DailyChallenges.claim(save, index)
                        if (result is DailyChallenges.ClaimResult.Success) onSaveChange(Achievements.apply(result.save))
                    }) { Text("Réclamer") }
                    else -> Unit
                }
            }
        }
    }
}
