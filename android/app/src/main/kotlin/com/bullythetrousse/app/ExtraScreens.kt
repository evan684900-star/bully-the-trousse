@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.GraphicsQuality
import com.bullythetrousse.core.SkinStats

/**
 * Écrans ouverts depuis le menu (Succès, Défis, Classement, Profil). Côté
 * web ce sont des modales (`#achievements-modal`, `#challenges-modal`) ou
 * des écrans (`#screen-leaderboard`, `#screen-profile`) ; ils reprennent
 * ici le fond `--app-bg` et les cartes `.achievement-row` du site.
 */
@Composable
internal fun ModalScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
        }
        Text(title, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        content()
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(ButtonSecondary)
            .clickable(onClick = onBack)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text("← Retour", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Les 47 succès du jeu. `:core` ne porte que leur id et leur émoji (pas
 * encore les libellés traduits du web, voir les clés `achv*Name` dans
 * index.html), donc on affiche pour l'instant la grille des émojis :
 * débloqués en clair, verrouillés estompés comme `.achievement-row.locked`.
 */
@Composable
fun AchievementsScreen(save: GameSave, onBack: () -> Unit) {
    ModalScreen("🏆 Succès", onBack) {
        Text(
            "${save.unlockedAchievements.size} / ${Achievements.ALL.size} débloqués",
            color = TextDim,
            fontSize = 13.sp,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (achievement in Achievements.ALL) {
                val unlocked = achievement.id in save.unlockedAchievements
                Box(
                    modifier = Modifier
                        .alpha(if (unlocked) 1f else 0.5f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(2.dp, if (unlocked) Accent else PanelBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(achievement.emoji, fontSize = 26.sp)
                }
            }
        }
    }
}

/** Les défis du jour (`#challenges-modal` côté web). */
@Composable
fun ChallengesScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onBack: () -> Unit) {
    ModalScreen("📅 Défis du jour", onBack) {
        if (save.dailyChallenges.isEmpty()) {
            Text(
                "Les défis du jour apparaissent dès ton premier lancer ou premier achat de la journée.",
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            return@ModalScreen
        }
        save.dailyChallenges.forEachIndexed { index, challenge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.7f)) {
                    Text(challenge.kind, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${challenge.progress.toInt()} / ${challenge.target.toInt()}   +${challenge.reward} $",
                        color = TextDim,
                        fontSize = 11.5.sp,
                    )
                }
                when {
                    challenge.claimed -> Text("✅", fontSize = 20.sp)
                    challenge.progress >= challenge.target -> Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Accent)
                            .clickable {
                                val result = DailyChallenges.claim(save, index)
                                if (result is DailyChallenges.ClaimResult.Success) onSaveChange(result.save)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Réclamer", color = OnAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    else -> Text("${(challenge.progress / challenge.target * 100).toInt()} %", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * `#settings-modal` : thème, langue, et le rappel du tutoriel. Le thème
 * clair et le bilingue ne sont pas encore portés, donc leurs boutons
 * affichent l'état courant sans le changer.
 */
@Composable
fun SettingsScreen(
    save: GameSave,
    session: CloudSession,
    onSaveChange: (GameSave) -> Unit,
    onOpenAccount: () -> Unit,
    onBack: () -> Unit,
) {
    ModalScreen("⚙️ Réglages", onBack) {
        SettingsRow("Compte") {
            GameButton("☁️ Gérer", secondary = true, small = true, onClick = onOpenAccount)
        }
        Text(
            session.statusText,
            color = if (session.state == CloudState.LINKED) Money else TextDim,
            fontSize = 11.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        SettingsRow("Musique") {
            GameButton(
                if (save.musicMuted) "🔇" else "🔊",
                secondary = true,
                small = true,
            ) { onSaveChange(save.copy(musicMuted = !save.musicMuted)) }
        }
        // Qualité : un bouton qui fait défiler Basse → Normale → Élevée.
        // Propre au portage Android — le site n'a pas ce réglage, mais un
        // téléphone d'entrée de gamme en a besoin.
        val quality = GraphicsQuality.fromId(save.graphicsQuality)
        SettingsRow("Qualité graphique") {
            GameButton(quality.label, secondary = true, small = true) {
                onSaveChange(save.copy(graphicsQuality = GraphicsQuality.next(quality).id))
            }
        }
        Text(
            qualityHint(quality),
            color = TextDim,
            fontSize = 11.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        SettingsRow("Thème") { GameButton("🌙", secondary = true, small = true) {} }
        SettingsRow("Langue") { GameButton("FR", secondary = true, small = true) {} }
    }
}

/** Ce que chaque niveau change, en une ligne : sans ça le bouton ne dit pas
 *  au joueur ce qu'il gagne ou perd en le touchant. */
private fun qualityHint(quality: GraphicsQuality): String = when (quality) {
    GraphicsQuality.LOW ->
        "Effets d'ambiance coupés et particules réduites : à choisir si le jeu saccade."
    GraphicsQuality.MEDIUM -> "Tous les effets, en quantité mesurée. Recommandé."
    GraphicsQuality.HIGH ->
        "Particules, nuages en profondeur et sillages au maximum. Pour les téléphones à l'aise."
}

/** `.settings-row` : libellé à gauche, contrôle à droite. */
@Composable
private fun SettingsRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        control()
    }
}

/**
 * `#changelog-modal` : le journal des changements, ouvert en touchant le
 * numéro de version du menu. Les entrées majeures sont mises en avant en
 * doré, les mineures en gris, comme côté web.
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    ModalScreen("📋 Journal des changements", onBack) {
        for (entry in CHANGELOG) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("v${entry.version}", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                for (line in entry.major) {
                    Text("• $line", color = TextColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                for (line in entry.minor) {
                    Text("• $line", color = TextDim, fontSize = 12.sp)
                }
            }
        }
    }
}

/** `#links-modal` : les deux liens du site, repris tels quels. */
@Composable
fun LinksScreen(onBack: () -> Unit) {
    ModalScreen("🔗 Mes liens", onBack) {
        LinkRow("🌐 evyverse.vercel.app", "Mon site")
        LinkRow("💬 Chaîne WhatsApp", "Actus de ce jeu")
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = TextDim, fontSize = 12.sp)
    }
}

/**
 * Classement (`#screen-leaderboard`) : les mêmes documents Firestore que le
 * site, donc les mêmes joueurs et les mêmes records — voir [FirebaseBridge].
 * Sans compte en ligne configuré, ou hors connexion, l'écran le dit au lieu
 * de rester vide.
 */
@Composable
fun LeaderboardScreen(save: GameSave, session: CloudSession, onBack: () -> Unit) {
    // Le monde Plage a son propre classement (`scoresCollection()` côté site).
    val collection = remember(save.inPlage) { session.bridge.scoresCollectionFor(save) }
    var entries by remember { mutableStateOf<List<LeaderboardEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(collection, session.state) {
        if (session.state == CloudState.NOT_CONFIGURED || session.state == CloudState.OFFLINE) return@LaunchedEffect
        try {
            entries = session.bridge.loadLeaderboard(collection)
        } catch (e: Exception) {
            failed = true
        }
    }

    ModalScreen(if (save.inPlage) "🏖️ Classement Plage" else "🏆 Classement", onBack) {
        Text(
            "Ton pseudo : ${save.pseudo.ifBlank { "-" }}",
            color = TextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        val rows = entries
        when {
            session.state == CloudState.NOT_CONFIGURED -> LeaderboardNotice(
                "Le classement a besoin du compte en ligne, pas encore configuré dans cette version.",
            )
            failed || session.state == CloudState.OFFLINE -> LeaderboardNotice(
                "Classement indisponible : pas de connexion.",
            )
            rows == null -> LeaderboardNotice("Chargement du classement…")
            rows.isEmpty() -> LeaderboardNotice("Personne n'a encore de record. À toi de jouer.")
            else -> rows.forEachIndexed { index, entry ->
                LeaderboardRow(rank = index + 1, entry = entry, isMe = entry.uid == session.uid)
            }
        }
    }
}

@Composable
private fun LeaderboardNotice(text: String) {
    Text(text, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
}

/** `.leaderboard-row` : rang, pseudo, distance — la ligne du joueur est
 *  soulignée en doré, comme sur le site. */
@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(2.dp, if (isMe) Accent else PanelBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#$rank",
            color = if (isMe) Accent else TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            entry.pseudo,
            color = TextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            "${"%.1f".format(entry.distanceMeters)} m",
            color = Money,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/**
 * Profil, porté de `#screen-profile` / `paintProfile()` : l'en-tête
 * (avatar rond bordé de doré, pseudo, statut), la rangée de trois cartes
 * `.profile-card` (trousse équipée + nombre de skins, abonnements,
 * abonnés), puis les statistiques détaillées.
 *
 * Abonnements/abonnés viennent de Firestore, comme sur le site.
 */
@Composable
fun ProfileScreen(save: GameSave, session: CloudSession, onBack: () -> Unit) {
    // Abonnements/abonnés : deux comptages Firestore (`countFollowers()` /
    // `countFollowing()` côté site). Tant qu'ils n'ont pas répondu — ou si le
    // compte en ligne n'est pas disponible — on laisse le tiret du site.
    var following by remember { mutableStateOf<Int?>(null) }
    var followers by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(session.uid) {
        val id = session.uid ?: return@LaunchedEffect
        try {
            following = session.bridge.countFollowing(id)
            followers = session.bridge.countFollowers(id)
        } catch (e: Exception) {
            // Hors ligne : on garde le tiret, ce n'est pas une erreur à montrer.
        }
    }

    ModalScreen("👤 Profil", onBack) {
        // .profile-header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(CardBg)
                    .border(3.dp, Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(save.avatarEmoji.ifBlank { "🎒" }, fontSize = 40.sp)
            }
            Text(
                save.pseudo.ifBlank { "?" },
                color = TextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            // .profile-status : toujours "En ligne" pour SON PROPRE profil côté
            // site (profileOnline, sans vérifier de seuil — ce seuil ne sert
            // qu'à afficher le profil d'un AUTRE joueur, pas encore porté ici).
            val isOnline = session.state == CloudState.GUEST || session.state == CloudState.LINKED
            Text(
                if (isOnline) "🟢 En ligne" else "Hors ligne",
                color = if (isOnline) Money else TextDim,
                fontSize = 12.5.sp,
                fontWeight = if (isOnline) FontWeight.Bold else FontWeight.Normal,
            )
        }

        // .profile-top-row : trois cartes côte à côte.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileCard(modifier = Modifier.weight(1f)) {
                TrousseSprite(
                    skinId = save.equippedSkin,
                    contentDescription = "trousse équipée",
                    modifier = Modifier.size(40.dp),
                )
                Text("+${save.ownedSkins.size}", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            ProfileCard(modifier = Modifier.weight(1f)) {
                Text(
                    following?.toString() ?: "—",
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("Abonnements", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            ProfileCard(modifier = Modifier.weight(1f)) {
                Text(
                    followers?.toString() ?: "—",
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("Abonnés", color = TextDim, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileRow("Lancers", save.totalThrows.toString())
            ProfileRow("Record", "${"%.1f".format(save.bestDistance)} m")
            ProfileRow("Record plage", "${"%.1f".format(save.plageBestDistance)} m")
            ProfileRow("Argent gagné", "${save.totalMoneyEarned} $")
            ProfileRow("Succès", "${save.unlockedAchievements.size} / ${Achievements.ALL.size}")
            ProfileRow("Puissance", SkinStats.totalPuissance(save).toString())
            ProfileRow("Vitesse", SkinStats.totalVitesse(save).toString())
        }
    }
}

/** `.profile-card` : carte carrée, contenu centré. */
@Composable
private fun ProfileCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
