package com.bullythetrousse.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bullythetrousse.core.Beep
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.VilleCredits
import com.bullythetrousse.core.VilleEngine
import com.bullythetrousse.core.VilleEntry
import com.bullythetrousse.core.VilleHost
import com.bullythetrousse.core.VilleMode
import com.bullythetrousse.core.VilleMusic
import com.bullythetrousse.core.VillePopup
import com.bullythetrousse.core.VilleTransKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le monde Ville en mode plateforme (aéroport, rue, réception, mode
 * histoire), portage de l'écran `#ville-screen` de ville.js.
 *
 * Toute la logique vit dans [VilleEngine] (`:core`, testé de bout en bout) ;
 * cet écran fait tourner sa boucle à chaque image, dessine son état dans le
 * repère virtuel (voir VilleRenderer.kt) et lui transmet les appuis.
 */
@Composable
fun VilleScreen(
    entry: VilleEntry,
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    /** La musique du monde se met en pause dans le mode histoire (voir [VilleMusic]). */
    onMusicChange: (VilleMusic) -> Unit,
    onExitToMenu: () -> Unit,
    onGoRooftop: () -> Unit,
    onLeaveVille: (destination: String) -> Unit,
) {
    val sfx = LocalSfx.current
    val tires = rememberOneShotSound(R.raw.sfx_tires)
    val callbacks = rememberUpdatedState(VilleCallbacks(onSaveChange, onExitToMenu, onGoRooftop, onLeaveVille))

    // La sauvegarde la plus récente : le moteur peut la modifier plusieurs
    // fois dans la même image, avant que l'écran ne soit recomposé avec.
    val latest = remember { SaveBox(save) }
    SideEffect {
        if (save !== latest.fromParent) {
            latest.fromParent = save
            latest.value = save
        }
    }

    var shopOpen by remember { mutableStateOf(false) }
    var skinShopClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Tutoriel du monde Ville en cours : ce qu'il faut rappeler à sa fermeture.
    var villeTutorialDone by remember { mutableStateOf<(() -> Unit)?>(null) }

    val host = remember {
        object : VilleHost {
            override val save: GameSave get() = latest.value
            override fun updateSave(transform: (GameSave) -> GameSave) {
                latest.value = transform(latest.value)
                callbacks.value.onSaveChange(latest.value)
            }
            override fun nowMillis(): Long = System.currentTimeMillis()
            override fun sfx(beeps: List<Beep>) = sfx.play(beeps)
            override fun playTires() = tires.play()
            override fun openShop() { shopOpen = true }
            override fun openSkinShop(onClose: () -> Unit) { skinShopClose = onClose }
            override fun startVilleTutorial(onDone: () -> Unit) { villeTutorialDone = onDone }
            override fun goRooftop() = callbacks.value.onGoRooftop()
            override fun exitToMenu() = callbacks.value.onExitToMenu()
            override fun leaveVille(destination: String) {
                updateSave { com.bullythetrousse.core.Ville.leave(it, destination) }
                callbacks.value.onLeaveVille(destination)
            }
        }
    }
    val engine = remember { VilleEngine(host) }
    var ui by remember { mutableStateOf(VilleUi()) }
    var tick by remember { mutableLongStateOf(0L) }
    var blur by remember { mutableFloatStateOf(0f) }

    // La Ville se joue en paysage (voir VilleLandscapeLock) : la partie ne
    // démarre qu'une fois l'écran pivoté, pour ne pas jouer ses premières
    // images en portrait. Un appareil qui refuse de pivoter (grand écran,
    // multifenêtre...) la lance quand même au bout d'un instant.
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withTimeoutOrNull(LANDSCAPE_WAIT_MILLIS) {
            snapshotFlow { canvasSize }.first { it.width > it.height }
        }
        engine.enter(entry)
        started = true
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016 else (now - last) / 1e9
                last = now
                runCatching { engine.frame(dt) }.onFailure {
                    // Une séquence qui plante ne doit pas figer tout l'écran
                    // (le site se contente aussi d'un console.error).
                    engine.stopCoroutine()
                }
                tick++
                ui = VilleUi.of(engine, latest.value.money)
                blur = transitionBlur(engine)
            }
        }
    }
    LaunchedEffect(ui.music) { onMusicChange(ui.music) }
    DisposableEffect(Unit) {
        onDispose {
            engine.flushStats()
            onMusicChange(VilleMusic.WORLD)
        }
    }

    // Musique du boss et pas : en pause dès que l'app quitte le premier plan
    // (la boucle du moteur, elle, s'arrête d'elle-même avec les images).
    val inForeground by rememberAppInForeground()
    BossMusic(
        active = ui.music == VilleMusic.BOSS,
        generation = ui.bossStarts,
        volume = ui.bossVolume,
        muted = save.musicMuted,
        inForeground = inForeground,
    )
    LoopingSound(R.raw.sfx_footsteps, playing = ui.footsteps && !shopOpen && inForeground, volume = 0.55f)

    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val sprite = rememberTrousseSprite()
    val skinFilter = rememberSkinColorFilter(save.equippedSkin)
    val filterQuality = spriteFilterQuality()
    val offscreen = remember { OffscreenFrame() }
    val density = LocalDensity.current.density

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Flou de la transition "percuté" (Android 12+ seulement :
                // en dessous, il reste le fondu au noir).
                .graphicsLayer {
                    renderEffect = if (blur > 0.1f) BlurEffect(blur * density, blur * density) else null
                }
                .onSizeChanged {
                    canvasSize = it
                    engine.resize(it.width.toDouble(), it.height.toDouble())
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        engine.input.any = true
                        engine.input.tap = true
                        if (engine.player.sword) engine.input.attack = true
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tick // redessine à chaque image de la boucle
            if (!started) {
                // Le temps que l'écran pivote : noir, ou le blanc des nuages
                // quand on arrive du crash d'avion (l'arrivée commence en blanc).
                drawRect(if (entry == VilleEntry.ARRIVAL) ARRIVAL_WHITE else Color.Black)
                return@Canvas
            }
            val sprites = VilleSprites(sprite, save.equippedSkin, skinFilter, save.equippedCosmetic, filterQuality)
            val sc = engine.scale.toFloat()
            val shakeX = (Random.nextFloat() - 0.5f) * engine.shake.toFloat() * density
            val shakeY = (Random.nextFloat() - 0.5f) * engine.shake.toFloat() * density
            val probe = VilleScene(engine, Ctx2D(this, textMeasurer), sprites)
            val glitch = max(engine.glitch.toFloat(), probe.transitionGlitch())
            fun DrawScope.scene() {
                withTransform({
                    translate(shakeX, shakeY)
                    scale(sc, sc, Offset.Zero)
                }) {
                    VilleScene(engine, Ctx2D(this, textMeasurer), sprites).renderScene()
                }
            }
            if (glitch > 0.01f) {
                // Le "glitch" découpe l'image déjà dessinée en bandes décalées :
                // il faut donc d'abord la dessiner hors écran.
                val bitmap = offscreen.bitmap(size)
                offscreen.scope.draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bitmap), size) {
                    drawRect(Color.Black)
                    scene()
                }
                drawImage(bitmap)
                drawGlitch(bitmap, glitch, density)
            } else {
                scene()
            }
            withTransform({ scale(sc, sc, Offset.Zero) }) {
                VilleScene(engine, Ctx2D(this, textMeasurer), sprites).drawTransition()
            }
        }

        if (started && ui.mode != VilleMode.CREDITS) {
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                VilleHud(ui.money, ui.eventText, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                if (ui.showBack) {
                    GameButton(
                        "← Menu",
                        secondary = true,
                        small = true,
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        onClick = { engine.onBackButton() },
                    )
                }
                VilleMessage(ui.message, Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp))
                if (ui.mode == VilleMode.PLAY || ui.mode == VilleMode.STAIRS) {
                    VilleTouchControls(
                        showAttack = ui.sword,
                        dashReady = ui.dashReady,
                        onLeft = { engine.input.left = it; if (it) engine.input.any = true },
                        onRight = { engine.input.right = it; if (it) engine.input.any = true },
                        onJump = { pressed ->
                            engine.input.holdJump = pressed
                            if (pressed) { engine.input.jump = true; engine.input.any = true }
                        },
                        onDash = { engine.input.dash = true; engine.input.any = true },
                        onInteract = { engine.input.interact = true; engine.input.tap = true; engine.input.any = true },
                        onAttack = { engine.input.attack = true; engine.input.any = true },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                    )
                }
                ui.choices?.let { options ->
                    VilleChoicesView(options, onPick = { engine.pickChoice(it) }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp))
                }
                ui.popup?.let { popup ->
                    VillePopupView(
                        popup,
                        onButton = { engine.pressPopupButton(it) },
                        onDismiss = { engine.dismissPopup() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        ui.credits?.let { credits ->
            if (ui.mode == VilleMode.CREDITS) VilleCreditsRoll(credits, onFinished = { engine.endCredits() })
        }

        if (shopOpen) {
            ShopScreen(
                save = save,
                onSaveChange = onSaveChange,
                onBackToMenu = {},
                onBackToGame = {},
                villeMode = true,
                onCloseVille = {
                    shopOpen = false
                    engine.resumeFromShop()
                },
            )
        }
        skinShopClose?.let { onClose ->
            TrousserieOverlay(save = save, onSaveChange = onSaveChange, onClose = {
                skinShopClose = null
                onClose()
            })
        }
        // Le tutoriel de la Ville, par-dessus tout : la Ville reste figée
        // jusqu'à sa fermeture (voir VilleEngine.maybeVilleTutorial).
        villeTutorialDone?.let { done ->
            TutorialOverlay(tutorial = Tutorial.VILLE, onDone = {
                villeTutorialDone = null
                host.updateSave { it.copy(villeTutorialSeen = true) }
                done()
            })
        }
    }
}

/**
 * Le monde Ville se joue en paysage, comme le site sur un écran d'ordinateur
 * ou un téléphone tourné ; le reste de l'app reste en portrait (manifeste).
 * Tant qu'il est affiché, l'écran suit le capteur entre les deux sens du
 * paysage, puis on rend l'orientation d'avant. L'activité ne se recrée pas
 * en pivotant (`configChanges` dans le manifeste) : la partie continue.
 */
@Composable
internal fun VilleLandscapeLock() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Au-delà, on démarre même si l'écran n'a pas pivoté. */
private const val LANDSCAPE_WAIT_MILLIS = 1500L

/** Le blanc des nuages (`#eef3f8`), celui de la fin du crash et du début de l'arrivée. */
private val ARRIVAL_WHITE = Color(0xFFEEF3F8)

private class VilleCallbacks(
    val onSaveChange: (GameSave) -> Unit,
    val onExitToMenu: () -> Unit,
    val onGoRooftop: () -> Unit,
    val onLeaveVille: (String) -> Unit,
)

private class SaveBox(var value: GameSave) {
    var fromParent: GameSave = value
}

/**
 * Ce que l'interface affiche par-dessus le canvas, recalculé à chaque image
 * mais comparé par valeur : Compose ne recompose les boutons et les textes
 * que quand l'un d'eux change vraiment.
 */
private data class VilleUi(
    val mode: VilleMode = VilleMode.PLAY,
    val message: String? = null,
    val popup: VillePopup? = null,
    val choices: List<String>? = null,
    val money: Int = 0,
    val eventText: String? = null,
    val showBack: Boolean = false,
    val sword: Boolean = false,
    val dashReady: Boolean = true,
    val footsteps: Boolean = false,
    val music: VilleMusic = VilleMusic.WORLD,
    val bossStarts: Int = 0,
    val bossVolume: Float = 0.5f,
    val credits: VilleCredits? = null,
) {
    companion object {
        fun of(e: VilleEngine, money: Int) = VilleUi(
            mode = e.mode,
            message = e.message,
            popup = e.popup,
            choices = e.choices,
            money = money,
            eventText = e.eventHudText(::hhmm),
            showBack = e.showBackButton && e.mode != VilleMode.CREDITS,
            sword = e.attackAvailable,
            dashReady = e.dashReady >= 1.0,
            footsteps = e.footstepsOn && !e.paused,
            music = e.music,
            bossStarts = e.bossMusicStarts,
            bossVolume = e.bossVolume,
            credits = e.credits,
        )
    }
}

/** Le flou de la transition "blur" en cours (px du repère du site, 0 = aucun). */
private fun transitionBlur(e: VilleEngine): Float {
    val tr = e.transition ?: return 0f
    if (tr.kind != VilleTransKind.BLUR) return 0f
    val p = tr.progress.toFloat()
    return if (p < 0.62f) c01f(p / 0.45f) * 14 else 0f
}

/** L'image hors écran de l'effet "glitch", recréée seulement si la taille change. */
private class OffscreenFrame {
    val scope = CanvasDrawScope()
    private var image: ImageBitmap? = null

    fun bitmap(size: Size): ImageBitmap {
        val w = max(1, size.width.toInt())
        val h = max(1, size.height.toInt())
        val current = image
        if (current != null && current.width == w && current.height == h) return current
        return ImageBitmap(w, h).also { image = it }
    }
}

/**
 * `glitchFX()` : bandes de l'image décalées horizontalement, barres de
 * couleur en mode additif, grain blanc — en pixels réels, comme le site.
 */
private fun DrawScope.drawGlitch(bitmap: ImageBitmap, amount: Float, density: Float) {
    val cw = bitmap.width
    val ch = bitmap.height
    val strips = floor(3 + amount * 16).toInt()
    repeat(strips) {
        val sh = (Random.nextFloat() * ch * 0.07f * amount + 2).toInt().coerceAtLeast(1)
        val sy = (Random.nextFloat() * ch).toInt().coerceIn(0, ch - 1)
        val height = minOf(sh, ch - sy)
        val off = ((Random.nextFloat() - 0.5f) * cw * 0.14f * amount).toInt()
        drawImage(
            bitmap,
            srcOffset = IntOffset(0, sy),
            srcSize = IntSize(cw, height),
            dstOffset = IntOffset(off, sy),
            dstSize = IntSize(cw, height),
        )
    }
    val colors = arrayOf(Color(255, 0, 90, 56), Color(0, 255, 200, 51), Color(90, 90, 255, 61))
    repeat(ceil(amount * 8).toInt()) { j ->
        drawRect(
            colors[j % 3],
            topLeft = Offset(Random.nextFloat() * cw, Random.nextFloat() * ch),
            size = Size(Random.nextFloat() * cw * 0.5f, Random.nextFloat() * ch * 0.025f + 2),
            blendMode = BlendMode.Plus,
        )
    }
    val grain = Color.White.copy(alpha = (0.05f * amount).coerceIn(0f, 1f))
    repeat(ceil(40 * amount).toInt()) {
        drawRect(grain, topLeft = Offset(Random.nextFloat() * cw, Random.nextFloat() * ch), size = Size(3 * density, 3 * density))
    }
}
