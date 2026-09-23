package com.bullythetrousse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.bullythetrousse.core.Achievements
import com.bullythetrousse.core.BeachCinematic
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.Gifts
import com.bullythetrousse.core.GraphicsQuality
import com.bullythetrousse.core.HapticEvent
import com.bullythetrousse.core.Lang
import com.bullythetrousse.core.VolcanoCinematic
import com.bullythetrousse.core.I18n
import com.bullythetrousse.core.PlayTimeTracker
import com.bullythetrousse.core.SfxCatalog
import com.bullythetrousse.core.name
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Treizième tranche du portage natif : écrans séparés (Menu / Jeu /
 * Boutique) et un vrai sélecteur de monde sur le menu (voir
 * MenuScreen.kt/WorldSelector), portage de buildWorldsRow()/
 * handleWorldCardClick() côté web — remplace l'unique écran fourre-tout
 * des tranches précédentes. `GameRoot` ne fait plus que choisir quel écran
 * afficher ; toute la logique de chaque écran vit dans son propre fichier
 * (MenuScreen.kt, GameScreen.kt, ShopScreen.kt, *CinematicScreen.kt).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vrai plein écran : le contenu s'étend derrière la barre de statut/
        // navigation (comme le jeu web, qui occupe toute la fenêtre) au lieu
        // de laisser Android réserver une bande grise au-dessus.
        enableEdgeToEdge()
        setContent {
            BullyTheTrousseTheme {
                // Pas de marge ici : chaque écran peint son propre fond en
                // pleine largeur (le ciel du menu passe derrière la barre de
                // statut, comme sur le site) et applique lui-même la marge de
                // sécurité à son contenu.
                Box(modifier = Modifier.fillMaxSize().background(AppBg)) {
                    GameRoot()
                }
            }
        }
    }
}

/**
 * Les destinations de l'app. Navigation volontairement simple (juste "où on
 * est", pas de pile d'historique) : chaque écran sait comment revenir au
 * Menu, ce qui suffit pour la profondeur de navigation de ce jeu.
 */
sealed interface Screen {
    data object Menu : Screen
    data object Game : Screen
    data object Shop : Screen
    data object Leaderboard : Screen
    data object Profile : Screen
    data object AchievementsList : Screen
    data object Challenges : Screen
    data object Settings : Screen
    data object Links : Screen
    data object Changelog : Screen
    data object Account : Screen
    data object VolcanoCinematic : Screen
    data object BeachCinematic : Screen

    /** Le profil d'un autre joueur ; [from] est l'écran où revenir (le
     *  classement, ou mon profil quand on l'ouvre depuis une liste d'abonnés). */
    data class PlayerProfile(val uid: String, val from: Screen = Screen.Leaderboard) : Screen
}

/**
 * Les destinations qui sont des MODALES côté site (`.modal-overlay`) : elles
 * se posent par-dessus l'écran courant, assombri à 50 %, au lieu de le
 * remplacer. C'est ce qui fait qu'on voit encore le menu derrière les
 * Réglages, comme sur le site.
 */
private val MODAL_SCREENS: Set<Screen> = setOf(
    Screen.Settings,
    Screen.Account,
    Screen.Links,
    Screen.Changelog,
    Screen.AchievementsList,
    Screen.Challenges,
)

// Le classement et les profils sont de vrais ÉCRANS côté site
// (`screens.leaderboard`/`screens.profile`), pas des `.modal-overlay` :
// ils remplacent le menu au lieu de le recouvrir.
private fun Screen.isModal(): Boolean = this in MODAL_SCREENS

