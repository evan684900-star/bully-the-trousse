@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.description
import com.bullythetrousse.core.name
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.Pseudo
import com.bullythetrousse.core.GraphicsQuality
import com.bullythetrousse.core.I18n
import kotlin.math.roundToInt
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.bullythetrousse.core.SecretMath
import com.bullythetrousse.core.Secrets
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.SfxCatalog
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import java.time.LocalDate
import com.bullythetrousse.core.Lang

/**
 * Écrans ouverts depuis le menu (Succès, Défis, Classement, Profil). Côté
 * web ce sont des modales (`#achievements-modal`, `#challenges-modal`) ou
 * des écrans (`#screen-leaderboard`, `#screen-profile`) ; ils reprennent
 * ici le fond `--app-bg` et les cartes `.achievement-row` du site.
 */
@Composable
internal fun ModalScreen(
    title: String,
    onBack: () -> Unit,
    /**
     * Un vrai ÉCRAN (classement, profil) peint son fond et remplace ce qu'il
     * y avait avant ; une MODALE (`.modal-overlay` côté site : réglages,
     * succès, liens...) le laisse transparent pour se poser sur l'écran
     * courant, assombri à 50 % par `GameContent`.
     */
    opaque: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (opaque) Modifier.background(AppBg) else Modifier)
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
        Text(tr("app.back"), color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Les 47 succès du jeu, en grille d'émojis : débloqués en clair, verrouillés
 * estompés comme `.achievement-row.locked`. Un appui ouvre la fiche du
 * succès (nom, description, part des joueurs qui l'ont), ce que la liste du
 * site affiche en permanence à côté de chaque icône.
 *
 * Les pourcentages suivent `loadAchievementStats()` : rien en dessous de
 * [ACHV_MIN_ACTIVE_PLAYERS] joueurs actifs, le panel serait trop petit pour
 * un chiffre fiable.
 */
@Composable
fun AchievementsScreen(save: GameSave, session: CloudSession, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<com.bullythetrousse.core.Achievement?>(null) }
    val lang = LocalLang.current

    // État du panel de joueurs (`#achv-pool-status`) et % par succès.
    var poolStatus by remember { mutableStateOf<String?>(null) }
    val percents = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(session.state) {
        val online = session.state == CloudState.GUEST || session.state == CloudState.LINKED
        if (!online) {
            poolStatus = I18n.tr("achvPctOffline", lang)
            return@LaunchedEffect
        }
        poolStatus = I18n.tr("achvPctLoading", lang)
        val totalActive = try {
            session.bridge.countActivePlayers()
        } catch (e: Exception) {
            poolStatus = I18n.tr("achvPctOffline", lang)
            return@LaunchedEffect
        }
        if (totalActive < ACHV_MIN_ACTIVE_PLAYERS) {
            poolStatus = I18n.tr("achvPctNotEnough", lang) + ACHV_MIN_ACTIVE_PLAYERS +
                I18n.tr("achvPctNotEnoughMin", lang) + totalActive + I18n.tr("achvPctNotEnoughSuffix", lang)
            return@LaunchedEffect
        }
        poolStatus = totalActive.toString() + I18n.tr("achvPctActivePlayers", lang)
        for (achievement in Achievements.ALL) {
            runCatching { session.bridge.countAchievementUnlocks(achievement.id) }.onSuccess { count ->
                percents[achievement.id] = (count * 100.0 / totalActive).roundToInt()
            }
        }
    }

    ModalScreen(tr("achvModalTitle"), onBack) {
        Text(
            tr("app.achvCount", "n" to save.unlockedAchievements.size, "total" to Achievements.ALL.size),
            color = TextDim,
            fontSize = 13.sp,
        )
        poolStatus?.let {
            Text(it, color = TextDim, fontSize = 11.5.sp, textAlign = TextAlign.Center)
        }
        Text(
            tr("app.achvTapHint"),
            color = TextDim,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
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
                        .clickable { detail = achievement }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(achievement.emoji, fontSize = 26.sp)
                }
            }
        }
    }

    detail?.let { achievement ->
        val unlocked = achievement.id in save.unlockedAchievements
        AchievementDetailDialog(
            emoji = if (unlocked) achievement.emoji else "🔒",
            name = achievement.name(lang),
            description = achievement.description(lang),
            unlocked = unlocked,
            percent = percents[achievement.id],
            onDismiss = { detail = null },
        )
    }
}

