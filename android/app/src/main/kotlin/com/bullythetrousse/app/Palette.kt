package com.bullythetrousse.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Le thème actif (`applyTheme()` côté site), tenu à jour par MainActivity
 * depuis `save.theme`.
 *
 * C'est un état observable plutôt qu'un CompositionLocal : les couleurs
 * ci-dessous sont lues aussi bien par les écrans que par le code de dessin
 * des canvas, qui ne peut pas lire un CompositionLocal. Changer de thème
 * recompose et redessine tout ce qui les a lues.
 */
object AppTheme {
    var isLight by mutableStateOf(false)
}

/**
 * Les variables CSS du site, reprises une par une (voir le bloc `:root` en
 * haut d'index.html). `:root` est le thème SOMBRE (le défaut) ;
 * `[data-theme="light"]` n'en surcharge que sept, reprises ici à l'identique.
 *
 * Tout écran porté doit piocher ici plutôt que réinventer des couleurs :
 * c'est ce qui garantit que l'app et le site restent identiques.
 */
val SkyTop = Color(0xFF7EC8E3) // --sky-top
val SkyBottom = Color(0xFFCDEFFD) // --sky-bottom
val Ground = Color(0xFF8A8F98) // --ground
val GroundDark = Color(0xFF6F7480) // --ground-dark
val PanelBg: Color get() = if (AppTheme.isLight) Color(0xEBFFFFFF) else Color(0xEB141824) // --panel-bg : rgba(20,24,36,0.92) / rgba(255,255,255,0.92)
val PanelBorder: Color get() = if (AppTheme.isLight) Color(0xFFD8DCE6) else Color(0xFF3A4260) // --panel-border
val Accent = Color(0xFFFFD23F) // --accent
val Accent2 = Color(0xFFFF6B6B) // --accent-2
val Money = Color(0xFF6BFFB0) // --money
val TextColor: Color get() = if (AppTheme.isLight) Color(0xFF1A1F2E) else Color(0xFFF4F6FF) // --text
val TextDim: Color get() = if (AppTheme.isLight) Color(0xFF5C6478) else Color(0xFFAAB0C8) // --text-dim
val AppBg: Color get() = if (AppTheme.isLight) Color(0xFFEEF1F7) else Color(0xFF10131C) // --app-bg
val ShopBg: Color get() = if (AppTheme.isLight) Color(0xFFF4F6FB) else Color(0xFF171B28) // --shop-bg
val CardBg: Color get() = if (AppTheme.isLight) Color(0xFFFFFFFF) else Color(0xFF1F2437) // --card-bg

/** Texte sombre posé sur un aplat doré (`.btn` : `color: #1a1a1a`). */
val OnAccent = Color(0xFF1A1A1A)

/** `.btn.secondary` et son ombre portée "dure". */
val ButtonSecondary = Color(0xFF4B5570)
val ButtonSecondaryShadow = Color(0xFF2F374A)

/** Ombre portée "dure" du `.btn` principal (`box-shadow: 0 5px 0 #c79a1f`). */
val ButtonAccentShadow = Color(0xFFC79A1F)

/** Sous-titre de `.title-card p` : bleu nuit fixe, volontairement peu lisible
 *  sur le ciel assombri — c'est exactement le rendu du site. */
val TitleSubtitle = Color(0xFF2C3550)

/**
 * `.screen` : le fond commun à tous les écrans — ciel sur les 60 % du haut,
 * puis le sol. `linear-gradient(180deg, var(--sky-top), var(--sky-bottom)
 * 60%, var(--ground) 60%, var(--ground-dark) 100%)`.
 */
val ScreenBackground = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to SkyTop,
        0.6f to SkyBottom,
        0.6f to Ground,
        1f to GroundDark,
    ),
)
