package com.bullythetrousse.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.bullythetrousse.core.QualityProfile
import com.bullythetrousse.core.Skins
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Le sprite unique de la trousse (`trousse-skin-1.png` côté web), partagé
 * par tout ce qui l'affiche : l'aperçu du menu, les cartes de la boutique,
 * le profil, le canvas de jeu et les cinématiques.
 *
 * Pourquoi un cache plutôt que `painterResource()` : l'image fait 980×980,
 * et `painterResource` la redécode à CHAQUE point d'appel. L'onglet Skins de
 * la boutique en affiche quatorze d'un coup, soit quatorze décodages pendant
 * le changement d'onglet — d'où la latence. Ici l'image n'est décodée qu'une
 * fois pour toute la vie du processus.
 *
 * Le fichier vit dans `res/drawable-nodpi/` et non `res/drawable/` : sans
 * qualificateur, Android considère une image comme dessinée pour un écran
 * mdpi et la ré-échantillonne à la densité de l'appareil — ×2,75 sur un
 * écran xxhdpi, soit un bitmap de 2695×2695 (29 Mo) au lieu de 3,8 Mo.
 */
@Volatile
private var cachedSprite: ImageBitmap? = null
private val spriteLock = Any()

fun trousseSprite(context: Context): ImageBitmap {
    cachedSprite?.let { return it }
    return synchronized(spriteLock) {
        cachedSprite ?: BitmapFactory
            .decodeResource(context.resources, R.drawable.trousse_skin_1)
            .asImageBitmap()
            .also { cachedSprite = it }
    }
}

@Composable
fun rememberTrousseSprite(): ImageBitmap {
    val context = LocalContext.current.applicationContext
    return remember(context) { trousseSprite(context) }
}

/**
 * La trousse telle qu'elle apparaît dans l'interface (aperçu du menu, carte
 * de boutique, profil) : le sprite recoloré par le filtre du skin.
 */
@Composable
fun TrousseSprite(
    skinId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        bitmap = rememberTrousseSprite(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = rememberSkinColorFilter(skinId),
        // Le sprite est affiché bien plus petit que ses 980 px : le
        // filtrage bilinéaire évite l'aspect crénelé, mais il se paie, d'où
        // le repli en qualité basse (voir LocalGraphicsQuality).
        filterQuality = spriteFilterQuality(),
    )
}

@Composable
internal fun spriteFilterQuality(): FilterQuality = LocalGraphicsQuality.current.profile.spriteFilter

/** Le même choix, pour le code de dessin qui a déjà le profil sous la main
 *  (les cinématiques) et ne peut pas lire un CompositionLocal. */
internal val QualityProfile.spriteFilter: FilterQuality
    get() = if (sharpSprites) FilterQuality.Medium else FilterQuality.Low

/**
 * La trousse dessinée sur un Canvas (jeu, cinématiques) — portage de
 * `paintTrousse()` côté web : le sprite filtré pour la plupart des skins,
 * mais un dessin dédié pour la Trousse Pièce (`drawPixelCoin`) et la
 * Trousse de Fer (`drawIronIngot`), qui n'utilisent pas l'image du tout.
 *
 * [rotationRadians] suit la convention du web (`c.rotate(rot)`).
 */
fun DrawScope.drawTrousseSprite(
    image: ImageBitmap,
    skinId: String,
    centerX: Float,
    centerY: Float,
    size: Float,
    rotationRadians: Float,
    colorFilter: ColorFilter?,
    alpha: Float = 1f,
    filterQuality: FilterQuality = FilterQuality.Medium,
) {
    val skin = Skins.find(skinId)
    rotateRad(rotationRadians, pivot = Offset(centerX, centerY)) {
        val left = centerX - size / 2f
        val top = centerY - size / 2f
        when {
            skin.isCoin -> drawPixelCoin(left, top, size, alpha)
            skin.isIron -> drawIronIngot(left, top, size, alpha)
            else -> drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(size.roundToInt(), size.roundToInt()),
                alpha = alpha,
                colorFilter = colorFilter,
                filterQuality = filterQuality,
            )
        }
    }
}

/** `drawPixelCoin()` : une pièce dessinée case par case sur une grille 14×14,
 *  bord plus sombre et croix gravée au centre. */
private fun DrawScope.drawPixelCoin(left: Float, top: Float, size: Float, alpha: Float) {
    val grid = 14
    val cell = size / grid
    val center = (grid - 1) / 2f
    for (gy in 0 until grid) {
        for (gx in 0 until grid) {
            val dx = gx - center
            val dy = gy - center
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > grid / 2f) continue
            val rim = dist > grid / 2f - 1.6f
            val isMark = (abs(dx) <= 0.6f && abs(dy) <= 3.2f) || (abs(dy) <= 0.6f && abs(dx) <= 3.2f)
            val color = when {
                rim -> Color(0xFFB8860A)
                isMark -> Color(0xFFE8B923)
                else -> Color(0xFFFFDD55)
            }
            drawRect(
                color = color,
                topLeft = Offset(left + gx * cell, top + gy * cell),
                size = Size(cell + 0.5f, cell + 0.5f),
                alpha = alpha,
            )
        }
    }
}

/** `drawIronIngot()` : le lingot trapézoïdal de la Trousse de Fer, avec son
 *  dégradé métal, son reflet supérieur et ses rayures de moulage. */
private fun DrawScope.drawIronIngot(left: Float, top: Float, size: Float, alpha: Float) {
    val w = size
    val h = size * 0.62f
    val originY = top + (size - h) / 2f
    val inset = w * 0.12f

    val body = Path().apply {
        moveTo(left + inset, originY)
        lineTo(left + w - inset, originY)
        lineTo(left + w, originY + h)
        lineTo(left, originY + h)
        close()
    }
    drawPath(
        path = body,
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFFDFE4EA),
                0.45f to Color(0xFFAAB2BD),
                1f to Color(0xFF6B7280),
            ),
            startY = originY,
            endY = originY + h,
        ),
        style = Fill,
        alpha = alpha,
    )

    val glint = Path().apply {
        moveTo(left + inset + 2f, originY + 2f)
        lineTo(left + w - inset - 2f, originY + 2f)
        lineTo(left + w * 0.62f, originY + h * 0.32f)
        lineTo(left + w * 0.32f, originY + h * 0.32f)
        close()
    }
    drawPath(path = glint, color = Color.White.copy(alpha = 0.55f), style = Fill, alpha = alpha)

    drawPath(path = body, color = Color(0xFF4B5058), style = Stroke(width = max(1f, size * 0.02f)), alpha = alpha)
    val scratch = Color(0xFF4B5058).copy(alpha = 0.35f)
    val scratchWidth = max(1f, size * 0.012f)
    for (i in 1..3) {
        val y = originY + h * (0.55f + i * 0.1f)
        drawLine(
            color = scratch,
            start = Offset(left + w * 0.08f, y),
            end = Offset(left + w * 0.92f, y),
            strokeWidth = scratchWidth,
            alpha = alpha,
        )
    }
}
