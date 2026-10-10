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
import androidx.compose.runtime.rememberUpdatedState
import com.bullythetrousse.core.AccountRules
import com.bullythetrousse.core.I18n
import com.bullythetrousse.core.MergeChoice
import com.bullythetrousse.core.SaveSummary

/**
 * Écran Compte, porté de la section « Paramètres du compte » / « Mon compte »
 * des réglages du site (`#settings-modal`) : statut de connexion, code du
 * compte (afficher, copier, régénérer), connexion à un autre compte avec un
 * code, compte privé, photo de profil, déconnexion.
 *
 * L'idée à faire passer au joueur, et qui guide la mise en page : **le code
 * n'est pas un mot de passe, c'est son compte**. Le même code ouvre la même
 * partie sur le site et sur le téléphone — même argent, même classement,
 * même profil. Le code affiché est toujours celui du compte connecté (lu
 * dans son e-mail), masqué tant qu'on ne demande pas à le voir.
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
    val toaster = LocalToaster.current
    val lang = LocalLang.current
    // Ancien code relié entre-temps à un autre compte : proposé tel quel.
    val suggestion = session.joinSuggestion.takeIf { it.isNotEmpty() && it != session.accountCode }
    var typedCode by remember { mutableStateOf(suggestion.orEmpty()) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var codeErrorKey by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var codeVisible by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    // Code vérifié en attente du choix « quelle partie garder ? ».
    var pending by remember { mutableStateOf<Pair<String, CodeLookup>?>(null) }
    val currentSave by rememberUpdatedState(save)

    // Messages résolus ici : tr() ne peut pas être appelé depuis une coroutine.
    val msgConnected = I18n.tr("accountConnected", lang)
    val msgRestored = I18n.tr("recoveryDone", lang)
    val msgRegenDone = I18n.tr("regenDone", lang)
    val msgLinked = I18n.tr("accountLinked", lang)
    val msgCopied = tr("recoveryCopied")

    /** Connecte l'appareil au code vérifié, avec la partie choisie. */
    fun confirm(code: String, lookup: CodeLookup, choice: MergeChoice) {
        busy = true
        scope.launch {
            val error = when (lookup) {
                is CodeLookup.Account -> session.joinAccount(code, choice, repository, currentSave, onSaveChange)
                is CodeLookup.Legacy -> session.restoreLegacy(code, lookup, choice, currentSave, onSaveChange)
            }
            if (error == null) {
                typedCode = ""
                codeVisible = false
                toaster(if (lookup is CodeLookup.Account) msgConnected else msgRestored)
            } else {
                errorKey = error
            }
            busy = false
        }
    }

    ModalScreen(tr("settingsAccountTitle"), onBack) {
        // --- Statut : "account-status" côté site ---
        InfoCard {
            Text(
                tr(session.statusKey),
                color = when (session.state) {
                    CloudState.LINKED -> MoneyText
                    CloudState.REVOKED -> AccentText
                    else -> TextDim
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (session.state == CloudState.NOT_CONFIGURED) {
                Text(tr("app.accountNotConfigured"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            // Quelques essais automatiques suivent déjà un échec (voir
            // rememberCloudSession) ; ce bouton couvre le cas où ils ont tous échoué.
            if (session.state == CloudState.OFFLINE) {
                GameButton(tr("app.accountRetry"), secondary = true, small = true) { session.retryConnection() }
            }
        }

        // --- 🔑 Mon compte ---
        InfoCard {
            Text(tr("settingsRecoveryTitle"), color = AccentText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(tr("settingsRecoveryHint"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            val code = session.accountCode
            if (code.isNotEmpty()) {
                // #recovery-code-value : masqué tant qu'on ne demande pas à le voir.
                Text(
                    if (codeVisible) RecoveryCode.format(code) else MASKED_CODE,
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameButton(
                        tr(if (codeVisible) "btnRecoveryHide" else "btnRecoveryReveal"),
                        secondary = true,
                        small = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        codeVisible = !codeVisible
                        if (codeVisible) session.markCodeShown(currentSave, onSaveChange)
                    }
                    GameButton(tr("btnRecoveryCopy"), secondary = true, small = true, modifier = Modifier.weight(1f)) {
                        clipboard.setText(AnnotatedString(code))
                        session.markCodeShown(currentSave, onSaveChange)
                        toaster(msgCopied)
                    }
                    GameButton(tr("btnRecoveryRegen"), secondary = true, small = true, modifier = Modifier.weight(1f)) {
                        codeErrorKey = null
                        showRegenConfirm = true
                    }
                }
                Text(tr("settingsRecoveryWarn"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else if (session.state == CloudState.GUEST || session.state == CloudState.LINKED) {
                // Compte connecté mais encore sans code (liaison automatique
                // échouée, ancien compte à vrai e-mail).
                GameButton(if (busy) "…" else tr("btnRecoveryCreate"), small = true) {
                    if (busy) return@GameButton
                    busy = true
                    codeErrorKey = null
                    scope.launch {
                        if (session.createCode({ currentSave }, onSaveChange)) {
                            codeVisible = true
                            toaster(msgLinked)
                        } else {
                            codeErrorKey = "codeCreateError"
                        }
                        busy = false
                    }
                }
            }
            codeErrorKey?.let { Text(tr(it), color = Color(0xFFFF6B6B), fontSize = 12.sp, textAlign = TextAlign.Center) }

            // .account-form : j'ai déjà un code.
            OutlinedTextField(
                value = typedCode,
                onValueChange = { typedCode = RecoveryCode.normalize(it).take(RecoveryCode.DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(tr("settingsRecoveryPlaceholder"), color = TextDim, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (suggestion != null && typedCode == suggestion) {
                Text(tr("settingsRecoverySuggest"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            errorKey?.let { Text(tr(it), color = Color(0xFFFF6B6B), fontSize = 12.sp, textAlign = TextAlign.Center) }
            GameButton(if (busy) "…" else tr("btnRecoverySubmit"), small = true) {
                if (busy) return@GameButton
                errorKey = null
                // recoverFromCode() : format, hors ligne, déjà ce compte-là.
                when {
                    !RecoveryCode.isValid(typedCode) -> errorKey = "recoveryErrFormat"
                    session.state == CloudState.NOT_CONFIGURED -> errorKey = "recoveryErrOffline"
                    typedCode == session.accountCode -> errorKey = "recoveryErrSameAccount"
                    else -> {
                        busy = true
                        val code = typedCode
                        scope.launch {
                            val found = runCatching { session.lookupCode(code) }
                            busy = false
                            found.onSuccess { lookup ->
                                if (lookup == null) {
                                    errorKey = "recoveryErrUnknown"
                                } else if (!AccountRules.hasProgress(currentSave)) {
                                    // Rien de joué sur cet appareil : inutile de demander.
                                    confirm(code, lookup, MergeChoice.OTHER)
                                } else {
                                    pending = code to lookup
                                }
                            }.onFailure { e ->
                                errorKey = if (FirebaseBridge.isRetryable(e)) "recoveryErrRetry" else "recoveryErrWrongCode"
                            }
                        }
                    }
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
                Text(tr("settingsPrivateLabel"), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val on = tr("settingsPrivateOn")
                val off = tr("settingsPrivateOff")
                GameButton(
                    tr(if (save.isPrivate) "app.privateOn" else "app.privateOff"),
                    secondary = !save.isPrivate,
                    small = true,
                    onClick = {
                        onSaveChange(save.copy(isPrivate = !save.isPrivate))
                        toaster(if (save.isPrivate) off else on)
                    },
                )
            }
            Text(tr("settingsPrivateHint"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        // --- Photo de profil (AVATAR_EMOJIS côté site) ---
        InfoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("settingsAvatarTitle"), color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                Text(tr("settingsAvatarPickHint"), color = TextDim, fontSize = 12.sp)
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

        // --- Déconnexion (btn-logout côté site) ---
        InfoCard {
            GameButton(tr("btnLogout"), secondary = true, small = true) { showLogoutConfirm = true }
            Text(tr("settingsLogoutHint"), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }

    // Hors de ModalScreen : ces fenêtres couvrent l'écran entier.
    pending?.let { (code, lookup) ->
        MergeChoiceDialog(
            current = SaveSummary.of(save),
            other = SaveSummary.of(lookup.save),
            onChoose = { choice ->
                pending = null
                confirm(code, lookup, choice)
            },
            onCancel = { pending = null },
        )
    }

    if (showRegenConfirm) {
        InfoDialog(
            title = tr("regenConfirmTitle"),
            onDismiss = { showRegenConfirm = false },
            buttons = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton(tr("regenConfirmYes"), modifier = Modifier.weight(1f)) {
                        showRegenConfirm = false
                        if (busy) return@GameButton
                        busy = true
                        scope.launch {
                            if (session.regenerateCode({ currentSave }, onSaveChange)) {
                                codeVisible = true
                                toaster(msgRegenDone)
                            } else {
                                codeErrorKey = "regenError"
                            }
                            busy = false
                        }
                    }
                    GameButton(tr("btnCancel"), secondary = true, modifier = Modifier.weight(1f)) { showRegenConfirm = false }
                }
            },
        ) {
            DialogText(tr("regenConfirmText"), color = TextColor)
        }
    }

    if (showLogoutConfirm) {
        InfoDialog(
            title = tr("logoutConfirmTitle"),
            onDismiss = { showLogoutConfirm = false },
            buttons = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GameButton(tr("logoutConfirmYes"), modifier = Modifier.weight(1f)) {
                        showLogoutConfirm = false
                        scope.launch {
                            // onSaveChange (updateSave) écrit déjà sur disque.
                            val fresh = session.logout(currentSave)
                            onSaveChange(fresh)
                            onBack()
                        }
                    }
                    GameButton(tr("btnCancel"), secondary = true, modifier = Modifier.weight(1f)) { showLogoutConfirm = false }
                }
            },
        ) {
            // Code déjà affiché : la partie reste sur le compte, seul l'appareil repart de zéro.
            val linked = session.accountCode.isNotEmpty() && save.accountCodeShown
            DialogText(tr(if (linked) "logoutConfirmTextLinked" else "logoutConfirmText"), color = TextColor)
        }
    }
}

/** `•••• •••• •••• ••••` : le code tant qu'on ne demande pas à le voir. */
private const val MASKED_CODE = "•••• •••• •••• ••••"

/**
 * `#merge-choice-modal` : deux cartes, la partie actuelle et celle du code ;
 * toucher une carte la garde, l'autre est remplacée. Un appui à côté ne
 * tranche rien (seul « Annuler » ferme), c'est un choix définitif.
 */
@Composable
private fun MergeChoiceDialog(
    current: SaveSummary,
    other: SaveSummary,
    onChoose: (MergeChoice) -> Unit,
    onCancel: () -> Unit,
) {
    InfoDialog(
        title = tr("mergeChoiceTitle"),
        onDismiss = onCancel,
        dismissOnScrim = false,
        buttons = { GameButton(tr("btnCancel"), secondary = true, modifier = Modifier.fillMaxWidth(), onClick = onCancel) },
    ) {
        DialogText(tr("mergeChoiceHint"))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MergeCard(tr("mergeChoiceCurrentTitle"), current, Modifier.weight(1f)) { onChoose(MergeChoice.CURRENT) }
            MergeCard(tr("mergeChoiceOtherTitle"), other, Modifier.weight(1f)) { onChoose(MergeChoice.OTHER) }
        }
    }
}

/** Une carte du choix : pseudo, argent, record, niveau (`renderMergeStatsHtml()`). */
@Composable
private fun MergeCard(title: String, stats: SaveSummary, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CardBg)
            .border(2.dp, Accent, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = AccentText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(stats.pseudo, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("💰 ${stats.money} $", color = TextColor, fontSize = 12.sp)
        Text("📏 ${"%.1f".format(stats.bestDistance)} m", color = TextColor, fontSize = 12.sp)
        Text("💪 ${tr("statLevel")} ${stats.level}", color = TextColor, fontSize = 12.sp)
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
