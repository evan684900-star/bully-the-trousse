package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.bullythetrousse.core.I18n
import com.bullythetrousse.core.Lang

/**
 * La langue active, fournie depuis MainActivity à partir de `save.lang`.
 * Changer de langue recompose toute l'interface d'un coup, comme
 * `applyLanguage()` côté site qui réécrit tous les `data-i18n` de la page.
 */
val LocalLang = staticCompositionLocalOf { Lang.FR }

/**
 * `tr(key)` côté site, pour l'interface : le texte de [key] dans la langue
 * active, placeholders remplacés (`tr("giftSent", "amount" to 50)`).
 */
@Composable
@ReadOnlyComposable
fun tr(key: String, vararg args: Pair<String, Any?>): String =
    I18n.tr(key, LocalLang.current, *args)
