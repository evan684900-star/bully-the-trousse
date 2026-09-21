package com.bullythetrousse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bullythetrousse.core.BeachCinematic
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.VolcanoCinematic

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
    data object VolcanoCinematic : Screen
    data object BeachCinematic : Screen
}

@Composable
fun GameRoot() {
    val context = LocalContext.current
    val repository = remember { SaveRepository(context) }
    var save by remember { mutableStateOf(repository.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    fun updateSave(updated: GameSave) {
        save = updated
        repository.save(updated)
    }

    when (screen) {
        Screen.Menu -> MenuScreen(
            save = save,
            onSaveChange = ::updateSave,
            onPlay = { screen = Screen.Game },
            onOpenShop = { screen = Screen.Shop },
            onOpenLeaderboard = { screen = Screen.Leaderboard },
            onOpenProfile = { screen = Screen.Profile },
            onOpenAchievements = { screen = Screen.AchievementsList },
            onOpenChallenges = { screen = Screen.Challenges },
            onStartVolcanoCinematic = { screen = Screen.VolcanoCinematic },
            onStartBeachCinematic = { screen = Screen.BeachCinematic },
        )

        Screen.Leaderboard -> LeaderboardScreen(onBack = { screen = Screen.Menu })

        Screen.Profile -> ProfileScreen(save = save, onBack = { screen = Screen.Menu })

        Screen.AchievementsList -> AchievementsScreen(save = save, onBack = { screen = Screen.Menu })

        Screen.Challenges -> ChallengesScreen(
            save = save,
            onSaveChange = ::updateSave,
            onBack = { screen = Screen.Menu },
        )

        Screen.Game -> GameScreen(
            save = save,
            onSaveChange = ::updateSave,
            onBackToMenu = { screen = Screen.Menu },
            onOpenShop = { screen = Screen.Shop },
        )

        Screen.Shop -> ShopScreen(
            save = save,
            onSaveChange = ::updateSave,
            onBackToMenu = { screen = Screen.Menu },
        )

        Screen.VolcanoCinematic -> VolcanoCinematicScreen(onFinished = { outcome ->
            updateSave(VolcanoCinematic.applyOutcome(save, outcome, System.currentTimeMillis()))
            screen = Screen.Menu
        })

        Screen.BeachCinematic -> BeachCinematicScreen(onFinished = {
            updateSave(BeachCinematic.applyOutcome(save))
            screen = Screen.Menu
        })
    }
}
