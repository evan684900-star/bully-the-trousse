@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Cosmetics
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.SkinShop
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.VilleCredits
import com.bullythetrousse.core.VilleEvent
import com.bullythetrousse.core.VillePopup
import com.bullythetrousse.core.VillePopupBody
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Ce que le site affiche en HTML par-dessus le canvas du monde Ville
 * (`.vl-hud`, `.vl-msg`, `.vl-pop`, `.vl-choices`, `.vl-touch`,
 * `.vl-credits`, le modal de La Trousserie), porté en Compose avec les
 * mêmes tailles et couleurs.
 */

/** `.vl-hud` : argent et évènement en cours, centrés en haut. */
@Composable
internal fun VilleHud(money: Int, eventText: String?, modifier: Modifier = Modifier) {
    FlowRowCentered(gap = 8.dp, modifier = modifier.widthIn(max = 520.dp)) {
        MoneyPill("💰 $money $", small = true)
        if (eventText != null) MoneyPill(eventText, small = true)
    }
}

/** `.vl-msg` : texte blanc très gras avec ombre, qui apparaît en fondu. */
@Composable
internal fun VilleMessage(text: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    val alpha by animateFloatAsState(if (text != null) 1f else 0f, tween(400), label = "villeMessage")
    if (alpha <= 0.01f) return
    Text(
        shown,
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(0.9f).alpha(alpha),
        style = TextStyle(
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color(0xF2000000), Offset(0f, 2f), 8f),
        ),
    )
}

