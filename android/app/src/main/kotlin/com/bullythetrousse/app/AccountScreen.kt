@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.RecoveryCode
import kotlinx.coroutines.launch

/**
 * Écran Compte, porté de la section « Paramètres du compte » / « Mon compte »
 * des réglages du site (`#settings-modal`) : statut de connexion, code de
 * récupération (afficher/créer, rejoindre), compte privé, photo de profil,
 * déconnexion.
 *
 * L'idée à faire passer au joueur, et qui guide la mise en page : **le code
 * n'est pas un mot de passe, c'est son compte**. Le même code ouvre la même
 * partie sur le site et sur le téléphone — même argent, même classement,
 * même profil.
 */
@Composable
fun AccountScreen(
    save: GameSave,
    session: CloudSession,
    repository: SaveRepository,
    onSaveChange: (GameSave) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var revealedCode by remember { mutableStateOf(save.recoveryCode.takeIf { RecoveryCode.isValid(it) }) }
    var typedCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    ModalScreen("☁️ Compte", onBack) {
        // --- Statut : "account-status" côté site ---
        InfoCard {
            Text(
                tr(session.statusKey),
                color = if (session.state == CloudState.LINKED) Money else TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (session.state == CloudState.NOT_CONFIGURED) {
                Text(
                    "Cette version de l'app n'a pas encore le fichier de configuration " +
                        "Firebase. Le jeu fonctionne, mais le classement et la sauvegarde " +
                        "en ligne sont coupés.",
                    color = TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            // Une tentative de connexion peut échouer sans que ce soit
            // définitif (réseau pas encore prêt) : quelques essais
            // automatiques suivent déjà (voir rememberCloudSession), ce
            // bouton couvre le cas où ils ont tous échoué.
            if (session.state == CloudState.OFFLINE) {
                GameButton("🔄 Réessayer", secondary = true, small = true) {
                    session.retryConnection()
                }
            }
        }

        // --- Mon code ---
        InfoCard {
            Text("🔑 Mon compte", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Ce code EST ton compte : entre-le sur un autre appareil et tu joues sur " +
                    "la même partie, synchronisée dans les deux sens. Note-le quelque part, " +
                    "c'est le seul moyen de retrouver ta partie si ce téléphone efface ses données.",
                color = TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            val code = revealedCode
            if (code == null) {
                GameButton("🔑 Créer / afficher mon code", small = true) {
                    if (busy) return@GameButton
                    busy = true
                    scope.launch {
                        revealedCode = session.revealOrCreateCode(save, onSaveChange)
                        busy = false
                    }
                }
            } else {
                Text(
                    RecoveryCode.format(code),
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                GameButton("📋 Copier", secondary = true, small = true) {
                    clipboard.setText(AnnotatedString(code))
                    message = "Code copié."
                }
                Text(
                    "⚠️ Garde-le pour toi : qui a ce code entre dans ton compte.",
                    color = TextDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // --- Rejoindre un compte ---
        InfoCard {
            Text("📥 Utiliser un code", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Entre le code de ta partie du site pour la retrouver ici. " +
                    "La partie du compte remplacera celle de ce téléphone.",
                color = TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = typedCode,
                onValueChange = { typedCode = RecoveryCode.normalize(it).take(RecoveryCode.DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("16 chiffres", color = TextDim, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            GameButton(if (busy) "…" else "📥 Connecter cet appareil", small = true) {
                if (busy) return@GameButton
                if (!RecoveryCode.isValid(typedCode)) {
                    message = "Un code fait 16 chiffres."
                    return@GameButton
                }
                busy = true
                message = null
                scope.launch {
                    message = session.joinAccount(typedCode, repository, save, onSaveChange)
                        ?: "Compte rejoint : ta partie est à jour."
                    busy = false
                }
            }
        }

        // --- Compte privé (chk-private-account côté site) ---
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Compte privé", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                GameButton(
                    if (save.isPrivate) "🔒 Activé" else "🔓 Désactivé",
                    secondary = !save.isPrivate,
                    small = true,
                    onClick = { onSaveChange(save.copy(isPrivate = !save.isPrivate)) },
                )
            }
            Text(
                "Les autres joueurs verront une banderole \"Ce compte est privé\" au lieu " +
                    "de ton profil. Ton score reste visible dans le classement.",
                color = TextDim,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
            )
        }

        // --- Photo de profil (AVATAR_EMOJIS côté site) ---
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🖼️ Photo de profil", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(2.dp, Accent, CircleShape)
                        .clickable { showAvatarPicker = !showAvatarPicker },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(save.avatarEmoji.ifBlank { "🎒" }, fontSize = 20.sp)
                }
            }
            if (showAvatarPicker) {
                FlowRowCentered(gap = 8.dp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    for (emoji in AVATAR_EMOJIS) {
                        val selected = save.avatarEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selected) Accent.copy(alpha = 0.25f) else CardBg)
                                .border(2.dp, if (selected) Accent else PanelBorder, CircleShape)
                                .clickable {
                                    onSaveChange(save.copy(avatarEmoji = emoji))
                                    showAvatarPicker = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        message?.let {
            Text(it, color = TextColor, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        // --- Déconnexion (btn-logout côté site) ---
        InfoCard {
            GameButton("🚪 Se déconnecter", secondary = true, small = true) { showLogoutConfirm = true }
            Text(
                if (RecoveryCode.isValid(save.recoveryCode)) {
                    "Ton compte reste accessible avec ton code sur n'importe quel appareil. " +
                        "Cet appareil, lui, repart avec un compte tout neuf."
                } else {
                    "Tu n'as pas encore de code : repartir de zéro sur cet appareil perd " +
                        "cette partie pour de bon."
                },
                color = TextDim,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "🚪 Se déconnecter ?",
            text = if (RecoveryCode.isValid(save.recoveryCode)) {
                "Cet appareil va repartir de zéro. Tu pourras retrouver cette partie " +
                    "n'importe quand avec ton code."
            } else {
                "Tu vas repartir de zéro sur cet appareil, avec un compte tout neuf. " +
                    "Sans code de récupération, cette partie sera perdue pour de bon."
            },
            confirmLabel = "🚪 Se déconnecter",
            onConfirm = {
                showLogoutConfirm = false
                scope.launch {
                    // onSaveChange (updateSave) écrit déjà sur disque : pas besoin
                    // d'un repository.save() séparé ici.
                    val fresh = session.logout(save)
                    onSaveChange(fresh)
                    onBack()
                }
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

/** `AVATAR_EMOJIS` côté site : le choix d'icônes de profil, dans le même ordre. */
private val AVATAR_EMOJIS = listOf(
    "🎒", "🚀", "🌋", "🏖️", "🏙️", "🏫",
    "💰", "🏆", "🥇", "🎯", "🔥", "❄️",
    "⚡", "💪", "🎨", "🌈", "🪙", "🛸",
    "🌠", "✨", "🎮", "😎", "🦄", "🐉",
)

/** Une carte d'information, même habillage que `.settings-row` mais en
 *  colonne : ces blocs portent du texte explicatif, pas une seule ligne. */
@Composable
internal fun InfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/**
 * `#logout-confirm-modal` : une confirmation simple par-dessus l'écran
 * courant, avant une action qu'on ne veut pas déclencher par un tap
 * accidentel.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ShopBg)
                .border(2.dp, PanelBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text(text, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(confirmLabel, small = true, onClick = onConfirm)
                GameButton("Annuler", secondary = true, small = true, onClick = onDismiss)
            }
        }
    }
}
