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
import com.bullythetrousse.core.Lang

/**
 * Tutoriel, porté de `#tutorial-overlay` et de `startTutorial()` : une
 * icône, un titre, un texte, les pastilles de progression `.tutorial-dot`,
 * « Passer » en haut à droite et « Suivant » / « C'est parti ! » en bas.
 *
 * Trois jeux de diapos (voir [Tutorial], généré depuis le site) : les bases
 * au tout premier lancement, puis Volcans et Plage à leur déblocage — tous
 * rejouables depuis les Réglages.
 */
@Composable
fun TutorialOverlay(tutorial: Tutorial, onDone: () -> Unit) {
    var step by remember(tutorial) { mutableIntStateOf(0) }
    val slides = tutorial.slides
    val slide = slides[step]
    val isLast = step == slides.lastIndex
    val english = LocalLang.current == Lang.EN

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
                GameButton(tr("tutorialSkip"), secondary = true, small = true, onClick = onDone)
            }
            Text(slide.icon, fontSize = 44.sp, textAlign = TextAlign.Center)
            Text(
                if (english) slide.titleEn else slide.titleFr,
                color = Accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                if (english) slide.textEn else slide.textFr,
                color = TextColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            // .tutorial-dot : une pastille par diapo, dorée sur la courante.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slides.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (index == step) Accent else Color.White.copy(alpha = 0.25f)),
                    )
                }
            }
            GameButton(tr(if (isLast) "tutorialDone" else "tutorialNext")) {
                if (isLast) onDone() else step++
            }
        }
    }
}
