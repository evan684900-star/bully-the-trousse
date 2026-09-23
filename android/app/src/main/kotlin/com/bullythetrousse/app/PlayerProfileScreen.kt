@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bullythetrousse.core.DailyStats
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.I18n
import com.bullythetrousse.core.Presence
import com.bullythetrousse.core.PresenceStatus
import com.bullythetrousse.core.SkinStats
import com.bullythetrousse.core.Skins
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Ce que l'écran Profil affiche, qu'il vienne de MA sauvegarde
 * (`buildOwnProfileData()` côté site, dispo hors ligne) ou du document public
 * `profiles/{uid}` d'un autre joueur — jamais de `users/{uid}`, qui reste privé.
 */
data class ProfileData(
    val pseudo: String,
    val avatarEmoji: String,
    val isPrivate: Boolean,
    val equippedSkin: String,
    val ownedSkins: List<String>,
    val totalMoneyEarned: Long,
    val dailyEarnings: Map<String, Long>,
    val dailyBestDistance: Map<String, Double>,
    val bestDistance: Double,
    val puissance: Int,
    val vitesse: Int,
    val achievementsCount: Int,
    val updatedAtMillis: Long?,
) {
    companion object {
        fun fromSave(save: GameSave) = ProfileData(
            pseudo = save.pseudo.ifBlank { "?" },
            avatarEmoji = save.avatarEmoji,
            isPrivate = save.isPrivate,
            equippedSkin = save.equippedSkin,
            ownedSkins = save.ownedSkins,
            totalMoneyEarned = save.totalMoneyEarned.toLong(),
            dailyEarnings = save.dailyEarnings.mapValues { it.value.toLong() },
            dailyBestDistance = save.dailyBestDistance,
            bestDistance = save.bestDistance,
            puissance = SkinStats.totalPuissance(save),
            vitesse = SkinStats.totalVitesse(save),
            achievementsCount = save.unlockedAchievements.size,
            updatedAtMillis = null,
        )

        fun fromPublic(p: PublicProfile) = ProfileData(
            pseudo = p.pseudo,
            avatarEmoji = p.avatarEmoji,
            isPrivate = p.isPrivate,
            equippedSkin = p.equippedSkin,
            ownedSkins = p.ownedSkins,
            totalMoneyEarned = p.totalMoneyEarned,
            dailyEarnings = p.dailyEarnings,
            dailyBestDistance = p.dailyBestDistance,
            bestDistance = p.bestDistance,
            puissance = p.puissance,
            vitesse = p.vitesse,
            achievementsCount = p.achievementsCount,
            updatedAtMillis = p.updatedAtMillis,
        )
    }
}

/**
 * MON profil (`renderProfile()` sans uid) : lu directement dans la
 * sauvegarde, donc complet même hors ligne. Bouton « Offrir de l'argent »,
 * aperçu de mes succès et accès à leur liste complète.
 */
@Composable
fun ProfileScreen(
    save: GameSave,
    session: CloudSession,
    onSaveChange: (GameSave) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showGift by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ProfileDetail?>(null) }
    val data = ProfileData.fromSave(save)
    ModalScreen(tr("btnProfile"), onBack, opaque = true) {
        ProfileBody(
            data = data,
            uid = session.uid,
            isOwn = true,
            save = save,
            session = session,
            onOpenAchievements = onOpenAchievements,
            onShowDetail = { detail = it },
            onGift = { showGift = true },
        )
    }
    // Hors de ModalScreen : ses fenêtres couvrent l'écran entier, ce qu'elles
    // ne pourraient pas faire depuis l'intérieur d'une colonne qui défile.
    ProfileDetailDialogs(detail, data, session.uid, session, onOpenPlayer) { detail = null }
    if (showGift) {
        GiftSendDialog(save = save, session = session, onSaveChange = onSaveChange, onDismiss = { showGift = false })
    }
}

/**
 * Le profil d'un AUTRE joueur, ouvert depuis le classement ou une liste
 * d'abonnés — `renderProfile(uid)` côté site : lit `profiles/{uid}`, avec le
 * bouton Suivre et, pour un compte privé, seulement une banderole (son score
 * reste visible au classement).
 */
