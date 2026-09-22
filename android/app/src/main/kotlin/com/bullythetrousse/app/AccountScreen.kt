package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Écran Compte, porté de la partie « code de récupération » des réglages du
 * site.
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

    ModalScreen("☁️ Compte", onBack) {
        InfoCard {
            Text(
                session.statusText,
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
        }

        // --- Mon code ---
        InfoCard {
            Text("Mon code", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Note-le quelque part : c'est lui qui te rend ta partie sur un autre " +
                    "appareil, ou sur le site.",
                color = TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            val code = revealedCode
            if (code == null) {
                GameButton("Afficher mon code", small = true) {
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
            }
        }

        // --- Rejoindre un compte ---
        InfoCard {
            Text("Utiliser un code", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
            GameButton(if (busy) "…" else "Se connecter", small = true) {
                if (busy) return@GameButton
                busy = true
                message = null
                scope.launch {
                    message = session.joinAccount(typedCode, repository, save, onSaveChange)
                        ?: "Compte rejoint : ta partie est à jour."
                    busy = false
                }
            }
        }

        message?.let {
            Text(it, color = TextColor, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

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