@Composable
fun GameRoot() {
    val context = LocalContext.current
    val repository = remember { SaveRepository(context) }
    var save by remember { mutableStateOf(repository.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    // Le dernier écran de fond : une modale se pose PAR-DESSUS lui sans le
    // remplacer (voir isModal), donc il faut le retenir pour continuer à le
    // dessiner derrière le voile.
    var baseScreen by remember { mutableStateOf<Screen>(Screen.Menu) }

    // Lecteurs partagés par toute l'app (voir SfxPlayer, HapticsPlayer),
    // créés avant updateSave() qui s'en sert pour le succès débloqué.
    val sfx = rememberSfxPlayer()
    val haptics = rememberHapticsPlayer()

    // Toasts de l'app entière, en file : plusieurs succès qui tombent d'un
    // coup s'affichent l'un après l'autre au lieu de s'écraser (voir
    // LocalToaster pour les écrans qui en émettent).
    val toasts = remember { mutableStateListOf<String>() }

    // La session en ligne, créée plus bas (elle a besoin d'updateSave pour
    // créditer les cadeaux) mais utilisée par updateSave pour publier les
    // succès : d'où cette référence renseignée juste après sa création.
    var cloudRef: CloudSession? = null

    // Cadeaux ramassés à la connexion, affichés dans une modale (voir
    // checkIncomingGifts() côté site) : lignes fusionnées par expéditeur + total.
    var receivedGifts by remember { mutableStateOf<Pair<List<Pair<String, Int>>, Int>?>(null) }

    // Temps de jeu et série d'écoute musicale, accumulés en mémoire et
    // reportés dans la sauvegarde toutes les 30 s (voir PlayTimeTracker).
    val playClock = remember { PlayTimeTracker(save.musicListenSeconds) }

    // Point de passage UNIQUE pour toute modification de la sauvegarde locale
    // (portage de persist() côté web, qui appelle checkAchievements() à
    // chaque appel — pas seulement après un lancer ou un achat). Sans ça,
    // les succès qui se déclenchent sans passer par ThrowFlight/applyPurchase
    // (débloquer le volcan, changer d'avatar, atteindre 5000 $...) ne
    // seraient constatés qu'au prochain lancer ou achat, au lieu de l'instant
    // où ils sont vraiment obtenus.
    fun updateSave(updated: GameSave) {
        // Les secondes de jeu en attente partent avec chaque écriture : sans
        // ça, une sauvegarde faite entre deux reports les écraserait.
        val withTime = playClock.flushInto(updated)
        val newlyUnlocked = Achievements.newlyUnlocked(withTime)
        val withAchievements = Achievements.apply(withTime)
        save = withAchievements
        repository.save(withAchievements)
        if (newlyUnlocked.isNotEmpty()) {
            // checkAchievements() : un toast par succès, sfxBuy(), et la
            // publication pour le pourcentage de joueurs.
            val lang = Lang.fromId(withAchievements.lang)
            newlyUnlocked.forEach { toasts += I18n.tr("achvUnlockedPrefix", lang) + it.name(lang) }
            sfx.play(SfxCatalog.BUY)
            haptics.play(HapticEvent.ACHIEVEMENT)
            cloudRef?.publishUnlocks(newlyUnlocked.map { it.id }, withAchievements.totalThrows)
        }
    }

    // Compte, sauvegarde cloud et classement (voir CloudSession). Silencieux
    // et sans effet tant que app/google-services.json n'est pas là : le jeu
    // reste entièrement jouable hors ligne.
    val cloud = rememberCloudSession(
        save = save,
        repository = repository,
        // La copie venue du cloud est DÉJÀ écrite sur le disque par la session
        // (avec l'horodatage du serveur) : la réécrire ici lui collerait
        // l'heure locale et ferait croire que ce téléphone vient de jouer.
        onSaveChange = { save = it },
        onGifts = { gifts, atLogin ->
            val total = Gifts.total(gifts)
            if (total > 0) {
                updateSave(save.copy(money = save.money + total))
                if (atLogin) {
                    receivedGifts = Gifts.mergeBySender(gifts) to total
                } else {
                    val lang = Lang.fromId(save.lang)
                    gifts.forEach { gift ->
                        toasts += I18n.tr("giftReceivedToast", lang, "pseudo" to gift.senderPseudo, "amount" to gift.amount)
                    }
                }
            }
        },
    )
    cloudRef = cloud

    // Langue jamais choisie : on devine d'après celle du téléphone, une seule
    // fois, comme l'initialisation du site (`navigator.language`).
    LaunchedEffect(Unit) {
        if (save.lang.isEmpty()) {
            updateSave(save.copy(lang = Lang.forDeviceLanguage(Locale.getDefault().language).id))
        }
    }

    // Une piste par monde, coupée par le bouton 🔊 (voir applyWorldMusic()),
    // en pause quand l'app n'est plus à l'écran.
    val inForeground by rememberAppInForeground()
    val musicPlaying = WorldMusic(world = save.currentWorld, muted = save.musicMuted, inForeground = inForeground)
    val currentMusicPlaying by rememberUpdatedState(musicPlaying)
    LaunchedEffect(inForeground) {
        if (!inForeground) return@LaunchedEffect
        while (true) {
            delay(1000)
            if (playClock.tick(currentMusicPlaying)) updateSave(save)
        }
    }

    // Le niveau de détail choisi dans les Réglages descend jusqu'aux écrans
    // qui dessinent, sans que les écrans intermédiaires aient à le porter
    // (voir LocalGraphicsQuality).
    CompositionLocalProvider(
        LocalGraphicsQuality provides GraphicsQuality.fromId(save.graphicsQuality),
        LocalSfx provides sfx,
        LocalHaptics provides haptics,
        LocalLang provides Lang.fromId(save.lang),
        LocalToaster provides { message -> toasts += message },
    ) {
        GameContent(
            save = save,
            screen = screen,
            cloud = cloud,
            repository = repository,
            baseScreen = baseScreen,
            updateSave = ::updateSave,
            goTo = { destination ->
                if (!destination.isModal()) baseScreen = destination
                screen = destination
            },
        )
        receivedGifts?.let { (rows, total) ->
            GiftsReceivedDialog(rows = rows, total = total, onDismiss = { receivedGifts = null })
        }
        Toast(message = toasts.firstOrNull(), onDismiss = { if (toasts.isNotEmpty()) toasts.removeAt(0) })
    }
}

/**
 * Le contenu de l'application : l'écran courant, le tutoriel du premier
 * lancement et la barre du bas. Extrait de [GameRoot] pour que le
 * fournisseur de qualité l'englobe d'un bloc.
 */
@Composable
private fun GameContent(
    save: GameSave,
    screen: Screen,
    cloud: CloudSession,
    repository: SaveRepository,
    baseScreen: Screen,
    updateSave: (GameSave) -> Unit,
    goTo: (Screen) -> Unit,
) {
    // Une modale se pose par-dessus l'écran de fond, assombri à 50 % —
    // `.modal-overlay { background: rgba(0,0,0,0.5) }` côté site. Sans ça
    // les Réglages remplaçaient le menu au lieu de le recouvrir.
    if (screen.isModal() && baseScreen != screen) {
        ScreenContent(
            save = save,
            screen = baseScreen,
            baseScreen = baseScreen,
            cloud = cloud,
            repository = repository,
            updateSave = updateSave,
            goTo = goTo,
        )
        // Le voile avale les taps : sans ça, toucher une zone vide des
        // Réglages actionnerait le bouton du menu resté visible dessous.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        )
    }

    ScreenContent(
        save = save,
        screen = screen,
        baseScreen = baseScreen,
        cloud = cloud,
        repository = repository,
        updateSave = updateSave,
        goTo = goTo,
    )

    // Tutoriel du tout premier lancement (showTutorialIfNeeded() côté web) :
    // il recouvre tout tant qu'il n'est pas terminé ou passé.
    if (!save.tutorialSeen) {
        TutorialOverlay(onDone = { updateSave(save.copy(tutorialSeen = true)) })
        return
    }

    // .links-btn + .corner-icons-right : en position:fixed côté web, donc
    // visibles par-dessus tous les écrans — sauf pendant les cinématiques,
    // qui occupent l'écran entier.
    val inCinematic = screen == Screen.VolcanoCinematic || screen == Screen.BeachCinematic
    if (!inCinematic) {
        BottomBar(
            musicMuted = save.musicMuted,
            onOpenLinks = { goTo(Screen.Links) },
            onToggleMute = { updateSave(save.copy(musicMuted = !save.musicMuted)) },
            onOpenSettings = { goTo(Screen.Settings) },
        )
    }
}

/** Un écran, sans le décor commun (voile des modales, tutoriel, barre du bas). */
@Composable
private fun ScreenContent(
    save: GameSave,
    screen: Screen,
    baseScreen: Screen,
    cloud: CloudSession,
    repository: SaveRepository,
    updateSave: (GameSave) -> Unit,
    goTo: (Screen) -> Unit,
) {
    // Fermer une modale rend la main à l'écran qu'elle recouvrait (le jeu,
    // le profil...), pas systématiquement au menu — comme côté site, où la
    // modale disparaît simplement de par-dessus l'écran actif.
    val closeModal = { goTo(baseScreen) }
    when (screen) {
        Screen.Menu -> MenuScreen(
            save = save,
            onSaveChange = updateSave,
            onPlay = { goTo(Screen.Game) },
            onOpenShop = { goTo(Screen.Shop) },
            onOpenLeaderboard = { goTo(Screen.Leaderboard) },
            onOpenProfile = { goTo(Screen.Profile) },
            onOpenAchievements = { goTo(Screen.AchievementsList) },
            onOpenChallenges = { goTo(Screen.Challenges) },
            onStartVolcanoCinematic = { goTo(Screen.VolcanoCinematic) },
            onStartBeachCinematic = { goTo(Screen.BeachCinematic) },
            onOpenChangelog = { goTo(Screen.Changelog) },
        )

        Screen.Leaderboard -> LeaderboardScreen(
            save = save,
            session = cloud,
            onSaveChange = updateSave,
            onOpenPlayer = { uid -> goTo(Screen.PlayerProfile(uid)) },
            onBack = { goTo(Screen.Menu) },
        )

        is Screen.PlayerProfile -> PlayerProfileScreen(
            uid = screen.uid,
            save = save,
            session = cloud,
            // Depuis une liste d'abonnés, on garde le même point de retour.
            onOpenPlayer = { other ->
                goTo(if (other == cloud.uid) Screen.Profile else Screen.PlayerProfile(other, screen.from))
            },
            onBack = { goTo(screen.from) },
        )

        Screen.Profile -> ProfileScreen(
            save = save,
            session = cloud,
            onSaveChange = updateSave,
            onOpenAchievements = { goTo(Screen.AchievementsList) },
            onOpenPlayer = { other ->
                goTo(if (other == cloud.uid) Screen.Profile else Screen.PlayerProfile(other, Screen.Profile))
            },
            onBack = { goTo(Screen.Menu) },
        )

        Screen.AchievementsList -> AchievementsScreen(save = save, session = cloud, onBack = closeModal)

        Screen.Challenges -> ChallengesScreen(
            save = save,
            onSaveChange = updateSave,
            onBack = closeModal,
        )

        Screen.Game -> GameScreen(
            save = save,
            onSaveChange = updateSave,
            onBackToMenu = { goTo(Screen.Menu) },
            onOpenShop = { goTo(Screen.Shop) },
        )

        Screen.Shop -> ShopScreen(
            save = save,
            onSaveChange = updateSave,
            onBackToMenu = { goTo(Screen.Menu) },
            onBackToGame = { goTo(Screen.Game) },
        )

        Screen.Settings -> SettingsScreen(
            save = save,
            session = cloud,
            onSaveChange = updateSave,
            onOpenAccount = { goTo(Screen.Account) },
            onBack = closeModal,
        )

        Screen.Account -> AccountScreen(
            save = save,
            session = cloud,
            repository = repository,
            onSaveChange = updateSave,
            onBack = { goTo(Screen.Settings) },
        )

        Screen.Links -> LinksScreen(onBack = closeModal)

        Screen.Changelog -> ChangelogScreen(onBack = closeModal)

        Screen.VolcanoCinematic -> VolcanoCinematicScreen(equippedSkin = save.equippedSkin, onFinished = { outcome ->
            updateSave(VolcanoCinematic.applyOutcome(save, outcome, System.currentTimeMillis()))
            goTo(Screen.Menu)
        })

        Screen.BeachCinematic -> BeachCinematicScreen(equippedSkin = save.equippedSkin, onFinished = {
            updateSave(BeachCinematic.applyOutcome(save))
            goTo(Screen.Menu)
        })
    }
}
