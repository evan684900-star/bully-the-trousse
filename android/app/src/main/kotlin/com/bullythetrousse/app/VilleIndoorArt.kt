package com.bullythetrousse.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bullythetrousse.app.Ctx2D.Companion.hex
import com.bullythetrousse.app.Ctx2D.Companion.rgba
import com.bullythetrousse.core.VilleLevels.Companion.ARENA_X
import com.bullythetrousse.core.VilleLevels.Companion.CIRCLE_T
import com.bullythetrousse.core.VilleLevels.Companion.DARKROOM_W
import com.bullythetrousse.core.VilleLevels.Companion.D_DOOR
import com.bullythetrousse.core.VilleLevels.Companion.D_PORTAL
import com.bullythetrousse.core.VilleLevels.Companion.D_PORTAL_ALT
import com.bullythetrousse.core.VilleLevels.Companion.D_RADIUS
import com.bullythetrousse.core.VilleLevels.Companion.D_SWITCH
import com.bullythetrousse.core.VilleLevels.Companion.E1
import com.bullythetrousse.core.VilleLevels.Companion.E2
import com.bullythetrousse.core.VilleLevels.Companion.RETURN_X
import com.bullythetrousse.core.VilleLevels.Companion.R_BOARD
import com.bullythetrousse.core.VilleLevels.Companion.R_DESK
import com.bullythetrousse.core.VilleLevels.Companion.R_ELEVATOR
import com.bullythetrousse.core.VilleLevels.Companion.R_EXIT
import com.bullythetrousse.core.VilleLevels.Companion.R_STAIRS
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Les niveaux intérieurs du monde Ville : la réception de la Tour, la pièce
 * noire du mode histoire et le monde bugé (arène du boss comprise). Portage
 * des fonctions de dessin de ville-levels.js.
 */

private val NOTES = listOf("#fff59d", "#ffffff", "#b3e5fc", "#ffccbc", "#dcedc8")

/* =====================================================================
   RÉCEPTION
   ===================================================================== */