@Composable
fun PlayerProfileScreen(
    uid: String,
    save: GameSave,
    session: CloudSession,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
) {
    var profile by remember(uid) { mutableStateOf<PublicProfile?>(null) }
    var status by remember(uid) { mutableStateOf(LoadStatus.LOADING) }
    var detail by remember(uid) { mutableStateOf<ProfileDetail?>(null) }

    LaunchedEffect(uid, session.state) {
        if (session.state == CloudState.NOT_CONFIGURED || session.state == CloudState.OFFLINE) {
            status = LoadStatus.UNAVAILABLE
            return@LaunchedEffect
        }
        status = try {
            profile = session.bridge.fetchPublicProfile(uid)
            if (profile == null) LoadStatus.NOT_FOUND else LoadStatus.READY
        } catch (e: Exception) {
            LoadStatus.ERROR
        }
    }

    ModalScreen(tr("btnProfile"), onBack, opaque = true) {
        val data = profile
        when (status) {
            LoadStatus.LOADING -> DialogText(tr("profileLoading"))
            LoadStatus.UNAVAILABLE -> DialogText(tr("leaderboardUnavailable"))
            LoadStatus.ERROR -> DialogText(tr("leaderboardError"))
            LoadStatus.NOT_FOUND -> DialogText(tr("profileNotFound"))
            LoadStatus.READY -> if (data != null) {
                ProfileBody(
                    data = ProfileData.fromPublic(data),
                    uid = uid,
                    isOwn = false,
                    save = save,
                    session = session,
                    onOpenAchievements = {},
                    onShowDetail = { detail = it },
                    onGift = {},
                )
            }
        }
    }
    profile?.let { ProfileDetailDialogs(detail, ProfileData.fromPublic(it), uid, session, onOpenPlayer) { detail = null } }
}

private enum class LoadStatus { LOADING, UNAVAILABLE, ERROR, NOT_FOUND, READY }

/** Les fenêtres de détail du profil (`profile-detail-modal`). */
private sealed interface ProfileDetail {
    data object Skins : ProfileDetail
    data object Level : ProfileDetail
    data class Follows(val direction: FollowDirection) : ProfileDetail
}

