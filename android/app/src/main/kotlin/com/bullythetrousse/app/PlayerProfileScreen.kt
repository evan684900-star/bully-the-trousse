package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.Presence
import com.bullythetrousse.core.PresenceStatus
import kotlinx.coroutines.launch

/**
 * Le profil d'un AUTRE joueur, ouvert en touchant sa ligne du classement —
 * portage de `renderProfile(uid)`/`paintProfile(data, false, uid)` côté site.
 *
 * Il ne lit que `profiles/{uid}`, le document public : le détail complet de
 * la sauvegarde (`users/{uid}`) reste privé, et un compte marqué privé
 * n'affiche qu'une banderole à la place de ses statistiques — son score
 * reste visible au classement, comme sur le site.
 */
@Composable
fun PlayerProfileScreen(
    uid: String,
    session: CloudSession,
    onBack: () -> Unit,
) {
    var profile by remember(uid) { mutableStateOf<PublicProfile?>(null) }
    var failed by remember(uid) { mutableStateOf(false) }
    var loading by remember(uid) { mutableStateOf(true) }
    var following by remember(uid) { mutableStateOf<Boolean?>(null) }
    var followers by remember(uid) { mutableStateOf<Int?>(null) }
    var followingCount by remember(uid) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid, session.state) {
        if (session.state == CloudState.NOT_CONFIGURED || session.state == CloudState.OFFLINE) {
            loading = false
            failed = true
            return@LaunchedEffect
        }
        try {
            profile = session.bridge.fetchPublicProfile(uid)
            followers = session.bridge.countFollowers(uid)
            followingCount = session.bridge.countFollowing(uid)
            session.uid?.let { me -> if (me != uid) following = session.bridge.isFollowing(me, uid) }
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }

    ModalScreen("👤 Profil", onBack, opaque = true) {
        val data = profile
        when {
            loading -> Text("Chargement du profil…", color = TextDim, fontSize = 13.sp)
            failed -> Text(
                "Profil indisponible : pas de connexion.",
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            data == null -> Text("Profil introuvable.", color = TextDim, fontSize = 13.sp)
            else -> {
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
                        Text(data.avatarEmoji.ifBlank { "🎒" }, fontSize = 40.sp)
                    }
                    Text(data.pseudo, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val status = Presence.of(data.updatedAtMillis, System.currentTimeMillis())
                    Text(
                        presenceText(status),
                        color = if (status is PresenceStatus.Online) Money else TextDim,
                        fontSize = 12.5.sp,
                        fontWeight = if (status is PresenceStatus.Online) FontWeight.Bold else FontWeight.Normal,
                    )
                    // Bouton s'abonner / abonné(e) : absent sur son propre profil.
                    if (session.uid != null && session.uid != uid) {
                        val isFollowing = following
                        GameButton(
                            when (isFollowing) {
                                null -> "…"
                                true -> "✅ Abonné(e)"
                                false -> "➕ Suivre"
                            },
                            secondary = isFollowing == true,
                            small = true,
                        ) {
                            val me = session.uid ?: return@GameButton
                            val current = following ?: return@GameButton
                            // Bascule tout de suite à l'écran : l'aller-retour
                            // réseau ne doit pas donner l'impression que le
                            // bouton n'a pas répondu.
                            following = !current
                            followers = followers?.plus(if (current) -1 else 1)
                            scope.launch {
                                try {
                                    if (current) {
                                        session.bridge.unfollow(me, uid)
                                    } else {
                                        session.bridge.follow(me, uid)
                                    }
                                } catch (e: Exception) {
                                    following = current // échec : on remet l'état d'avant
                                    followers = followers?.plus(if (current) 1 else -1)
                                }
                            }
                        }
                    }
                }

                // .profile-top-row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileCard(modifier = Modifier.weight(1f)) {
                        TrousseSprite(
                            skinId = data.equippedSkin,
                            contentDescription = "trousse équipée",
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            "+${data.ownedSkinsCount}",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    ProfileCard(modifier = Modifier.weight(1f)) {
                        Text(
                            followingCount?.toString() ?: "—",
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

                if (data.isPrivate) {
                    // .profile-private-banner : le score reste au classement,
                    // seul le détail du profil est masqué.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBg)
                            .border(2.dp, PanelBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "🔒 Ce compte est privé",
                            color = TextDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    ProfileRow("Record", "${"%.1f".format(data.bestDistance)} m")
                    ProfileRow("Argent gagné", "${data.totalMoneyEarned} $")
                    ProfileRow("Niveau", "${data.puissance + data.vitesse}")
                    ProfileRow("Succès", "${data.achievementsCount} / ${Achievements.ALL.size}")
                }
            }
        }
    }
}

/** Les libellés de `formatPresenceHtml()` côté site. */
private fun presenceText(status: PresenceStatus): String = when (status) {
    PresenceStatus.Online -> "🟢 En ligne"
    is PresenceStatus.MinutesAgo -> "⚪ Vu il y a ${status.minutes} min"
    is PresenceStatus.HoursAgo -> "⚪ Vu il y a ${status.hours} h"
    is PresenceStatus.DaysAgo -> "⚪ Vu il y a ${status.days} j"
    PresenceStatus.Unknown -> "⚪ Statut inconnu"
}