private val BOARD_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/** `.vl-pop` : petite fenêtre (titre, croix, contenu, liste de boutons). */
@Composable
internal fun VillePopupView(
    popup: VillePopup,
    onButton: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .widthIn(min = 230.dp, max = 380.dp)
            .fillMaxWidth(0.88f)
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                popup.title,
                color = TextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 34.dp),
            )
            when (val body = popup.body) {
                VillePopupBody.None -> Unit
                is VillePopupBody.Text -> PopupText(body.text)
                is VillePopupBody.Board -> NoticeBoard(body.events, body.nowMillis)
                VillePopupBody.Controls -> ControlsHelp()
            }
            popup.buttons.forEachIndexed { i, button ->
                GameButton(
                    button.label,
                    secondary = button.secondary,
                    small = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onButton(i) },
                )
            }
        }
        // .vl-x : la croix en haut à droite
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
                .clip(CircleShape)
                .border(2.dp, PanelBorder, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = TextColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PopupText(text: String) {
    Text(text, color = TextColor, fontSize = 13.5.sp, lineHeight = 19.sp)
}

/** `.vl-board` : les évènements des 5 prochains jours. */
@Composable
private fun NoticeBoard(events: List<VilleEvent>, nowMillis: Long) {
    PopupText("Évènements des 5 prochains jours (heure de ton appareil) :")
    if (events.isEmpty()) {
        PopupText("Rien de prévu pour l'instant.")
        return
    }
    Column(
        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (ev in events) {
            val current = ev.isActiveAt(nowMillis)
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(CardBg)
                    .border(2.dp, if (current) Accent else PanelBorder, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    "${ev.type.emoji} ${ev.type.label}" + if (current) " — EN COURS" else "",
                    color = TextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                val day = Instant.ofEpochMilli(ev.startMillis).atZone(ZoneId.systemDefault()).format(BOARD_DAY)
                Text("$day · ${hhmm(ev.startMillis)} → ${hhmm(ev.endMillis)}", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(ev.type.effect, color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * `.vl-keys` : les commandes, au réveil dans le monde bugé. Le site liste
 * les touches du clavier ; ici ce sont les boutons tactiles de l'écran.
 */
@Composable
private fun ControlsHelp() {
    val rows = listOf(
        "◀  ▶" to "se déplacer",
        "▲" to "sauter (maintenir pour sauter plus haut)",
        "⚡" to "dash (recharge 3 s)",
        "E" to "interagir",
        "⚔️" to "attaquer (quand tu as une arme) — toucher l'écran marche aussi",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((key, action) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardBg)
                        .border(2.dp, PanelBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(key, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(action, color = TextColor, fontSize = 13.sp)
            }
        }
    }
    PopupText("À gauche : retour à la pièce du portail. À droite : ...l'inconnu.")
}

/** `.vl-choices` : les réponses proposées pendant une rencontre. */
@Composable
internal fun VilleChoicesView(options: List<String>, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    FlowRowCentered(
        gap = 10.dp,
        modifier = modifier
            .widthIn(max = 560.dp)
            .clip(shape)
            .background(PanelBg)
            .border(2.dp, PanelBorder, shape)
            .padding(12.dp),
    ) {
        options.forEachIndexed { i, label ->
            GameButton("${i + 1}. $label", small = true, onClick = { onPick(i) })
        }
    }
}

/** Un bouton rond de `.vl-touch` : maintenu tant que le doigt reste dessus. */
@Composable
private fun TouchButton(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit = {},
    dimmed: Boolean = false,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(58.dp)
            .alpha(if (dimmed) 0.4f else 1f)
            .clip(CircleShape)
            .background(if (pressed) Color(0x99FFD23F) else Color(0x8C141824))
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    onPress()
                    // Relâché ou glissé hors du bouton : dans les deux cas on relâche.
                    waitForUpOrCancellation()?.consume()
                    pressed = false
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/** `.vl-touch` : ◀ ▶ à gauche ; ⚔️ ⚡ E ▲ à droite. */
@Composable
internal fun VilleTouchControls(
    showAttack: Boolean,
    dashReady: Boolean,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit,
    onJump: (Boolean) -> Unit,
    onDash: () -> Unit,
    onInteract: () -> Unit,
    onAttack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TouchButton("◀", onPress = { onLeft(true) }, onRelease = { onLeft(false) })
            TouchButton("▶", onPress = { onRight(true) }, onRelease = { onRight(false) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            if (showAttack) TouchButton("⚔️", onPress = onAttack)
            TouchButton("⚡", onPress = onDash, dimmed = !dashReady)
            TouchButton("E", onPress = onInteract)
            TouchButton("▲", onPress = { onJump(true) }, onRelease = { onJump(false) })
        }
    }
}

/**
 * `.vl-credits` : le générique qui défile, s'arrête quand le remerciement
 * final est au centre de l'écran, puis attend un appui.
 */
@Composable
internal fun VilleCreditsRoll(credits: VilleCredits, onFinished: () -> Unit) {
    val density = LocalDensity.current
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var finalCenter by remember { mutableFloatStateOf(-1f) }
    var y by remember { mutableFloatStateOf(Float.NaN) }
    var ended by remember { mutableStateOf(false) }
    var endedFor by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(credits) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                if (containerHeight <= 0f || finalCenter < 0f) return@withFrameNanos
                if (y.isNaN()) y = containerHeight + with(density) { 20.dp.toPx() }
                if (!ended) {
                    // 34 px/s côté site.
                    y -= with(density) { 34.dp.toPx() } * dt
                    val target = containerHeight / 2 - finalCenter
                    if (y <= target) { y = target; ended = true }
                } else {
                    endedFor += dt
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerHeight = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (ended && endedFor > 2f) onFinished()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, if (y.isNaN()) 100_000 else y.roundToInt()) }
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.9f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (line in credits.intro) {
                Text(line, color = Color.White, fontSize = 18.sp, lineHeight = 29.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.size(20.dp))
            }
            CreditsHeading("STATISTIQUES")
            for ((label, value) in credits.stats) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Color(0xFFD8D8D8), fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }
            if (credits.answer.isNotEmpty()) {
                CreditsHeading("CE QUE TU AS DIT À L'AUTRE TOI")
                Text("« ${credits.answer} »", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.size(20.dp))
            }
            Spacer(Modifier.size(70.dp))
            Text(
                credits.finalLine,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.onGloballyPositioned {
                    // Centre de la ligne, dans le repère de la colonne.
                    finalCenter = it.positionInParent().y + it.size.height / 2f
                },
            )
        }
        val contAlpha by animateFloatAsState(if (ended && endedFor > 2f) 1f else 0f, tween(1000), label = "creditsCont")
        Text(
            "Appuie n'importe où pour continuer",
            color = Color(0xFF8A8A8A),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 30.dp)
                .alpha(contAlpha),
        )
    }
}

@Composable
private fun CreditsHeading(text: String) {
    Text(
        text,
        color = Color(0xFF9A9A9A),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 42.dp, bottom = 12.dp),
    )
}

/**
 * La Trousserie (`#ville-skinshop-modal`) : les skins au prix de la Ville,
 * puis les cosmétiques décoratifs de [Cosmetics].
 */
@Composable
internal fun TrousserieOverlay(save: GameSave, onSaveChange: (GameSave) -> Unit, onClose: () -> Unit) {
    val sfx = LocalSfx.current
    var toast by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .widthIn(max = 1100.dp)
                .padding(vertical = 12.dp)
                .clip(shape)
                .background(PanelBg)
                .border(2.dp, PanelBorder, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("👕 La Trousserie", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "ici, tout est 20% plus cher mais vous gagnez 20% plus d'argent",
                color = Money,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            MoneyPill("💰 ${save.money} $", small = true)
            Text("🎨 Skins", color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            for (skin in Skins.ALL) {
                val (name, desc) = SKIN_LABELS[skin.id] ?: (skin.id to "")
                val owned = skin.id in save.ownedSkins
                val equipped = save.equippedSkin == skin.id
                ShopCard(
                    title = name,
                    description = desc,
                    leading = { TrousseSprite(skinId = skin.id, contentDescription = name, modifier = Modifier.size(46.dp)) },
                ) {
                    when {
                        equipped -> Text("✅ Équipée", color = Money, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        owned -> GameButton("Équiper", secondary = true, small = true) {
                            onSaveChange(SkinShop.equip(save, skin.id))
                            sfx.play(SfxCatalog.BUY)
                        }
                        else -> GameButton("${SkinShop.price(save, skin.id)} $", small = true) {
                            when (val result = SkinShop.buy(save, skin.id)) {
                                is SkinShop.PurchaseResult.Success -> {
                                    applyPurchase(result.save, SkinShop.price(save, skin.id), onSaveChange)
                                    sfx.play(SfxCatalog.BUY)
                                }
                                else -> { sfx.play(SfxCatalog.ERROR); toast = "💸 Pas assez d'argent !" }
                            }
                        }
                    }
                }
            }
            Text("🎩 Cosmétiques", color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            for (cosmetic in Cosmetics.ALL) {
                val owned = cosmetic.id in save.ownedCosmetics
                val worn = save.equippedCosmetic == cosmetic.id
                ShopCard(
                    title = cosmetic.name + if (worn) " · ÉQUIPÉE" else "",
                    description = "Purement décoratif, se porte sur n'importe quel skin.",
                    leading = { CosmeticPreview(cosmetic.id) },
                ) {
                    val label = when {
                        worn -> "Retirer"
                        owned -> "Équiper"
                        else -> "${Cosmetics.price(cosmetic, save)} $"
                    }
                    GameButton(label, secondary = worn, small = true) {
                        when (val result = Cosmetics.press(save, cosmetic.id)) {
                            is Cosmetics.Result.Success -> {
                                if (result.spent > 0) {
                                    applyPurchase(result.save, result.spent, onSaveChange)
                                    toast = "✅ ${cosmetic.name} achetée !"
                                } else {
                                    onSaveChange(result.save)
                                }
                                sfx.play(SfxCatalog.BUY)
                            }
                            Cosmetics.Result.NotEnoughMoney -> { sfx.play(SfxCatalog.ERROR); toast = "💸 Pas assez d'argent !" }
                        }
                    }
                }
            }
            GameButton("✕ Fermer", secondary = true, onClick = onClose)
        }
        Toast(message = toast, onDismiss = { toast = null })
    }
}

/** L'icône d'un cosmétique : la trousse de base qui le porte. */
@Composable
private fun CosmeticPreview(id: String) {
    val sprite = rememberTrousseSprite()
    val filterQuality = spriteFilterQuality()
    Canvas(modifier = Modifier.size(46.dp)) {
        val s = size.width * 72f / 92f
        val cx = size.width / 2
        val cy = size.height * 56f / 92f
        drawTrousseSprite(sprite, "classique", cx, cy, s, 0f, null, filterQuality = filterQuality, cosmetic = id)
    }
}
