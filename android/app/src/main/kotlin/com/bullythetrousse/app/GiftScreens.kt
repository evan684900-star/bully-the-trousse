@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.BumpMode
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.GiftCheck
import com.bullythetrousse.core.Gifts
import com.bullythetrousse.core.SkinStats
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * « Offrir de l'argent » (`openGiftModal()` côté site) : mes abonnés et mes
 * abonnements, chacun avec les boutons rapides +10/+100/+1000/Max, un champ
 * montant et le bouton 💸.
 *
 * L'argent part tout de suite de mon solde (pour que l'écran réagisse), puis
 * le cadeau est écrit dans `gifts`. S'il n'a pas pu partir, l'argent revient :
 * il ne doit jamais disparaître sans qu'un cadeau ait vraiment été envoyé.
 */
@Composable
fun GiftSendDialog(
    save: GameSave,
    session: CloudSession,
    onSaveChange: (GameSave) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    // Le montant débité et le remboursement doivent partir de la sauvegarde
    // du MOMENT, pas de celle d'avant l'aller-retour réseau.
    val currentSave by rememberUpdatedState(save)

    var targets by remember { mutableStateOf<List<Pair<String, PublicProfile?>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val inputs = remember { mutableStateMapOf<String, String>() }
    val sent = remember { mutableStateMapOf<String, Boolean>() }
    val sending = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(session.uid) {
        val me = session.uid
        if (me == null) {
            failed = true
            return@LaunchedEffect
        }
        try {
            targets = session.bridge.listGiftTargets(me)
        } catch (e: Exception) {
            failed = true
        }
    }

    // Messages d'erreur résolus maintenant : les appels à tr() ne peuvent
    // pas se faire depuis une coroutine.
    val msgInvalid = tr("giftInvalidAmount")
    val msgNotEnough = tr("giftNotEnoughMoney")
    val msgCooldown = tr("giftCooldown")
    val msgSent = tr("giftSent")
    val msgError = tr("giftError")

    fun send(targetUid: String) {
        val me = session.uid ?: return
        val check = Gifts.check(inputs[targetUid].orEmpty(), currentSave.money, session.lastGiftSentAtMillis, System.currentTimeMillis())
        val amount = when (check) {
            is GiftCheck.Ok -> check.amount
            GiftCheck.InvalidAmount -> return toaster(msgInvalid)
            GiftCheck.NotEnoughMoney -> return toaster(msgNotEnough)
            is GiftCheck.Cooldown -> return toaster(msgCooldown.replace("{seconds}", check.secondsLeft.toString()))
        }
        session.lastGiftSentAtMillis = System.currentTimeMillis()
        sending[targetUid] = true
        onSaveChange(currentSave.copy(money = currentSave.money - amount))
        session.launchDetached {
            try {
                session.bridge.sendGift(me, targetUid, currentSave.pseudo, amount)
                // Compté une fois le cadeau confirmé seulement (succès « Grand
                // donateur » et défi « gift »), pas à la déduction optimiste.
                var updated = currentSave.copy(totalMoneyGifted = currentSave.totalMoneyGifted + amount)
                updated = DailyChallenges.ensure(updated, LocalDate.now().toString(), SkinStats.totalPuissance(updated), SkinStats.totalVitesse(updated))
                updated = DailyChallenges.bump(updated, "gift", amount.toDouble(), BumpMode.ADD)
                onSaveChange(updated)
                playPaymentSound(context)
                toaster(msgSent.replace("{amount}", amount.toString()))
                inputs[targetUid] = ""
                sent[targetUid] = true
            } catch (e: CancellationException) {
                // L'app se ferme : l'écriture est déjà dans la file de Firestore
                // et partira quand même, surtout ne pas rembourser.
                throw e
            } catch (e: Exception) {
                onSaveChange(currentSave.copy(money = currentSave.money + amount))
                // Rien n'est vraiment parti : le délai ne doit pas s'appliquer.
                session.lastGiftSentAtMillis = 0L
                toaster(msgError)
            }
            sending[targetUid] = false
        }
    }

    InfoDialog(title = tr("giftModalTitle"), onDismiss = onDismiss) {
        val list = targets
        when {
            failed -> DialogText(tr("leaderboardError"))
            list == null -> DialogText(tr("profileLoading"))
            list.isEmpty() -> DialogText(tr("giftNoFollowers"))
            else -> for ((uid, profile) in list) {
                GiftRow(
                    avatar = profile?.avatarEmoji.orEmpty().ifBlank { "🎒" },
                    pseudo = profile?.pseudo ?: "?",
                    input = inputs[uid].orEmpty(),
                    onInput = { typed -> inputs[uid] = typed.filter { it.isDigit() }.take(12) },
                    onQuickAdd = { add -> inputs[uid] = Gifts.quickAdd(inputs[uid].orEmpty(), add, currentSave.money).toString() },
                    onMax = { inputs[uid] = Gifts.max(currentSave.money).toString() },
                    sent = sent[uid] == true,
                    busy = sending[uid] == true,
                    onSend = { send(uid) },
                )
            }
        }
    }
}

/** `.gift-row` : avatar + pseudo, puis les contrôles de montant. */
@Composable
private fun GiftRow(
    avatar: String,
    pseudo: String,
    input: String,
    onInput: (String) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onMax: () -> Unit,
    sent: Boolean,
    busy: Boolean,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(avatar, fontSize = 22.sp)
            Text(pseudo, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        FlowRowCentered(gap = 6.dp) {
            GameButton("+10", secondary = true, small = true) { onQuickAdd(10) }
            GameButton("+100", secondary = true, small = true) { onQuickAdd(100) }
            GameButton("+1000", secondary = true, small = true) { onQuickAdd(1000) }
            GameButton(tr("giftMaxBtn"), secondary = true, small = true, onClick = onMax)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(tr("giftAmountPlaceholder"), color = TextDim, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
            )
            GameButton(
                when {
                    busy -> "…"
                    sent -> "✅"
                    else -> "💸"
                },
                small = true,
                modifier = Modifier.width(56.dp),
            ) { if (!busy) onSend() }
        }
    }
}

/**
 * La modale des cadeaux reçus depuis la dernière connexion : une ligne par
 * expéditeur (dons fusionnés) et le total crédité.
 */
@Composable
fun GiftsReceivedDialog(rows: List<Pair<String, Int>>, total: Int, onDismiss: () -> Unit) {
    InfoDialog(title = tr("giftsReceivedTitle"), onDismiss = onDismiss) {
        for ((pseudo, amount) in rows) {
            Text("🎁 $pseudo : +$amount $", color = TextColor, fontSize = 14.sp)
        }
        Text(
            tr("giftsReceivedTotal", "amount" to total),
            color = Money,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * `sfxPayment()` : le seul bruitage, avec le grondement du volcan, qui soit
 * un vrai fichier (`paiment.mp3`). Best-effort : un son qui ne part pas ne
 * doit jamais bloquer l'envoi.
 */
private fun playPaymentSound(context: Context) {
    runCatching {
        MediaPlayer.create(context, R.raw.sfx_payment)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}
