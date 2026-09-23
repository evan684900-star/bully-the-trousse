package com.bullythetrousse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `.modal-overlay` + `.info-box` du site : voile sombre, boîte centrée avec
 * un titre, un contenu qui défile s'il est long, et des boutons en bas.
 *
 * Toucher le voile ferme la boîte comme côté site (`e.target === el`), sauf
 * si [dismissOnScrim] est faux — pour les choix qu'un appui distrait ne doit
 * pas trancher (quelle partie garder...).
 *
 * @param buttons Les boutons du bas ; par défaut un simple « OK » (`btnOk`).
 */
@Composable
fun InfoDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissOnScrim: Boolean = true,
    buttons: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (dismissOnScrim) onDismiss() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ShopBg)
                .border(2.dp, PanelBorder, RoundedCornerShape(18.dp))
                // Absorbe les appuis dans la boîte : sans ça ils traverseraient
                // jusqu'au voile et fermeraient la fenêtre.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                color = Accent,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            if (buttons != null) {
                buttons()
            } else {
                GameButton(tr("btnOk"), modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}

/** `.settings-hint` / texte d'accompagnement d'une boîte, centré et estompé. */
@Composable
fun DialogText(text: String, color: Color = TextDim) {
    Text(text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
}
