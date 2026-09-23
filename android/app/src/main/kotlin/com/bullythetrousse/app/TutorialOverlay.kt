package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tutoriel du tout premier lancement, porté de `#tutorial-overlay` et de
 * `TUTORIAL_SLIDES` (index.html) : 6 diapos avec une icône, un titre, un
 * texte, les pastilles de progression `.tutorial-dot`, un bouton "Passer"
 * en haut à droite et "Suivant" / "C'est parti !" en bas.
 *
 * Les textes sont repris mot pour mot du site (version française).
 */
data class TutorialSlide(val icon: String, val title: String, val text: String)

val TUTORIAL_SLIDES: List<TutorialSlide> = listOf(
    TutorialSlide("🎒", "Bienvenue dans Bully the Trousse !", "Le but du jeu : lancer ta trousse le plus loin possible !"),
    TutorialSlide("🤓👆", "Comment lancer", "Clique 3 fois : une fois pour charger la PUISSANCE, une fois pour verrouiller la PRÉCISION, et une dernière fois pour lancer !"),
    TutorialSlide("💰", "Gagne de l'argent", "Plus tu lances loin, plus tu gagnes d'argent. Dépense-le dans la Boutique pour améliorer ta Puissance, ta Vitesse, ou débloquer des skins (d'autres skins arriveront plus tard)."),
    TutorialSlide("🏆", "Défie le monde entier (enfin, ceux qui jouent au jeu...)", "Bats ton record pour apparaître dans le Classement mondial, visible depuis le menu !"),
    TutorialSlide("🔁", "Alors partage un max !", "Partage le jeu à tes amis pour qu'ils puissent eux aussi battre ton record !"),
    TutorialSlide("🤗", "Visite mes autres sites !", "Va sur evyverse.vercel.app pour découvrir mes autres sites !"),
)

@Composable
fun TutorialOverlay(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val slide = TUTORIAL_SLIDES[step]
    val isLast = step == TUTORIAL_SLIDES.lastIndex

    // Voile sombre plein écran, puis la boîte du tutoriel au centre.
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xCC060A16)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(PanelBg)
                .border(2.dp, PanelBorder, RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                GameButton("Passer ✕", secondary = true, small = true, onClick = onDone)
            }
            Text(slide.icon, fontSize = 44.sp, textAlign = TextAlign.Center)
            Text(
                slide.title,
                color = Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                slide.text,
                color = TextColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            // .tutorial-dot : une pastille par diapo, dorée sur la courante.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TUTORIAL_SLIDES.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (index == step) Accent else Color.White.copy(alpha = 0.25f)),
                    )
                }
            }
            GameButton(if (isLast) "C'est parti !" else "Suivant") {
                if (isLast) onDone() else step++
            }
        }
    }
}
