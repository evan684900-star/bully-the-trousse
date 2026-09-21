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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Écrans ouverts depuis le menu (Succès, Défis, Classement, Profil). Côté
 * web ce sont des modales (`#achievements-modal`, `#challenges-modal`) ou
 * des écrans (`#screen-leaderboard`, `#screen-profile`) ; ils reprennent
 * ici le fond `--app-bg` et les cartes `.achievement-row` du site.
 */
@Composable
private fun ModalScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
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
fun SettingsScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onBack: () -> Unit) {
    ModalScreen("⚙️ Réglages", onBack) {
        SettingsRow("Musique") {
            GameButton(
                if (save.musicMuted) "🔇" else "🔊",
                secondary = true,
                small = true,
            ) { onSaveChange(save.copy(musicMuted = !save.musicMuted)) }
        }
        SettingsRow("Thème") { GameButton("🌙", secondary = true, small = true) {} }
        SettingsRow("Langue") { GameButton("FR", secondary = true, small = true) {} }
    }
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
 * Classement : côté web il vient de Firestore (`#screen-leaderboard`), qui
 * n'est pas encore branché dans l'app (voir FirebaseSaveRepository.kt et
 * android/README.md) — l'écran existe donc, mais sans données pour l'instant.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    ModalScreen("🏆 Classement", onBack) {
        Text(
            "Le classement a besoin d'un compte en ligne (Firebase), pas encore branché dans l'app.",
            color = TextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Profil (`#screen-profile` côté web) : pseudo, avatar, stats détaillées. */
@Composable
fun ProfileScreen(save: GameSave, onBack: () -> Unit) {
    ModalScreen("👤 Profil", onBack) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileRow("Pseudo", save.pseudo.ifBlank { "—" })
            ProfileRow("Lancers", save.totalThrows.toString())
            ProfileRow("Record", "${"%.1f".format(save.bestDistance)} m")
            ProfileRow("Record plage", "${"%.1f".format(save.plageBestDistance)} m")
            ProfileRow("Argent gagné", "${save.totalMoneyEarned} $")
            ProfileRow("Succès", "${save.unlockedAchievements.size} / ${Achievements.ALL.size}")
        }
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