/** `ACHV_MIN_ACTIVE_PLAYERS` : en dessous, le panel est jugé trop petit pour un % fiable. */
private const val ACHV_MIN_ACTIVE_PLAYERS = 5

/**
 * La fiche d'un succès : ce que la liste du site (`renderAchievements()`)
 * montre en permanence à côté de chaque icône, ici derrière un appui pour
 * garder la grille compacte.
 */
@Composable
private fun AchievementDetailDialog(
    emoji: String,
    name: String,
    description: String,
    unlocked: Boolean,
    percent: Int?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ShopBg)
                .border(2.dp, if (unlocked) Accent else PanelBorder, RoundedCornerShape(16.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(emoji, fontSize = 44.sp)
            Text(
                name,
                color = if (unlocked) Accent else TextColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(description, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text(
                tr(if (unlocked) "app.achvUnlocked" else "app.achvLocked"),
                color = if (unlocked) Money else TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            percent?.let {
                Text(tr("app.achvPlayersPct", "pct" to it), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * `#challenges-modal` / `renderDailyChallenges()` : les 3 défis du jour,
 * générés dès l'ouverture s'ils ne le sont pas encore, avec leur
 * description, une barre de progression, la récompense à réclamer et le
 * compte à rebours jusqu'au renouvellement de minuit.
 */
@Composable
fun ChallengesScreen(save: GameSave, onSaveChange: (GameSave) -> Unit, onBack: () -> Unit) {
    val sfx = LocalSfx.current
    val toaster = LocalToaster.current

    // ensureDailyChallenges() : les défis du jour existent dès qu'on regarde.
    LaunchedEffect(Unit) {
        val ensured = DailyChallenges.ensure(save, LocalDate.now().toString(), SkinStats.totalPuissance(save), SkinStats.totalVitesse(save))
        if (ensured != save) onSaveChange(ensured)
    }

    // Compte à rebours jusqu'à minuit, rafraîchi chaque seconde.
    var countdown by remember { mutableStateOf(timeUntilMidnight()) }
    LaunchedEffect(Unit) {
        while (true) {
            countdown = timeUntilMidnight()
            delay(1000)
        }
    }

    ModalScreen(tr("challengesModalTitle"), onBack) {
        Text(tr("challengesResetInfo", "time" to countdown), color = TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        save.dailyChallenges.forEachIndexed { index, challenge ->
            val def = CHALLENGE_DISPLAY[challenge.kind]
            val isDistance = challenge.kind == "distance" || challenge.kind == "distanceCumul"
            val isMoney = challenge.kind == "earn" || challenge.kind == "gift" || challenge.kind == "spend"
            val shownRaw = minOf(challenge.progress, challenge.target)
            val shown = if (isDistance) "%.1f".format(shownRaw) else shownRaw.roundToInt().toString()
            val target = if (challenge.target % 1.0 == 0.0) challenge.target.toLong().toString() else challenge.target.toString()
            val unit = if (isDistance) " m" else if (isMoney) " $" else ""
            val fraction = (challenge.progress / challenge.target).coerceIn(0.0, 1.0).toFloat()
            val done = challenge.progress >= challenge.target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (challenge.claimed) 0.55f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(def?.first ?: "🎯", fontSize = 26.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        def?.second?.let { tr(it, "target" to target) } ?: challenge.kind,
                        color = TextColor,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // .challenge-bar : dégradé doré → orange.
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x4D000000)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(10.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFFD23F), Color(0xFFFF9F43)))),
                        )
                    }
                    Text("$shown / $target$unit", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                when {
                    challenge.claimed -> Text(tr("challengeClaimed"), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    done -> GameButton("+${challenge.reward} $", small = true) {
                        // claimDailyChallenge() : sfxBuy() et « 🎯 +X$ ! ».
                        val result = DailyChallenges.claim(save, index)
                        if (result is DailyChallenges.ClaimResult.Success) {
                            onSaveChange(result.save)
                            sfx.play(SfxCatalog.BUY)
                            toaster("🎯 +${result.reward}$ !")
                        }
                    }
                    else -> Text("+${challenge.reward} $", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** L'icône et la clé de description de chaque défi (`CHALLENGE_POOL` côté site). */
private val CHALLENGE_DISPLAY: Map<String, Pair<String, String>> = mapOf(
    "throws" to ("🎒" to "challengeThrowsDesc"),
    "distance" to ("📏" to "challengeDistanceDesc"),
    "earn" to ("💰" to "challengeEarnDesc"),
    "perfect" to ("✨" to "challengePerfectDesc"),
    "distanceCumul" to ("🛣️" to "challengeDistanceCumulDesc"),
    "gift" to ("🎁" to "challengeGiftDesc"),
    "qte" to ("🎯" to "challengeQteDesc"),
    "spend" to ("🛍️" to "challengeSpendDesc"),
    "volcan" to ("🌋" to "challengeVolcanDesc"),
    "skid" to ("💨" to "challengeSkidDesc"),
)

/** « HH:MM:SS » jusqu'au prochain minuit local (`updateChallengesCountdown()`). */
private fun timeUntilMidnight(): String {
    val now = java.time.LocalDateTime.now()
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val remaining = java.time.Duration.between(now, midnight).seconds
    return "%02d:%02d:%02d".format(remaining / 3600, (remaining % 3600) / 60, remaining % 60)
}

/**
 * `#settings-modal` : thème, langue, relecture des tutoriels, compte, musique,
 * qualité graphique (propre au portage), téléchargement des musiques, et le
 * point quasi invisible du bas qui ouvre le calcul mental secret.
 */
@Composable
fun SettingsScreen(
    save: GameSave,
    session: CloudSession,
    onSaveChange: (GameSave) -> Unit,
    onOpenAccount: () -> Unit,
    onReplayTutorial: (Tutorial) -> Unit,
    onBack: () -> Unit,
) {
    var secretMath by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val saved = tr("app.musicSaved")
    val saveError = tr("app.musicSaveError")

    ModalScreen(tr("settingsTitle"), onBack) {
        // Thème : bascule jour/nuit de toute l'interface (applyTheme()).
        val night = save.theme != "light"
        SettingsRow(tr("settingsThemeLabel")) {
            GameButton(if (night) "🌙" else "☀️", secondary = true, small = true) {
                onSaveChange(save.copy(theme = if (night) "light" else "dark"))
            }
        }
        // Langue : FR <-> EN, toute l'interface suit immédiatement (LocalLang).
        SettingsRow(tr("settingsLangLabel")) {
            GameButton(if (save.lang == "en") "EN" else "FR", secondary = true, small = true) {
                onSaveChange(save.copy(lang = if (save.lang == "en") "fr" else "en"))
            }
        }

        // 📖 Revoir le tutoriel : Volcans et Plage seulement une fois débloqués.
        SettingsSection(tr("settingsTutoTitle"))
        SettingsRow(tr("settingsTutoBasics")) {
            GameButton(tr("settingsTutoReplay"), secondary = true, small = true) { onReplayTutorial(Tutorial.BASICS) }
        }
        if (save.volcanUnlocked) {
            SettingsRow(tr("settingsTutoVolcan")) {
                GameButton(tr("settingsTutoReplay"), secondary = true, small = true) { onReplayTutorial(Tutorial.VOLCANO) }
            }
        }
        if (save.plageUnlocked) {
            SettingsRow(tr("settingsTutoPlage")) {
                GameButton(tr("settingsTutoReplay"), secondary = true, small = true) { onReplayTutorial(Tutorial.PLAGE) }
            }
        }

        // Compte : code de récupération, compte privé, avatar, déconnexion —
        // regroupés sur un écran à part (AccountScreen).
        SettingsSection(tr("settingsAccountTitle"))
        SettingsRow(tr("app.settingsAccount")) {
            GameButton(tr("app.settingsManage"), secondary = true, small = true, onClick = onOpenAccount)
        }
        Text(
            tr(session.statusKey),
            color = if (session.state == CloudState.LINKED) Money else TextDim,
            fontSize = 11.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )

        SettingsRow(tr("app.settingsMusic")) {
            GameButton(if (save.musicMuted) "🔇" else "🔊", secondary = true, small = true) {
                onSaveChange(save.copy(musicMuted = !save.musicMuted))
            }
        }
        // Qualité : Basse → Normale → Élevée. Propre au portage Android — le
        // site n'a pas ce réglage, mais un téléphone d'entrée de gamme en a besoin.
        val quality = GraphicsQuality.fromId(save.graphicsQuality)
        SettingsRow(tr("app.settingsQuality")) {
            GameButton(tr(qualityKey(quality)), secondary = true, small = true) {
                onSaveChange(save.copy(graphicsQuality = GraphicsQuality.next(quality).id))
            }
        }
        Text(
            tr(qualityKey(quality) + "Hint"),
            color = TextDim,
            fontSize = 11.5.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )

        // 🎵 Musiques du jeu : chaque piste téléchargeable.
        SettingsSection(tr("settingsMusicTitle"))
        for ((labelKey, track) in listOf(
            "settingsMusicNormal" to (R.raw.music_cour to "musique-fond.mp3"),
            "settingsMusicPlage" to (R.raw.music_plage to "musique-plage.mp3"),
            "settingsMusicVolcan" to (R.raw.music_volcan to "musique-volcan.mp3"),
        )) {
            SettingsRow(tr(labelKey)) {
                GameButton(tr("btnMusicDownload"), secondary = true, small = true) {
                    when (exportMusic(context, track.first, track.second)) {
                        MusicExportResult.SAVED -> toaster(saved)
                        MusicExportResult.SHARED -> Unit
                        MusicExportResult.FAILED -> toaster(saveError)
                    }
                }
            }
        }

        // .settings-secret-trigger : un point presque invisible.
        Text(
            "·",
            color = TextColor.copy(alpha = 0.15f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { secretMath = true }
                .padding(top = 14.dp, bottom = 2.dp),
        )
    }

    if (secretMath) {
        SecretMathDialog(
            onSolved = {
                secretMath = false
                onSaveChange(Secrets.unlock(save))
            },
            onDismiss = { secretMath = false },
        )
    }
}

/** La clé de traduction du niveau de qualité (`app.qualityLow`...). */
private fun qualityKey(quality: GraphicsQuality): String = when (quality) {
    GraphicsQuality.LOW -> "app.qualityLow"
    GraphicsQuality.MEDIUM -> "app.qualityMedium"
    GraphicsQuality.HIGH -> "app.qualityHigh"
}

/** `.settings-section-title` */
@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        color = Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

/**
 * `#secret-math-modal` : 4 calculs, 5 s chacun. Une mauvaise réponse ou le
 * temps écoulé referment tout sans un mot (échec silencieux, pas d'indice).
 */
@Composable
private fun SecretMathDialog(onSolved: () -> Unit, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var remaining by remember { mutableLongStateOf(SecretMath.SECONDS_PER_QUESTION * 1000L) }
    val sfx = LocalSfx.current
    val toaster = LocalToaster.current
    val unlocked = tr("secretUnlocked")

    LaunchedEffect(index) {
        val deadline = System.currentTimeMillis() + SecretMath.SECONDS_PER_QUESTION * 1000L
        while (true) {
            remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                onDismiss()
                return@LaunchedEffect
            }
            delay(100)
        }
    }

    fun submit() {
        if (!SecretMath.isCorrect(index, typed)) return onDismiss()
        if (index == SecretMath.QUESTIONS.lastIndex) {
            sfx.play(SfxCatalog.BUY)
            toaster(unlocked)
            onSolved()
        } else {
            typed = ""
            index++
        }
    }

    InfoDialog(
        title = SecretMath.QUESTIONS[index].text,
        onDismiss = onDismiss,
        buttons = { GameButton(tr("secretMathSubmit"), modifier = Modifier.fillMaxWidth()) { submit() } },
    ) {
        DialogText("${SecretMath.secondsLeft(remaining)}s")
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it.filter { c -> c.isDigit() || c == '-' }.take(6) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
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

/**
 * `#changelog-modal` : le journal des changements, ouvert en touchant le
 * numéro de version du menu. Les entrées majeures sont mises en avant en
 * doré, les mineures en gris, comme côté web.
 */
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val english = LocalLang.current == Lang.EN
    ModalScreen(tr("changelogTitle"), onBack) {
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
                if (entry.major.isNotEmpty()) {
                    Text(tr("changelogMajor"), color = TextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    for (line in entry.major) {
                        Text("• ${if (english) line.en else line.fr}", color = TextColor, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (entry.minor.isNotEmpty()) {
                    Text(tr("changelogMinor"), color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    for (line in entry.minor) {
                        Text("• ${if (english) line.en else line.fr}", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** `#links-modal` : les deux liens du site, qui s'ouvrent dans le navigateur. */
@Composable
fun LinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    ModalScreen(tr("linksTitle"), onBack) {
        LinkRow("🌐 evyverse.vercel.app", tr("linksSiteDesc")) { openUrl(context, "https://evyverse.vercel.app") }
        LinkRow("💬 ${tr("linksWhatsappLabel")}", tr("linksWhatsappDesc")) {
            openUrl(context, "https://whatsapp.com/channel/0029VbDIKVI4IBh8MLKV2y25")
        }
    }
}

/** Ouvre une adresse dans le navigateur (ou l'app qui la gère, WhatsApp...). */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
fun LeaderboardScreen(
    save: GameSave,
    session: CloudSession,
    onSaveChange: (GameSave) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
) {
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

    var editingPseudo by remember { mutableStateOf(false) }

    ModalScreen(tr(if (save.inPlage) "leaderboardTitlePlage" else "leaderboardTitle"), onBack, opaque = true) {
        // `#leaderboard-pseudo` + `btn-edit-pseudo` côté site : c'est d'ici
        // qu'on change le nom affiché aux autres joueurs.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${tr("pseudoLabel")}${save.pseudo.ifBlank { "-" }}",
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            GameButton("✏️", secondary = true, small = true) { editingPseudo = true }
        }
        Text(
            tr("app.leaderboardTapHint"),
            color = TextDim,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
        )
        val rows = entries
        when {
            session.state == CloudState.NOT_CONFIGURED || session.state == CloudState.OFFLINE ->
                LeaderboardNotice(tr("leaderboardUnavailable"))
            failed -> LeaderboardNotice(tr("leaderboardError"))
            rows == null -> LeaderboardNotice(tr("leaderboardLoading"))
            rows.isEmpty() -> LeaderboardNotice(tr("leaderboardEmpty"))
            else -> rows.forEachIndexed { index, entry ->
                LeaderboardRow(
                    rank = index + 1,
                    entry = entry,
                    isMe = entry.uid == session.uid,
                    onClick = { onOpenPlayer(entry.uid) },
                )
            }
        }
    }

    if (editingPseudo) {
        PseudoDialog(
            current = save.pseudo,
            onDismiss = { editingPseudo = false },
            onConfirm = { typed ->
                editingPseudo = false
                Pseudo.sanitize(typed)?.let { onSaveChange(save.copy(pseudo = it)) }
            },
        )
    }
}

/**
 * Changement de pseudo (`btn-edit-pseudo` côté site, qui utilise un `prompt()`
 * natif). Le nouveau nom repart aussitôt vers le classement : `scores/{uid}`
 * n'est normalement réécrit que sur un nouveau record, donc sans ça le
 * changement n'apparaîtrait aux autres qu'au prochain record battu — c'est
 * [CloudSession] qui s'en charge, puisque toute modification de la sauvegarde
 * déclenche un renvoi vers le cloud.
 */
@Composable
private fun PseudoDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember { mutableStateOf(current) }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ShopBg)
                .border(2.dp, PanelBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("pseudoPrompt"), color = Accent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                tr("app.pseudoHint"),
                color = TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(Pseudo.MAX_LENGTH) },
                singleLine = true,
                placeholder = { Text(tr("app.pseudoPlaceholder"), color = TextDim, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${typed.length} / ${Pseudo.MAX_LENGTH}", color = TextDim, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(tr("app.save"), small = true) { onConfirm(typed) }
                GameButton(tr("btnCancel"), secondary = true, small = true, onClick = onDismiss)
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
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry, isMe: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(2.dp, if (isMe) Accent else PanelBorder, shape)
            .clickable(onClick = onClick)
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