/** `paintProfile(data, isOwn, uid)` côté site. */
@Composable
private fun ProfileBody(
    data: ProfileData,
    uid: String?,
    isOwn: Boolean,
    save: GameSave,
    session: CloudSession,
    onOpenAchievements: () -> Unit,
    onShowDetail: (ProfileDetail) -> Unit,
    onGift: () -> Unit,
) {
    val online = session.state == CloudState.GUEST || session.state == CloudState.LINKED
    var following by remember(uid) { mutableStateOf<Int?>(null) }
    var followers by remember(uid) { mutableStateOf<Int?>(null) }
    var rank by remember(uid) { mutableStateOf<Int?>(null) }

    // `loadFollowCounts()` + `computeRank()` : tirets tant qu'ils n'ont pas
    // répondu, ou hors ligne.
    LaunchedEffect(uid, online, data.bestDistance) {
        if (uid == null || !online) return@LaunchedEffect
        runCatching { following = session.bridge.countFollowing(uid) }
        runCatching { followers = session.bridge.countFollowers(uid) }
        runCatching { rank = session.bridge.computeRank(session.bridge.scoresCollectionFor(save), data.bestDistance) }
    }

    // .profile-header
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            Text(data.avatarEmoji.ifBlank { "🎒" }, fontSize = 40.sp)
        }
        Text(data.pseudo, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        // `formatPresenceHtml()` : mon propre profil est toujours « en ligne »
        // (je suis en train de l'utiliser), celui d'un autre se déduit de
        // son dernier signe de vie.
        val status = if (isOwn) PresenceStatus.Online else Presence.of(data.updatedAtMillis, System.currentTimeMillis())
        Text(
            presenceText(status),
            color = if (status is PresenceStatus.Online) OnlineGreen else TextDim,
            fontSize = 12.5.sp,
            fontWeight = if (status is PresenceStatus.Online) FontWeight.Bold else FontWeight.Normal,
        )
        if (isOwn) {
            // Offrir de l'argent : indisponible hors ligne, comme wireGiftButton().
            if (online) {
                GameButton(tr("profileGiftButton"), small = true, onClick = onGift)
            } else {
                GameButton(tr("profileFollowUnavailable"), secondary = true, small = true) {}
            }
        } else if (uid != null) {
            FollowButton(targetUid = uid, session = session, online = online) { delta ->
                followers = followers?.plus(delta)
            }
        }
    }

    // .profile-top-row : pastille trousse (liste des skins), abonnements, abonnés.
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileCard(modifier = Modifier.weight(1f), onClick = { onShowDetail(ProfileDetail.Skins) }) {
            Box {
                TrousseSprite(skinId = data.equippedSkin, contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("+${data.ownedSkins.size}", color = OnAccent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        ProfileCard(modifier = Modifier.weight(1f), onClick = { onShowDetail(ProfileDetail.Follows(FollowDirection.FOLLOWING)) }) {
            Text(following?.toString() ?: "—", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(tr("profileFollowing"), color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        ProfileCard(modifier = Modifier.weight(1f), onClick = { onShowDetail(ProfileDetail.Follows(FollowDirection.FOLLOWERS)) }) {
            Text(followers?.toString() ?: "—", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(tr("profileFollowers"), color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }

    if (!isOwn && data.isPrivate) {
        // .profile-private-banner : le score reste au classement, seul le
        // détail du profil est masqué.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBorder)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔒 ${tr("profilePrivateBanner")}", color = TextColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    } else {
        val today = LocalDate.now()
        SectionTitle(tr("profileWeeklyTitle"))
        WeeklyChart(data.dailyEarnings, today)

        // .profile-stats-grid : 2 colonnes.
        val weeklyBest = DailyStats.weeklyBest(data.dailyBestDistance, today)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("🚀", "${"%.1f".format(weeklyBest)} m", tr("profileWeeklyBest"))
            StatTile("💰", "${data.totalMoneyEarned} $", tr("profileTotalEarned"))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile("🏅", rank?.let { "#$it" } ?: "—", tr("profileRank"))
            StatTile("💪", "${data.puissance + data.vitesse}", tr("profileLevel"), onClick = { onShowDetail(ProfileDetail.Level) })
        }

        // .profile-achv-header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("profileAchvTitle"), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (isOwn) GameButton(tr("profileViewAll"), secondary = true, small = true, onClick = onOpenAchievements)
        }
        if (isOwn) {
            // .profile-achv-preview : les succès débloqués, en pastilles rondes.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (a in Achievements.ALL.filter { it.id in save.unlockedAchievements }) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                            .border(2.dp, PanelBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(a.emoji, fontSize = 22.sp)
                    }
                }
            }
        }
        Text(
            "${data.achievementsCount} / ${Achievements.ALL.size}",
            color = TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/**
 * Les fenêtres de détail du profil (`profile-detail-modal`) : trousses
 * possédées, niveau, listes d'abonnés/abonnements.
 */
@Composable
private fun ProfileDetailDialogs(
    detail: ProfileDetail?,
    data: ProfileData,
    uid: String?,
    session: CloudSession,
    onOpenPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val online = session.state == CloudState.GUEST || session.state == CloudState.LINKED
    when (detail) {
        null -> Unit
        ProfileDetail.Skins -> SkinsDetailDialog(data, onDismiss = onDismiss)
        ProfileDetail.Level -> InfoDialog(title = tr("profileLevel"), onDismiss = onDismiss) {
            LevelRow("💪 ${tr("statPuissance")}", data.puissance)
            LevelRow("⚡ ${tr("statVitesse")}", data.vitesse)
        }
        is ProfileDetail.Follows -> FollowListDialog(
            uid = uid,
            direction = detail.direction,
            session = session,
            online = online,
            onOpenPlayer = { other ->
                onDismiss()
                onOpenPlayer(other)
            },
            onDismiss = onDismiss,
        )
    }
}

/** Le vert de `.profile-status.online` (#3ddc84). */
private val OnlineGreen = Color(0xFF3DDC84)

/** Bouton « Suivre » / « Abonné(e) » (`wireFollowButton()`). */
@Composable
private fun FollowButton(targetUid: String, session: CloudSession, online: Boolean, onFollowersDelta: (Int) -> Unit) {
    val me = session.uid
    if (!online || me == null || me == targetUid) {
        if (me != targetUid) GameButton(tr("profileFollowUnavailable"), secondary = true, small = true) {}
        return
    }
    var following by remember(targetUid) { mutableStateOf<Boolean?>(null) }
    var busy by remember(targetUid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val errorText = tr("profileFollowError")
    LaunchedEffect(targetUid, me) {
        following = runCatching { session.bridge.isFollowing(me, targetUid) }.getOrNull()
    }
    val current = following
    GameButton(
        when (current) {
            null -> tr("profileFollowLoading")
            true -> tr("profileUnfollow")
            false -> tr("profileFollow")
        },
        secondary = current == true,
        small = true,
    ) {
        if (current == null || busy) return@GameButton
        busy = true
        scope.launch {
            try {
                if (current) session.bridge.unfollow(me, targetUid) else session.bridge.follow(me, targetUid)
                following = !current
                onFollowersDelta(if (current) -1 else 1)
            } catch (e: Exception) {
                toaster(errorText)
            }
            busy = false
        }
    }
}

/** `.profile-card` : carte cliquable, contenu centré. */
@Composable
private fun ProfileCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(84.dp)
            .clip(shape)
            .background(CardBg)
            .border(2.dp, PanelBorder, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}

/** `.profile-section-title` */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
    )
}

/**
 * `.weekly-chart` : les gains des 7 derniers jours en barres, aujourd'hui en
 * doré. Hauteur relative au meilleur jour, 4 % minimum pour qu'un jour sans
 * lancer reste visible.
 */
@Composable
private fun WeeklyChart(dailyEarnings: Map<String, Long>, today: LocalDate) {
    val days = DailyStats.last7DayKeys(today)
    val values = days.map { dailyEarnings[DailyStats.key(it)] ?: 0L }
    val max = maxOf(1L, values.maxOrNull() ?: 0L)
    val lang = LocalLang.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(shape)
            .background(CardBg)
            .border(2.dp, PanelBorder, shape)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { i, day ->
            val pct = maxOf(4, (values[i] * 100.0 / max).roundToInt())
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 26.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(pct / 100f * 0.8f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (day == today) Accent else PanelBorder),
                )
                Text(I18n.weekdayLetter(day.dayOfWeek.value, lang), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** `.profile-stat-tile` */
@Composable
private fun RowScope.StatTile(icon: String, value: String, label: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(CardBg)
            .border(2.dp, PanelBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Text(value, color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        Text(label, color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** `.level-detail-row` */
@Composable
private fun LevelRow(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextColor, fontSize = 14.sp)
        Text(value.toString(), color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** `.skin-list-row` : ligne d'une liste (skins possédés, abonnés...). */
@Composable
private fun ListRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(2.dp, PanelBorder, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * `buildSkinsListHtml()` : toutes les trousses, cadenas sur celles pas encore
 * possédées, badge sur celle équipée.
 */
@Composable
private fun SkinsDetailDialog(data: ProfileData, onDismiss: () -> Unit) {
    InfoDialog(title = tr("profileSkinsTitle"), onDismiss = onDismiss) {
        for (skin in Skins.ALL) {
            val owned = skin.id in data.ownedSkins
            val name = SKIN_LABELS[skin.id]?.first ?: skin.id
            ListRow(modifier = Modifier.alpha(if (owned) 1f else 0.45f)) {
                TrousseSprite(skinId = skin.id, contentDescription = null, modifier = Modifier.size(32.dp), locked = !owned)
                Text(name, color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                when {
                    owned && skin.id == data.equippedSkin ->
                        Text(tr("profileEquippedTag"), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    !owned -> Text("🔒", fontSize = 14.sp)
                }
            }
        }
    }
}

/** `openFollowListModal(uid, type)` : toucher une ligne ouvre ce profil. */
@Composable
private fun FollowListDialog(
    uid: String?,
    direction: FollowDirection,
    session: CloudSession,
    online: Boolean,
    onOpenPlayer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var rows by remember(uid, direction) { mutableStateOf<List<Pair<String, PublicProfile?>>?>(null) }
    var failed by remember(uid, direction) { mutableStateOf(false) }
    LaunchedEffect(uid, direction, online) {
        if (uid == null || !online) return@LaunchedEffect
        try {
            rows = session.bridge.listFollows(uid, direction)
        } catch (e: Exception) {
            failed = true
        }
    }
    val title = tr(if (direction == FollowDirection.FOLLOWING) "profileFollowing" else "profileFollowers")
    InfoDialog(title = title, onDismiss = onDismiss) {
        val list = rows
        when {
            uid == null || !online -> DialogText(tr("leaderboardUnavailable"))
            failed -> DialogText(tr("leaderboardError"))
            list == null -> DialogText(tr("profileLoading"))
            list.isEmpty() -> DialogText(tr("profileFollowListEmpty"))
            else -> for ((otherUid, profile) in list) {
                ListRow(modifier = Modifier.clickable { onOpenPlayer(otherUid) }) {
                    Text(profile?.avatarEmoji.orEmpty().ifBlank { "🎒" }, fontSize = 22.sp)
                    Text(profile?.pseudo ?: "?", color = TextColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Les libellés de `formatPresenceHtml()` côté site. */
@Composable
private fun presenceText(status: PresenceStatus): String = when (status) {
    PresenceStatus.Online -> tr("profileOnline")
    is PresenceStatus.MinutesAgo -> tr("profileLastSeenMinutes", "n" to status.minutes)
    is PresenceStatus.HoursAgo -> tr("profileLastSeenHours", "n" to status.hours)
    is PresenceStatus.DaysAgo -> tr("profileLastSeenDays", "n" to status.days)
    PresenceStatus.Unknown -> tr("profileLastSeenUnknown")
}