internal fun VilleScene.drawReception() {
    val h = vh
    val w = vw
    c.fill = c.linearGradient(0f, 0f, 0f, gy, 0f to hex("#f6dfa6"), 1f to hex("#e7bf73"))
    c.fillRect(0f, 0f, w, gy)
    // poutres du plafond
    val ceil = max(0f, gy - 440)
    c.fill(hex("#5a3718")); c.fillRect(0f, 0f, w, ceil + 16)
    var bx = floor(cam / 160) * 160
    while (bx < cam + w + 160) { c.fill(hex("#6b4220")); c.fillRect(bx - cam, ceil, 26f, 30f); bx += 160 }
    c.fill(hex("#c9a227")); c.fillRect(0f, ceil + 16, w, 4f)
    // papier peint à motifs
    c.fill(rgba(160, 110, 40, 0.12f))
    var px = floor(cam / 60) * 60
    while (px < cam + w) {
        var py = ceil + 50
        while (py < gy - 150) {
            c.fillCircle(px - cam + ((py / 60) % 2) * 30, py, 5f)
            py += 60
        }
        px += 60
    }
    // lambris
    c.fill(hex("#8b5a2b")); c.fillRect(0f, gy - 130, w, 130f)
    var lx = floor(cam / 120) * 120
    while (lx < cam + w) { c.stroke(hex("#6b4220")); c.lineWidth = 3f; c.strokeRect(lx - cam + 12, gy - 116, 96f, 96f); lx += 120 }
    c.fill(hex("#c9a227")); c.fillRect(0f, gy - 134, w, 5f)
    // sol en parquet
    c.fill = c.linearGradient(0f, gy, 0f, h, 0f to hex("#b67a40"), 1f to hex("#8a5528"))
    c.fillRect(0f, gy, w, h - gy)
    c.stroke(rgba(60, 30, 10, 0.25f)); c.lineWidth = 2f
    for (r in 0 until 6) {
        val y = gy + 10 + r * r * 6 + r * 10
        if (y > h) break
        c.line(0f, y, w, y)
        var x = floor(cam / 140) * 140 + (r % 2) * 70
        while (x < cam + w) { c.line(x - cam, y, x - cam, y + 10 + r * 8); x += 140 }
    }
    // tapis devant la réception
    if (inView(R_DESK.toFloat(), 260f)) {
        c.fill(hex("#a8322a")); c.fillRect(R_DESK.toFloat() - 240 - cam, gy + 14, 480f, min(60f, h - gy - 20))
        c.stroke(hex("#e0a030")); c.lineWidth = 3f; c.strokeRect(R_DESK.toFloat() - 228 - cam, gy + 22, 456f, min(44f, h - gy - 36))
    }
    // porte d'entrée + fenêtre sur la rue (ciel réel)
    if (inView(R_EXIT.toFloat(), 80f)) {
        val x = R_EXIT.toFloat() - cam
        c.fill(hex("#5a3718")); c.fillRect(x - 56, gy - 230, 112f, 230f)
        c.fill(sky.top); c.fillRect(x - 44, gy - 218, 88f, 120f)
        c.fill(hex("#6b4220")); c.fillRect(x - 44, gy - 98, 88f, 98f)
        c.fill(hex("#ffd23f")); c.fillCircle(x + 32, gy - 70, 5f)
        c.fill(hex("#1b5e20")); c.fillRoundRect(x - 36, gy - 262, 72f, 22f, 4f)
        c.fill(Color.White); c.font = Ctx2D.Font(12f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("SORTIE", x, gy - 246); c.textAlign = Ctx2D.Align.LEFT
    }
    if (inView(270f, 100f)) {
        val x = 270 - cam
        c.saved {
            c.clipRect(x - 70, gy - 330, 140f, 170f)
            c.drawVilleSky(w, h, gy - 160, sky, cam)
            c.drawSkyline(w, gy - 160, cam, 0.3f, 17, SkylineStyle(hazeColor(sky, 0.5f), if (outage) 0f else sky.night, 40f, 150f, bw = 50f))
        }
        c.stroke(hex("#5a3718")); c.lineWidth = 10f; c.strokeRect(x - 70, gy - 330, 140f, 170f)
        c.lineWidth = 4f
        c.line(x, gy - 330, x, gy - 160)
        c.line(x - 70, gy - 245, x + 70, gy - 245)
        c.fill(hex("#a8322a")); c.fillRect(x - 86, gy - 344, 20f, 200f); c.fillRect(x + 66, gy - 344, 20f, 200f)
    }
    // escaliers
    if (inView(R_STAIRS.toFloat(), 80f)) {
        val x = R_STAIRS.toFloat() - cam
        c.fill(hex("#6b4220")); c.fillRect(x - 50, gy - 210, 100f, 210f)
        c.fill(hex("#3a2410")); c.fillRect(x - 40, gy - 200, 80f, 200f)
        c.fill(hex("#8b5a2b"))
        for (k in 0 until 6) c.fillRect(x - 40 + k * 13, gy - 30 - k * 28, 80f - k * 13, 8f)
        c.fill(hex("#2e3b4e")); c.fillRoundRect(x - 44, gy - 244, 88f, 24f, 4f)
        c.fill(Color.White); c.font = Ctx2D.Font(12f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("ESCALIERS", x, gy - 228); c.textAlign = Ctx2D.Align.LEFT
    }
    // ascenseur
    if (inView(R_ELEVATOR.toFloat(), 100f)) {
        val x = R_ELEVATOR.toFloat() - cam
        val elev = e.levels.reception.elevator.toFloat()
        c.fill(hex("#c9a227")); c.fillRect(x - 70, gy - 240, 140f, 240f)
        c.fill(hex("#2b2f36")); c.fillRect(x - 58, gy - 228, 116f, 228f)
        c.fill(rgba(255, 230, 170, 0.8f)); c.fillRect(x - 54, gy - 224, 108f, 224f)
        val o = elev * 54
        c.fill = c.linearGradient(x - 58, 0f, x + 58, 0f, 0f to hex("#8e959e"), 0.5f to hex("#d9dde2"), 1f to hex("#8e959e"))
        c.fillRect(x - 58 - o, gy - 228, 58f, 228f); c.fillRect(x + o, gy - 228, 58f, 228f)
        c.fill(rgba(0, 0, 0, 0.3f)); c.fillRect(x - 1, gy - 228, 2f, 228 * (1 - elev))
        c.fill(hex("#1a1c20")); c.fillRoundRect(x - 30, gy - 274, 60f, 26f, 5f)
        c.fill(if (outage) hex("#552222") else hex("#ff9a3c")); c.font = Ctx2D.Font(14f, FontWeight.Black, FontFamily.Monospace)
        c.textAlign = Ctx2D.Align.CENTER
        c.fillText(if (outage) "--" else if (elev > 0) "0" else "▲ 1", x, gy - 256)
        c.fill(hex("#1a1c20")); c.fillRoundRect(x + 80, gy - 130, 20f, 40f, 4f)
        c.fill(if (outage) hex("#333333") else hex("#ffd23f")); c.fillCircle(x + 90, gy - 118, 5f); c.fillCircle(x + 90, gy - 102, 5f)
        c.textAlign = Ctx2D.Align.LEFT
    }
    // tableau des annonces
    if (inView(R_BOARD.toFloat(), 90f)) {
        val x = R_BOARD.toFloat() - cam
        c.fill(hex("#5a3718")); c.fillRect(x - 86, gy - 300, 172f, 130f)
        c.fill(hex("#c79a5b")); c.fillRect(x - 78, gy - 292, 156f, 114f)
        NOTES.forEachIndexed { i, col ->
            c.saved {
                c.translate(x - 56 + (i % 3) * 52, gy - 262 + (i / 3) * 50)
                c.rotate((sr(i * 3.0) - 0.5f) * 0.3f)
                c.fill(hex(col)); c.fillRect(-20f, -18f, 40f, 36f)
                c.fill(rgba(0, 0, 0, 0.35f)); for (k in 0 until 3) c.fillRect(-14f, -8f + k * 8, 28f, 2f)
                c.fill(hex("#c62828")); c.fillCircle(0f, -16f, 3f)
            }
        }
        c.fill(hex("#3a2410")); c.font = Ctx2D.Font(12f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("ANNONCES", x, gy - 306); c.textAlign = Ctx2D.Align.LEFT
        if (e.activeEvent() != null) { c.fill(hex("#ff3b3b")); c.fillCircle(x + 80, gy - 296, 8f) }
    }
    // horloge à l'heure réelle
    if (inView(1110f, 50f)) {
        val x = 1110 - cam
        val y = gy - 290
        val d = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        c.fill(hex("#5a3718")); c.fillCircle(x, y, 34f)
        c.fill(hex("#fff8e6")); c.fillCircle(x, y, 28f)
        c.saved {
            c.stroke(hex("#3a2410")); c.lineCap = StrokeCap.Round
            val hA = ((d.hour % 12) + d.minute / 60f) / 12 * 2 * PI.toFloat() - PI.toFloat() / 2
            val mA = (d.minute + d.second / 60f) / 60 * 2 * PI.toFloat() - PI.toFloat() / 2
            c.lineWidth = 4f; c.line(x, y, x + cos(hA) * 15, y + sin(hA) * 15)
            c.lineWidth = 2.5f; c.line(x, y, x + cos(mA) * 22, y + sin(mA) * 22)
        }
    }
    // réception : crabe réceptionniste derrière le comptoir
    if (inView(R_DESK.toFloat(), 200f)) {
        val x = R_DESK.toFloat() - cam
        c.fill(hex("#c9a227")); c.font = Ctx2D.Font(22f, FontWeight.Black, FontFamily.Serif); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("RÉCEPTION", x, gy - 250)
        c.fill(hex("#5a3718")); c.fillRect(x - 90, gy - 236, 180f, 70f)
        for (k in 0 until 8) { c.fill(hex("#c9a227")); c.fillRect(x - 80 + k * 21, gy - 222, 4f, 10f); c.fillRect(x - 81 + k * 21, gy - 212, 6f, 12f) }
        val cy = gy - 128
        c.drawCrab(x + 10, cy, 60f, t, -1)
        c.drawCrabOutfit(x + 10, cy, 60f, -1, "bowtie")
        c.fill = c.linearGradient(0f, gy - 110, 0f, gy, 0f to hex("#9b6534"), 1f to hex("#6b4220"))
        c.fillRect(x - 150, gy - 110, 300f, 110f)
        c.fill(hex("#e9e4da")); c.fillRect(x - 158, gy - 118, 316f, 12f)
        c.stroke(hex("#c9a227")); c.lineWidth = 3f; c.strokeRect(x - 136, gy - 96, 272f, 80f)
        c.fill(hex("#c9a227")); c.beginPath(); c.arc(x - 90, gy - 124, 9f, PI.toFloat(), 0f); c.fill(); c.fillRect(x - 100, gy - 125, 20f, 3f)
        c.fill(hex("#2b2f36")); c.fillRect(x + 60, gy - 160, 54f, 38f); c.fillRect(x + 83, gy - 122, 8f, 6f)
        c.fill(hex("#4fc3f7")); c.fillRect(x + 64, gy - 156, 46f, 30f)
        c.textAlign = Ctx2D.Align.LEFT
    }
    // coin salon
    if (inView(1680f, 140f)) {
        val x = 1680 - cam
        c.fill(hex("#7b3f1d")); c.fillRoundRect(x - 90, gy - 80, 180f, 60f, 14f)
        c.fill(hex("#8d4a24")); c.fillRoundRect(x - 100, gy - 112, 200f, 44f, 14f)
        c.fill(hex("#5a2d12")); c.fillRect(x - 84, gy - 22, 10f, 22f); c.fillRect(x + 74, gy - 22, 10f, 22f)
        drawPlant(x + 130, 1.1f)
    }
    // suspensions (lumière chaude)
    if (!outage) {
        for (lampX in floatArrayOf(350f, 900f, 1330f, 1700f)) {
            if (!inView(lampX, 200f)) continue
            val x = lampX - cam
            val y = ceil + 110
            c.stroke(hex("#3a2410")); c.lineWidth = 2f; c.line(x, ceil + 20, x, y - 16)
            c.fill(hex("#c9a227"))
            c.beginPath(); c.moveTo(x - 26, y); c.lineTo(x - 12, y - 18); c.lineTo(x + 12, y - 18); c.lineTo(x + 26, y); c.fill()
            c.fill = c.radialGradient(x, y + 4, 2f, 230f, 0f to rgba(255, 220, 140, 0.45f), 1f to rgba(255, 220, 140, 0f))
            c.fillRect(x - 230, y - 20, 460f, 460f)
        }
    }
}

internal fun VilleScene.drawReceptionFront() {
    val h = vh
    for (x in floatArrayOf(820f, 1580f)) {
        if (!inView(x, 40f)) continue
        c.fill = c.linearGradient(x - 28 - cam, 0f, x + 28 - cam, 0f, 0f to hex("#6b4220"), 0.5f to hex("#a0703f"), 1f to hex("#5a3718"))
        c.fillRect(x - 28 - cam, 0f, 56f, h)
        c.fill(hex("#c9a227")); c.fillRect(x - 32 - cam, gy - 10, 64f, 10f); c.fillRect(x - 32 - cam, 40f, 64f, 8f)
    }
    if (inView(560f, 40f)) c.saved { c.translate(0f, 40f); drawPlant(560 - cam, 1.3f) }
    if (abs(e.player.x - R_DESK) < 150 && !e.host.save.villeReceptionDone) {
        bubble(R_DESK.toFloat() - cam + 10, gy - 150, "Bienvenue à La Tour ! Approchez, approchez.")
    }
}

internal fun VilleScene.drawReceptionOverlay() {
    if (!outage) return
    val (px, py) = e.playerScreen()
    val blink = floor(e.time * 2).toInt() % 2 != 0
    darkness(
        0.82f,
        listOf(
            VilleScene.Hole(px.toFloat(), py.toFloat(), 170f, 0.95f),
            VilleScene.Hole(R_EXIT.toFloat() - cam, gy - 250, 70f, 0.8f),
            VilleScene.Hole(R_STAIRS.toFloat() - cam, gy - 240, 80f, 0.9f),
            VilleScene.Hole(R_ELEVATOR.toFloat() - cam, gy - 250, if (blink) 40f else 20f, 0.6f),
        ),
    )
}

/* =====================================================================
   PIÈCE NOIRE
   ===================================================================== */
internal fun VilleScene.drawDarkroom() {
    val st = e.story ?: return
    val w = vw
    val h = vh
    c.fill(hex("#6f737b")); c.fillRect(0f, 0f, w, gy)
    c.fill(rgba(0, 0, 0, 0.1f))
    for (i in 0 until 12) {
        c.fillEllipse(sr(i.toDouble()) * DARKROOM_W.toFloat() - cam, gy * (0.2f + sr(i * 3.0) * 0.6f), 40 + sr(i * 5.0) * 60, 20 + sr(i * 7.0) * 30)
    }
    c.fill(hex("#4b4e55")); c.fillRect(0f, gy, w, h - gy)
    c.fill(hex("#3a3c42")); c.fillRect(0f, gy - 4, w, 6f)
    val ceil = max(0f, gy - 450)
    c.fill(hex("#55585f")); c.fillRect(0f, 0f, w, ceil + 10)
    // porte métallique
    val dx = D_DOOR.toFloat() - cam
    c.fill(hex("#3d4148")); c.fillRect(dx - 50, gy - 220, 100f, 220f)
    c.fill(hex("#565b63")); c.fillRect(dx - 42, gy - 212, 84f, 212f)
    c.fill(hex("#b0b5bd")); c.fillRect(dx + 26, gy - 110, 10f, 18f)
    // interrupteur (petite LED qui aide à le trouver)
    val sx = D_SWITCH.toFloat() - cam
    c.fill(hex("#d9d6cc")); c.fillRoundRect(sx - 12, gy - 150, 24f, 36f, 4f)
    c.fill(if (st.light) hex("#888888") else hex("#555555")); c.fillRect(sx - 4, gy - 144 + (if (st.light) 0 else 12), 8f, 12f)
    c.fill(if (st.light) hex("#3ddc84") else hex("#ff5a3c")); c.fillCircle(sx, gy - 120, 2.5f)
    // lampe unique au plafond
    val lampX = DARKROOM_W.toFloat() / 2 - cam
    c.stroke(hex("#222222")); c.lineWidth = 2f; c.line(lampX, ceil, lampX, ceil + 60)
    c.fill(if (st.light) hex("#fff3b0") else hex("#555555")); c.fillCircle(lampX, ceil + 70, 11f)
    if (st.light) {
        c.saved {
            c.blend = BlendMode.Plus
            c.fill = c.radialGradient(
                lampX, ceil + 70, 6f, gy - ceil + 80,
                0f to rgba(255, 225, 120, 0.45f), 0.5f to rgba(255, 210, 100, 0.14f), 1f to rgba(255, 200, 90, 0f),
            )
            c.beginPath(); c.moveTo(lampX - 12, ceil + 70); c.lineTo(lampX - 520, h); c.lineTo(lampX + 520, h); c.lineTo(lampX + 12, ceil + 70); c.fill()
        }
    }
    c.drawPortal(D_PORTAL.toFloat() - cam, gy - D_PORTAL_ALT.toFloat(), D_RADIUS.toFloat(), t, st.escaped, if (st.light) 1f else 0.08f)
}

internal fun VilleScene.drawDarkroomOverlay() {
    val st = e.story ?: return
    if (st.light) return
    val (px, py) = e.playerScreen()
    darkness(
        0.97f,
        listOf(
            VilleScene.Hole(px.toFloat(), py.toFloat(), 95f, 0.85f),
            VilleScene.Hole(D_SWITCH.toFloat() - cam, gy - 120, 22f, 0.9f),
            VilleScene.Hole(D_PORTAL.toFloat() - cam, gy - D_PORTAL_ALT.toFloat(), 60f, 0.12f),
        ),
    )
}

/* =====================================================================
   MONDE BUGÉ + BOSS
   ===================================================================== */
private val GPROPS = listOf("desk", "chair", "cabinet", "monitor", "lamp", "door", "chair", "desk")

private fun VilleScene.drawOfficeProp(kind: String, x: Float, y: Float, s: Float) {
    c.saved {
        c.translate(x, y)
        c.scale(s, s)
        when (kind) {
            "desk" -> {
                c.fill(hex("#7b5a3a")); c.fillRect(-60f, -74f, 120f, 10f)
                c.fill(hex("#5e4329")); c.fillRect(-56f, -64f, 10f, 64f); c.fillRect(46f, -64f, 10f, 64f); c.fillRect(10f, -64f, 40f, 44f)
                c.fill(hex("#c9a227")); c.fillRect(26f, -46f, 8f, 3f)
            }
            "chair" -> {
                c.fill(hex("#2f3542")); c.fillRect(-22f, -44f, 44f, 8f); c.fillRect(14f, -96f, 8f, 56f)
                c.fill(hex("#57606f")); c.fillRect(-2f, -36f, 4f, 26f); c.fillRect(-20f, -10f, 40f, 4f)
            }
            "cabinet" -> {
                c.fill(hex("#8a8f99")); c.fillRect(-24f, -110f, 48f, 110f)
                c.fill(hex("#6f747d")); for (k in 0 until 3) c.fillRect(-20f, -104f + k * 36, 40f, 30f)
                c.fill(hex("#cccccc")); for (k in 0 until 3) c.fillRect(-8f, -92f + k * 36, 16f, 4f)
            }
            "monitor" -> {
                c.fill(hex("#1e2229")); c.fillRect(-30f, -58f, 60f, 40f)
                c.fill(if (floor(t * 3 + x).toInt() % 5 != 0) hex("#39d1c3") else hex("#ff4d8d")); c.fillRect(-26f, -54f, 52f, 32f)
                c.fill(hex("#1e2229")); c.fillRect(-4f, -18f, 8f, 14f); c.fillRect(-16f, -4f, 32f, 4f)
            }
            "lamp" -> {
                c.fill(hex("#3a3f48")); c.fillRect(-3f, -140f, 6f, 140f); c.fillRect(-14f, -4f, 28f, 4f)
                c.fill(hex("#e0c060")); c.beginPath(); c.moveTo(-18f, -128f); c.lineTo(-8f, -150f); c.lineTo(8f, -150f); c.lineTo(18f, -128f); c.fill()
            }
            "door" -> {
                c.fill(hex("#6b4220")); c.fillRect(-36f, -170f, 72f, 170f)
                c.fill(hex("#4e2f16")); c.fillRect(-28f, -162f, 56f, 162f)
                c.fill(hex("#c9a227")); c.fillCircle(18f, -80f, 4f)
            }
        }
    }
}

/** Un building couché qui flotte au loin. */
private fun VilleScene.hBuilding(x: Float, y: Float, w: Float, h: Float, rot: Float, color: Color, windows: Color) {
    c.saved {
        c.translate(x, y)
        c.rotate(rot)
        c.fill(color); c.fillRect(-w / 2, -h / 2, w, h)
        c.fill(windows)
        var i = -w / 2 + 10
        while (i < w / 2 - 14) {
            var j = -h / 2 + 10
            while (j < h / 2 - 12) { c.fillRect(i, j, 10f, 8f); j += 18 }
            i += 22
        }
    }
}

internal fun VilleScene.drawGlitchWorld() {
    val st = e.story ?: return
    val w = vw
    val h = vh
    c.fill = c.linearGradient(0f, 0f, 0f, h, 0f to hex("#140b26"), 0.6f to hex("#1d2a3c"), 1f to hex("#0d1119"))
    c.fillRect(0f, 0f, w, h)
    // buildings couchés qui flottent au loin
    for (i in -2 until 14) {
        val par = 0.15f + (i % 3) * 0.12f // % du JS : négatif pour i < 0, comme le site
        val span = 520f
        val x = ((i * span - cam * par) % (span * 12) + span * 12) % (span * 12) - span
        if (x < -300 || x > w + 300) continue
        val y = gy * (0.2f + sr(i * 3.3) * 0.55f)
        val rot = (sr(i * 5.0) - 0.5f) * 0.5f + if (i % 4 == 0) PI.toFloat() / 2 else 0f
        hBuilding(x, y, 220 + sr(i.toDouble()) * 160, 60 + sr(i * 2.0) * 50, rot, rgba(70, 60, 110, 0.35f + par), rgba(120, 255, 230, 0.18f))
    }
    // plafond : un autre building à l'horizontale
    val ceil = gy - 430
    if (ceil > -30) {
        c.fill(hex("#2c2644")); c.fillRect(0f, -10f, w, ceil + 10)
        c.fill(hex("#554a7a")); c.fillRect(0f, ceil - 8, w, 8f)
        c.fill(hex("#1c1830"))
        var x = floor(cam / 36) * 36
        while (x < cam + w) {
            var y = ceil - 26
            while (y > -10) { c.fillRect(x - cam + 8, y, 14f, 10f); y -= 24 }
            x += 36
        }
    }
    // accessoires à moitié enfouis dans le sol ou le plafond
    for (i in 2 until 18) {
        val px = i * 310 + sr(i * 1.7) * 90
        if (!inView(px, 120f)) continue
        if (abs(px - ARENA_X) < 620 || abs(px - E1) < 140 || abs(px - E2) < 140) continue
        val kind = GPROPS[(sr(i * 4.9) * GPROPS.size).toInt().coerceIn(0, GPROPS.size - 1)]
        val inCeil = sr(i * 8.3) < 0.35f && ceil > -30
        val sink = 0.3f + sr(i * 2.9) * 0.4f
        c.saved {
            if (inCeil) c.clipRect(0f, ceil, w, h) else c.clipRect(0f, 0f, w, gy)
            if (inCeil) {
                c.translate(px - cam, ceil)
                c.rotate(PI.toFloat() + (sr(i.toDouble()) - 0.5f) * 0.4f)
                drawOfficeProp(kind, 0f, 60 * sink, 1f)
            } else {
                c.translate(px - cam, gy)
                c.rotate((sr(i * 6.0) - 0.5f) * 0.6f)
                drawOfficeProp(kind, 0f, 70 * sink, 1f)
            }
        }
    }
    // portail de retour (tout à gauche)
    if (inView(RETURN_X.toFloat(), 100f)) c.drawPortal(RETURN_X.toFloat() - cam, gy - 90, 62f, t, false, if (st.escape != null) 1f else 0.8f)
    // portail d'arrière-plan qui aspire la trousse adverse
    st.backgroundPortal?.let { bp ->
        if (bp.radius > 2) c.drawPortal(bp.x.toFloat() - cam, gy - bp.alt.toFloat(), bp.radius.toFloat(), t * 2, false, 1f)
    }
    // arène : un building de chaque côté + 4 plateformes en losange
    if (st.buildRise > 0 && st.stage < 5) {
        val aw = st.arenaHalfWidth.toFloat()
        for (sd in intArrayOf(-1, 1)) {
            val bx = ARENA_X.toFloat() + sd * aw - cam
            val hh = (gy + 20) * st.buildRise.toFloat()
            c.fill(hex("#3a3358")); c.fillRect(bx - 40, gy - hh, 80f, hh)
            c.fill(rgba(255, 80, 110, 0.5f))
            var y = gy - 20
            while (y > gy - hh + 10) { c.fillRect(bx - 28, y - 12, 16f, 12f); c.fillRect(bx + 12, y - 12, 16f, 12f); y -= 28 }
        }
        for (pl in e.platforms()) {
            val x = pl.x.toFloat() - cam
            val y = gy - pl.alt.toFloat()
            val pw = pl.width.toFloat()
            c.fill(hex("#5a5f6b")); c.fillRect(x - pw / 2, y, pw, 16f)
            c.fill(hex("#8d93a0")); c.fillRect(x - pw / 2, y, pw, 4f)
            c.fill(rgba(120, 255, 230, 0.35f))
            var k = x - pw / 2 + 10
            while (k < x + pw / 2 - 12) { c.fillRect(k, y + 7, 12f, 5f); k += 24 }
        }
    }
    // sol : la façade d'un building couché
    c.fill(hex("#3e3658")); c.fillRect(0f, gy, w, h - gy)
    c.fill(hex("#6c608f")); c.fillRect(0f, gy - 3, w, 6f)
    var x = floor(cam / 40) * 40
    while (x < cam + w) {
        var r = 0
        while (r * 26 + 18 < h - gy) {
            c.fill(if (sr(x * 0.3 + r * 7) < 0.08f) rgba(90, 255, 220, 0.5f) else hex("#231e36"))
            c.fillRect(x - cam + 10, gy + 18 + r * 26, 18f, 12f)
            r++
        }
        x += 40
    }
    // épée abandonnée
    st.droppedSwordX?.let { swordX ->
        c.saved {
            c.translate(swordX.toFloat() - cam, gy - 8)
            c.rotate(PI.toFloat() / 2 + 0.2f)
            drawSword(0f, 0f, 1, 0f, 0.9f)
        }
    }
}

internal fun VilleScene.drawFightMid() {
    val f = e.story?.fight ?: return
    for (ci in f.circles) {
        val x = ci.x.toFloat() - cam
        val y = gy - ci.alt.toFloat()
        val r = ci.radius.toFloat()
        val p = c01f((ci.t / CIRCLE_T).toFloat())
        if (ci.boom == 0.0) {
            c.fill(rgba(255, 30, 40, 0.14f + 0.26f * p)); c.fillCircle(x, y, r)
            c.stroke(rgba(255, 60, 60, 0.95f)); c.lineWidth = 4f; c.strokeCircle(x, y, r)
            c.stroke(rgba(255, 255, 255, 0.8f)); c.lineWidth = 2f; c.strokeCircle(x, y, r * (1 - p))
        } else {
            val q = (ci.boom / 0.3).toFloat()
            c.fill(rgba(255, 220, 200, (1 - q) * 0.8f)); c.fillCircle(x, y, r * (1 + q * 0.2f))
        }
    }
}

/** Le vide qui dévore le monde bugé pendant la fuite. */
internal fun VilleScene.drawGlitchFront() {
    val esc = e.story?.escape ?: return
    val x = esc.voidX.toFloat() - cam
    if (x >= vw + 40) return
    c.fill(Color.Black)
    c.fillRect(x, 0f, vw - x + 40, vh)
    val palette = arrayOf(hex("#ff2d6f"), hex("#2dffd8"), Color.Black, hex("#6c4dff"))
    var y = 0f
    while (y < vh) {
        val j = Random.nextFloat() * 40
        c.fill(palette[Random.nextInt(4)])
        c.fillRect(x - j, y, j + 2, 12f)
        y += 14
    }
}
