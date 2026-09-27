package com.bullythetrousse.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bullythetrousse.app.Ctx2D.Companion.hex
import com.bullythetrousse.app.Ctx2D.Companion.rgba
import com.bullythetrousse.core.CityCar
import com.bullythetrousse.core.PedestrianLight
import com.bullythetrousse.core.CrosswalkLight
import com.bullythetrousse.core.VilleLevels.Companion.AIRPORT_END
import com.bullythetrousse.core.VilleLevels.Companion.CITY_W
import com.bullythetrousse.core.VilleLevels.Companion.CROSS_A
import com.bullythetrousse.core.VilleLevels.Companion.CROSS_B
import com.bullythetrousse.core.VilleLevels.Companion.SECURITY_X
import com.bullythetrousse.core.VilleLevels.Companion.SKINSHOP_X
import com.bullythetrousse.core.VilleLevels.Companion.TOWER_A
import com.bullythetrousse.core.VilleLevels.Companion.TOWER_DOOR
import com.bullythetrousse.core.VilleLevels.Companion.lightState
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Le niveau "city" : l'aéroport puis la rue jusqu'à la Tour, portage des
 * fonctions de dessin de ville-levels.js (drawCityBack/drawCityFront/
 * drawAirport/drawCars et leurs petits objets).
 */

private val FACADES = listOf("#c9a27e", "#b5846b", "#d8c3a5", "#9fa8b3", "#c7b299", "#8c9aa6", "#a8927a")
private val SHOPS = listOf(
    "CAFÉ" to "#6d4c41", "BOULANGERIE" to "#c77d2e", "PHARMACIE ✚" to "#2e8b57", "FLEURISTE" to "#c2477a",
    "LIBRAIRIE" to "#3b5998", "PRESSING" to "#607d8b", "KEBAB" to "#b03a2e",
)
private val BAG_COLORS = listOf("#c0392b", "#2980b9", "#8e44ad", "#16a085", "#f39c12")
private val SHOP_WINDOW_SKINS = listOf("classique", "doree", "glacee", "feu")

/** "HH:MM" à l'heure de l'appareil (`fmtTime()`). */
internal fun hhmm(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

/* ---- Rue perpendiculaire et voitures ---- */

private class RoadGeom(val cx: Float, val hz: Float, val near: Float)

private fun VilleScene.roadGeom() = RoadGeom(cx = (CROSS_A + CROSS_B).toFloat() / 2 - cam, hz = gy - 130, near = vh + 90)

private fun VilleScene.carPos(car: CityCar): Triple<Float, Float, Float> {
    val g = roadGeom()
    val u = c01f(car.u.toFloat())
    val y = lerpf(g.hz, g.near, u.pow(1.6f))
    val s = lerpf(0.12f, 1.55f, u)
    val x = if (car.killer) (car.x.toFloat() - cam) else g.cx + car.lane * lerpf(8f, 95f, u)
    return Triple(x, y, s)
}

internal fun VilleScene.drawCars(front: Boolean) {
    val state = e.levels.city.state
    val list = (state.killer?.let { state.cars + it } ?: state.cars).sortedBy { it.u }
    for (car in list) {
        val (x, y, s) = carPos(car)
        if ((y > gy + 40) != front) continue
        c.drawCarFacing(x, y, s, car.color, car.lane < 0, sky.night > 0.35f)
    }
    // Feu vert piéton : une voiture attend au loin.
    if (!front && lightState(e.time).pedestrian != PedestrianLight.RED) {
        val (x, y, s) = carPos(CityCar(lane = -1, u = 0.3, speed = 0.0, color = 0x2F6FB5))
        c.drawCarFacing(x, y, s, 0x2F6FB5, true, sky.night > 0.35f)
    }
}

/* ---- Façades, lampadaires, mobilier ---- */

private fun VilleScene.drawFacade(x0: Float, x1: Float, seedBase: Int) {
    val lit = if (outage) 0f else sky.night
    var bx = x0
    while (bx < x1) {
        val i = floor(bx / 280).toInt() + seedBase
        val w = min(280f, x1 - bx)
        if (!inView(bx + w / 2, w)) { bx += 280; continue }
        val sx = bx - cam
        val hgt = 330 + sr(i * 2.1) * 170
        c.fill(hex(FACADES[(sr(i * 4.3) * FACADES.size).toInt().coerceIn(0, FACADES.size - 1)]))
        c.fillRect(sx, gy - hgt, w + 1, hgt)
        c.fill(rgba(0, 0, 0, 0.12f))
        c.fillRect(sx, gy - hgt, w, 10f)
        c.fillRect(sx + w - 3, gy - hgt, 3f, hgt)
        // fenêtres
        for (r in 0 until 6) {
            val wy = gy - hgt + 30 + r * 52
            if (wy > gy - 140) break
            for (k in 0 until 4) {
                val wx = sx + 22 + k * 64
                if (wx + 34 > sx + w) break
                val on = sr(i * 17 + r * 5.1 + k * 2.3) < lit * 0.6f
                c.fill(if (on) rgba(255, 212, 130, 0.95f) else if (sky.night > 0.5f) hex("#2a3144") else rgba(160, 200, 230, 0.8f))
                c.fillRect(wx, wy, 34f, 38f)
                c.fill(rgba(0, 0, 0, 0.25f))
                c.fillRect(wx - 3, wy + 38, 40f, 4f)
                c.fillRect(wx + 16, wy, 2f, 38f)
            }
        }
        // boutique au rez-de-chaussée
        val shop = SHOPS[(sr(i * 6.7) * SHOPS.size).toInt().coerceIn(0, SHOPS.size - 1)]
        c.fill(hex(shop.second))
        c.fillRect(sx + 10, gy - 132, w - 20, 26f)
        c.fill(Color.White); c.font = Ctx2D.Font(15f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText(shop.first, sx + w / 2, gy - 114)
        for (k in 0 until ((w - 20) / 20).toInt()) {
            c.fill(if (k % 2 != 0) hex("#f4f1ea") else hex(shop.second))
            c.beginPath()
            c.moveTo(sx + 10 + k * 20, gy - 106); c.lineTo(sx + 30 + k * 20, gy - 106)
            c.lineTo(sx + 25 + k * 20, gy - 92); c.lineTo(sx + 15 + k * 20, gy - 92)
            c.fill()
        }
        c.fill(if (lit > 0.3f) rgba(255, 220, 150, 0.9f) else rgba(170, 205, 230, 0.85f))
        c.fillRect(sx + 20, gy - 86, w - 110, 70f)
        c.fill(hex("#3b2f2a")); c.fillRect(sx + w - 78, gy - 90, 44f, 90f)
        c.fill(hex("#ffd23f")); c.fillRect(sx + w - 42, gy - 46, 4f, 4f)
        c.textAlign = Ctx2D.Align.LEFT
        bx += 280
    }
}

private fun VilleScene.drawLamp(x: Float, big: Boolean) {
    val k = if (big) 1.35f else 1f
    c.fill(if (big) hex("#1d2027") else hex("#3a3f48"))
    c.fillRect(x - 4 * k, gy - 250 * k, 8 * k, 250 * k)
    c.fillRect(x - 4 * k, gy - 250 * k, 40 * k, 6 * k)
    c.fillRect(x + 28 * k, gy - 250 * k, 16 * k, 14 * k)
    if (sky.night > 0.3f && !outage) {
        c.fill = c.radialGradient(x + 36 * k, gy - 234 * k, 2f, 150 * k, 0f to rgba(255, 225, 150, 0.55f), 1f to rgba(255, 225, 150, 0f))
        c.fillRect(x - 120 * k, gy - 390 * k, 310 * k, 400 * k)
        c.fill(hex("#fff3c4")); c.fillRect(x + 30 * k, gy - 238 * k, 12 * k, 5 * k)
    }
}

private fun VilleScene.drawCrabGuard(x: Float, kind: String, dir: Int, size: Float) {
    val y = gy - size * 0.3f
    c.drawCrab(x - cam, y, size, t + x * 0.01f, dir)
    c.drawCrabOutfit(x - cam, y, size, dir, kind)
}

private fun VilleScene.drawCrabGuardAt(sx: Float, kind: String, dir: Int, size: Float) {
    val y = gy - 96
    c.drawCrab(sx, y, size, t, dir)
    c.drawCrabOutfit(sx, y, size, dir, kind)
}

internal fun VilleScene.drawCityBack() {
    val w = vw
    val h = vh
    val lit = if (outage) 0f else sky.night
    c.drawVilleSky(w, h, gy - 60, sky, cam)
    c.drawSkyline(w, gy - 170, cam, 0.12f, 5, SkylineStyle(hazeColor(sky, 0.7f), lit * 0.8f, 80f, 260f, bw = 90f))
    c.drawSkyline(
        w, gy - 90, cam, 0.35f, 9,
        SkylineStyle(hazeColor(sky, 0.5f), lit, 120f, 360f, bw = 140f, dayWindows = if (sky.night < 0.5f) rgba(255, 255, 255, 0.14f) else null),
    )
    // rue perpendiculaire qui file vers l'horizon (d'où viennent les voitures)
    val g = roadGeom()
    c.fill(hex("#3a3d43"))
    c.beginPath()
    c.moveTo(CROSS_A.toFloat() - cam, gy); c.lineTo(g.cx - 16, g.hz); c.lineTo(g.cx + 16, g.hz); c.lineTo(CROSS_B.toFloat() - cam, gy)
    c.closePath(); c.fill()
    c.saved {
        c.stroke(rgba(255, 255, 255, 0.6f)); c.lineWidth = 2f
        c.dash = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
        c.line(g.cx, g.hz, g.cx, gy)
    }
    // façades
    drawFacade(AIRPORT_END.toFloat(), CROSS_A.toFloat() - 60, 100)
    drawFacade(CROSS_B.toFloat() + 60, TOWER_A.toFloat(), 200)
    drawSkinShop()
    drawTower()
    var lx = AIRPORT_END.toFloat() + 250
    while (lx < TOWER_A) {
        if (!(lx > CROSS_A - 120 && lx < CROSS_B + 120) && inView(lx, 60f)) drawLamp(lx - cam, false)
        lx += 440
    }
    // trottoir
    c.fill = c.linearGradient(0f, gy, 0f, h, 0f to hex("#b9b8b0"), 1f to hex("#8e8d86"))
    c.fillRect(max(0f, AIRPORT_END.toFloat() - cam), gy, w, h - gy)
    c.fill(rgba(0, 0, 0, 0.12f))
    var x = floor(cam / 60) * 60
    while (x < cam + w) {
        if (x >= AIRPORT_END) c.fillRect(x - cam, gy, 2f, h - gy)
        x += 60
    }
    c.fill(hex("#d9d8d0")); c.fillRect(max(0f, AIRPORT_END.toFloat() - cam), gy - 2, w, 6f)
    // chaussée + passage piéton
    c.fill(hex("#3a3d43"))
    c.fillRect(CROSS_A.toFloat() - cam, gy, (CROSS_B - CROSS_A).toFloat(), h - gy)
    c.fill(hex("#f2f2ee"))
    val depth = h - gy
    floatArrayOf(0.06f, 0.26f, 0.5f, 0.78f).forEachIndexed { i, f ->
        val y0 = gy + depth * f
        val hh = depth * (0.09f + i * 0.03f)
        c.fillRect(CROSS_A.toFloat() - cam + 16, y0, (CROSS_B - CROSS_A).toFloat() - 32, hh)
    }
    // feux
    val ls = lightState(e.time)
    drawPedLight(CROSS_A.toFloat() - 50 - cam, ls)
    drawCarLight(CROSS_B.toFloat() + 50 - cam, ls)
    // quelques objets de rue
    if (inView(3700f, 40f)) drawBench(3700 - cam)
    if (inView(3800f, 40f)) drawBin(3800 - cam)
    if (inView(4480f, 40f)) drawHydrant(4480 - cam)
    if (inView(4700f, 60f)) drawBusStop(4700 - cam)
    // intérieur de l'aéroport par-dessus, pour x < AIRPORT_END
    if (cam < AIRPORT_END) {
        c.saved {
            c.clipRect(0f, 0f, AIRPORT_END.toFloat() - cam, h)
            drawAirport()
        }
    }
}

/** Vitrine de La Trousserie : trousses exposées, enseigne, porte vitrée. */
private fun VilleScene.drawSkinShop() {
    if (!inView(SKINSHOP_X.toFloat(), 150f)) return
    val x = SKINSHOP_X.toFloat() - cam
    val open = !outage
    c.fill(hex("#2c3e50")); c.fillRect(x - 140, gy - 200, 280f, 200f)
    c.fill(hex("#8e44ad")); c.fillRect(x - 146, gy - 206, 292f, 40f)
    c.fill(hex("#ffd23f")); c.font = Ctx2D.Font(20f, FontWeight.Black, FontFamily.Serif); c.textAlign = Ctx2D.Align.CENTER
    c.fillText("👕 La Trousserie", x, gy - 179)
    for (k in 0 until 12) {
        c.fill(if (k % 2 != 0) hex("#f4f1ea") else hex("#8e44ad"))
        c.beginPath()
        c.moveTo(x - 140 + k * 23.3f, gy - 166); c.lineTo(x - 116.7f + k * 23.3f, gy - 166)
        c.lineTo(x - 121 + k * 23.3f, gy - 150); c.lineTo(x - 135 + k * 23.3f, gy - 150)
        c.fill()
    }
    c.fill(if (open) rgba(255, 236, 190, 0.95f) else rgba(60, 70, 90, 0.9f)); c.fillRect(x - 128, gy - 142, 170f, 110f)
    c.fill(hex("#6d4c41")); c.fillRect(x - 128, gy - 60, 170f, 8f)
    SHOP_WINDOW_SKINS.forEachIndexed { i, id ->
        c.ds.drawTrousseSprite(
            sprites.image, id, x - 104 + i * 42, gy - 78, 40f, 0f,
            if (id == "classique") null else skinColorFilter(id), c.alpha, sprites.filterQuality,
        )
    }
    c.fill(hex("#15151a")); c.fillRect(x - 100, gy - 136, 30f, 4f); c.fillRect(x - 94, gy - 150, 18f, 14f)
    c.fill(hex("#ffd23f"))
    c.beginPath()
    c.moveTo(x - 30, gy - 124); c.lineTo(x - 24, gy - 138); c.lineTo(x - 18, gy - 128); c.lineTo(x - 12, gy - 138); c.lineTo(x - 6, gy - 124)
    c.fill()
    c.fill(rgba(170, 205, 230, 0.7f)); c.fillRect(x + 58, gy - 142, 70f, 142f)
    c.stroke(hex("#1c2833")); c.lineWidth = 4f; c.strokeRect(x + 58, gy - 142, 70f, 142f)
    c.fill(hex("#ffd23f")); c.fillRect(x + 64, gy - 76, 5f, 16f)
    c.fill(hex("#e74c3c")); c.fillRoundRect(x + 70, gy - 128, 48f, 18f, 4f)
    c.fill(Color.White); c.font = Ctx2D.Font(11f, FontWeight.Black)
    c.fillText(if (open) "OUVERT" else "FERMÉ", x + 94, gy - 115)
    c.textAlign = Ctx2D.Align.LEFT
}

private fun VilleScene.drawTower() {
    if (!inView(((TOWER_A + CITY_W) / 2).toFloat(), ((CITY_W - TOWER_A) / 2).toFloat() + 40)) return
    val x0 = TOWER_A.toFloat() - cam
    val w = (CITY_W - TOWER_A).toFloat() + 20
    val lit = if (outage) 0f else sky.night
    c.fill(hex("#4a5568"))
    c.fillRect(x0, -20f, w, gy + 20)
    var r = 0
    while (r * 46 < gy - 180) {
        var k = 0
        while (k * 58 < w - 40) {
            val on = sr(r * 3.3 + k * 7.1 + 50) < lit * 0.55f
            c.fill(if (on) rgba(255, 214, 140, 0.95f) else if (sky.night > 0.5f) hex("#232a3a") else rgba(170, 210, 240, 0.7f))
            c.fillRect(x0 + 24 + k * 58, gy - 210 - r * 46, 40f, 32f)
            k++
        }
        r++
    }
    c.fill(hex("#c9b28a")); c.fillRect(x0, gy - 170, w, 170f)
    val dx = TOWER_DOOR.toFloat() - cam
    c.fill(hex("#7a1f2b")); c.fillRect(dx - 110, gy - 176, 220f, 30f)
    c.fill(hex("#ffd23f")); c.font = Ctx2D.Font(15f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
    c.fillText("LA TOUR — RÉCEPTION", dx, gy - 156)
    c.fill(rgba(255, 210, 140, 0.95f)); c.fillRect(dx - 60, gy - 140, 120f, 140f)
    c.fill(hex("#5b3b1c")); c.fillRect(dx - 64, gy - 144, 128f, 6f); c.fillRect(dx - 2, gy - 140, 4f, 140f)
    c.fill(rgba(255, 255, 255, 0.25f)); c.fillRect(dx - 50, gy - 130, 12f, 110f)
    for (o in intArrayOf(-95, 95)) {
        c.fill(hex("#6d4c41")); c.fillRect(dx + o - 16, gy - 34, 32f, 34f)
        c.fill(hex("#2e7d32")); c.fillCircle(dx + o, gy - 50, 24f)
    }
    c.fill(hex("#e53935")); c.fillRect(CITY_W.toFloat() - 60 - cam, gy - 70, 12f, 70f)
    for (k in 0 until 4) {
        c.fill(if (k % 2 != 0) Color.White else hex("#e53935"))
        c.fillRect(CITY_W.toFloat() - 100 - cam, gy - 70 + k * 12, 90f, 12f)
    }
    c.textAlign = Ctx2D.Align.LEFT
}

private fun VilleScene.drawPedLight(x: Float, ls: CrosswalkLight) {
    c.fill(hex("#2b2f36")); c.fillRect(x - 4, gy - 210, 8f, 210f)
    c.fill(hex("#1a1c20")); c.fillRoundRect(x - 24, gy - 270, 48f, 90f, 8f)
    val red = ls.pedestrian == PedestrianLight.RED
    val greenOn = ls.pedestrian == PedestrianLight.GREEN ||
        (ls.pedestrian == PedestrianLight.BLINK && floor(e.time * 4).toInt() % 2 != 0)
    c.fill(if (red) hex("#ff3b3b") else hex("#401010")); c.fillRoundRect(x - 17, gy - 262, 34f, 36f, 6f)
    c.fill(if (greenOn) hex("#3ddc84") else hex("#0f3020")); c.fillRoundRect(x - 17, gy - 222, 34f, 36f, 6f)
    c.saved {
        c.font = Ctx2D.Font(22f); c.textAlign = Ctx2D.Align.CENTER; c.textBaseline = Ctx2D.Baseline.MIDDLE
        c.alpha = if (red) 1f else 0.25f; c.fillText("🧍", x, gy - 243)
        c.alpha = if (greenOn) 1f else 0.25f; c.fillText("🚶", x, gy - 203)
        c.alpha = 1f
        c.fill(hex("#1a1c20")); c.fillRoundRect(x - 16, gy - 176, 32f, 20f, 5f)
        c.fill(if (red) hex("#ff6b6b") else hex("#6bffb0")); c.font = Ctx2D.Font(13f, FontWeight.ExtraBold)
        c.fillText(kotlin.math.ceil(ls.secondsLeft).toInt().toString(), x, gy - 165)
    }
}

private fun VilleScene.drawCarLight(x: Float, ls: CrosswalkLight) {
    c.fill(hex("#2b2f36")); c.fillRect(x - 4, gy - 240, 8f, 240f)
    c.fill(hex("#1a1c20")); c.fillRoundRect(x - 16, gy - 330, 32f, 90f, 8f)
    val carsGo = ls.pedestrian == PedestrianLight.RED
    c.fill(if (!carsGo) hex("#ff3b3b") else hex("#401010")); c.fillCircle(x, gy - 314, 10f)
    c.fill(if (ls.pedestrian == PedestrianLight.BLINK) hex("#ffb52e") else hex("#40300a")); c.fillCircle(x, gy - 286, 10f)
    c.fill(if (carsGo) hex("#3ddc84") else hex("#0f3020")); c.fillCircle(x, gy - 258, 10f)
}

private fun VilleScene.drawBench(x: Float) {
    c.fill(hex("#6d4c41")); c.fillRect(x - 50, gy - 40, 100f, 8f); c.fillRect(x - 50, gy - 70, 100f, 8f)
    c.fill(hex("#333333")); c.fillRect(x - 44, gy - 40, 6f, 40f); c.fillRect(x + 38, gy - 40, 6f, 40f)
}

private fun VilleScene.drawBin(x: Float) {
    c.fill(hex("#2e7d32")); c.fillRoundRect(x - 18, gy - 56, 36f, 56f, 5f)
    c.fill(hex("#1b5e20")); c.fillRect(x - 21, gy - 60, 42f, 8f)
}

private fun VilleScene.drawHydrant(x: Float) {
    c.fill(hex("#c62828")); c.fillRoundRect(x - 11, gy - 48, 22f, 48f, 6f)
    c.fillRect(x - 17, gy - 34, 34f, 8f)
    c.beginPath(); c.arc(x, gy - 48, 11f, kotlin.math.PI.toFloat(), 0f); c.fill()
}

private fun VilleScene.drawBusStop(x: Float) {
    c.fill(rgba(180, 215, 235, 0.5f)); c.fillRect(x - 70, gy - 150, 140f, 150f)
    c.fill(hex("#37474f")); c.fillRect(x - 76, gy - 156, 152f, 10f); c.fillRect(x - 74, gy - 150, 5f, 150f); c.fillRect(x + 69, gy - 150, 5f, 150f)
    c.fill(hex("#1565c0")); c.fillRoundRect(x + 80, gy - 200, 34f, 34f, 17f)
    c.fill(Color.White); c.font = Ctx2D.Font(16f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
    c.fillText("BUS", x + 97, gy - 177)
    c.fill(hex("#37474f")); c.fillRect(x + 95, gy - 166, 4f, 166f)
    c.textAlign = Ctx2D.Align.LEFT
}

/**
 * Intérieur de l'aéroport : baies vitrées sur le tarmac, tapis à bagages,
 * tableau des vols, sièges, portique de sécurité et douaniers crabes.
 */
private fun VilleScene.drawAirport() {
    val h = vh
    val x1 = AIRPORT_END.toFloat() - cam
    val airportEnd = AIRPORT_END.toFloat()
    // mur
    c.fill = c.linearGradient(0f, 0f, 0f, gy, 0f to hex("#d9e1ea"), 1f to hex("#c3ceda"))
    c.fillRect(0f, 0f, x1, gy)
    // baies vitrées : ciel réel + tarmac + avions garés
    val wTop = max(40f, gy - 390)
    val wBot = gy - 150
    c.saved {
        c.beginPath()
        var x = 0f
        while (x < airportEnd) {
            if (!(x > 1380 && x < 1900) && inView(x + 80, 100f)) c.rect(x + 12 - cam, wTop, 166f, wBot - wTop)
            x += 190
        }
        c.clip()
        c.drawVilleSky(vw, h, wBot, sky, cam)
        c.drawSkyline(vw, wBot - 40, cam, 0.1f, 3, SkylineStyle(hazeColor(sky, 0.6f), sky.night * 0.8f, 20f, 90f, bw = 50f))
        c.fill(hex("#5f6670")); c.fillRect(0f, wBot - 40, vw, 40f)
        c.fill(hex("#e8d06a"))
        var mx = -((cam * 0.6f) % 80)
        while (mx < vw) { c.fillRect(mx, wBot - 20, 40f, 3f); mx += 80 }
        floatArrayOf(300f, 1300f, 2300f).forEachIndexed { i, px ->
            c.drawAirliner(px - cam * 0.6f, wBot - 58, 0.34f, dir = if (i % 2 != 0) -1 else 1, lights = sky.night > 0.4f)
        }
        val tk = (t * 140) % 3600
        c.drawAirliner(
            tk - 400 - cam * 0.6f + 800, wBot - 70 - max(0f, tk - 1400) * 0.12f, 0.28f,
            dir = 1, rot = if (tk > 1400) -0.12f else 0f, lights = true,
        )
    }
    c.fill(hex("#8b96a3"))
    var fx = 0f
    while (fx < airportEnd) {
        if (!(fx > 1380 && fx < 1900) && inView(fx + 80, 100f)) {
            c.fillRect(fx + 6 - cam, wTop - 6, 178f, 8f); c.fillRect(fx + 6 - cam, wBot - 2, 178f, 8f)
            c.fillRect(fx + 6 - cam, wTop, 8f, wBot - wTop); c.fillRect(fx + 176 - cam, wTop, 8f, wBot - wTop)
            c.fillRect(fx + 92 - cam, wTop, 4f, wBot - wTop)
        }
        fx += 190
    }
    // plafond + néons
    c.fill(hex("#56616e")); c.fillRect(0f, 0f, x1, max(0f, wTop - 26))
    var nx = floor(cam / 220) * 220
    while (nx < min(airportEnd, cam + vw + 220)) {
        c.fill(hex("#f4f8ff")); c.fillRect(nx - cam, wTop - 30, 90f, 6f)
        nx += 220
    }
    // sol carrelé brillant
    c.fill = c.linearGradient(0f, gy, 0f, h, 0f to hex("#c2cad3"), 1f to hex("#98a2ad"))
    c.fillRect(0f, gy, x1, h - gy)
    c.fill(rgba(255, 255, 255, 0.18f)); c.fillRect(0f, gy + 10, x1, 4f)
    c.fill(rgba(0, 0, 0, 0.08f))
    var tx = floor(cam / 80) * 80
    while (tx < min(airportEnd, cam + vw)) { c.fillRect(tx - cam, gy, 2f, h - gy); tx += 80 }
    c.fill(hex("#7d8894")); c.fillRect(0f, gy - 3, x1, 5f)
    // panneaux suspendus
    hangSign(260f, wTop, "✈ DÉPARTS", hex("#1d3b6e"))
    hangSign(700f, wTop, "ARRIVÉES ↓", hex("#1d3b6e"))
    hangSign(1500f, wTop, "CONTRÔLE DE SÉCURITÉ", hex("#6e1d2b"))
    hangSign(2680f, wTop, "SORTIE →", hex("#1b5e20"))
    // comptoir des départs + agent crabe
    if (inView(260f, 140f)) {
        val x = 260 - cam
        drawCrabGuardAt(x + 10, "airline", 1, 52f)
        c.fill(hex("#3f5d8a")); c.fillRect(x - 120, gy - 90, 240f, 90f)
        c.fill(hex("#e9eef5")); c.fillRect(x - 126, gy - 96, 252f, 10f)
        c.fill(hex("#ffd23f")); c.font = Ctx2D.Font(13f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("BULLY AIR", x, gy - 44)
        c.fill(hex("#12161f")); c.fillRect(x - 80, gy - 230, 160f, 70f)
        c.fill(hex("#ffcf3f")); c.font = Ctx2D.Font(11f, FontWeight.Bold, FontFamily.Monospace); c.textAlign = Ctx2D.Align.LEFT
        val save = e.host.save
        val rows = listOf(
            "COUR D'ÉCOLE" to "OK",
            "VOLCANS" to (if (save.volcanUnlocked) "OK" else "--"),
            "PLAGE" to (if (save.plageUnlocked) "OK" else "--"),
            "LUNE" to "ANNULÉ",
        )
        rows.forEachIndexed { i, r ->
            c.fillText(r.first, x - 72, gy - 212 + i * 15)
            c.fillText(r.second, x + 38, gy - 212 + i * 15)
        }
    }
    // tapis à bagages
    if (inView(720f, 200f)) {
        val x = 720 - cam
        c.fill(hex("#2d3137")); c.fillRoundRect(x - 170, gy - 46, 340f, 30f, 15f)
        c.fill(hex("#555b64")); c.fillRect(x - 160, gy - 44, 320f, 6f)
        for (k in 0 until 5) {
            val bx = x - 160 + ((t * 40 + k * 70) % 320)
            c.fill(hex(BAG_COLORS[k])); c.fillRoundRect(bx - 16, gy - 70, 32f, 26f, 4f)
            c.fill(hex("#222222")); c.fillRect(bx - 5, gy - 76, 10f, 6f)
        }
    }
    // tableau des vols
    if (inView(1050f, 120f)) {
        val x = 1050 - cam
        c.fill(hex("#12161f")); c.fillRect(x - 100, gy - 300, 200f, 110f)
        c.fill(hex("#7d8894")); c.fillRect(x - 3, gy - 190, 6f, 190f)
        c.fill(hex("#ffcf3f")); c.font = Ctx2D.Font(11f, FontWeight.Bold, FontFamily.Monospace); c.textAlign = Ctx2D.Align.LEFT
        val now = System.currentTimeMillis()
        listOf("BT 101", "BT 207", "BT 314", "BT 420", "BT 999").forEachIndexed { i, f ->
            val time = hhmm(now + (i + 1) * 17 * 60_000L)
            c.fillText("$f  $time  ${if (i == 4) "???" else "À L'HEURE"}", x - 90, gy - 280 + i * 18)
        }
    }
    // sièges
    for (k in 0 until 6) {
        val sx = 1150f + k * 42
        if (!inView(sx, 30f)) continue
        c.fill(hex("#2a5d9f")); c.fillRoundRect(sx - cam - 17, gy - 64, 34f, 40f, 5f)
        c.fill(hex("#1f4a80")); c.fillRect(sx - cam - 19, gy - 30, 38f, 10f)
        c.fill(hex("#555555")); c.fillRect(sx - cam - 2, gy - 20, 4f, 20f)
    }
    if (inView(1236f, 40f)) {
        val y = gy - 50
        c.drawCrab(1236 - cam, y, 36f, 0f, 1)
        c.drawCrabOutfit(1236 - cam, y, 36f, 1, "sunglasses")
    }
    // plantes
    for (px in floatArrayOf(480f, 1360f, 2200f)) if (inView(px, 40f)) drawPlant(px - cam, 1f)
    // scanner à rayons X + portique
    if (inView(SECURITY_X.toFloat(), 260f)) {
        val x = SECURITY_X.toFloat() - cam
        c.fill(hex("#9aa3ad")); c.fillRect(x - 200, gy - 80, 120f, 50f)
        c.fill(hex("#6c7680")); c.fillRect(x - 230, gy - 36, 180f, 10f)
        c.fill(hex("#2d3137")); c.fillRect(x - 170, gy - 76, 60f, 36f)
        c.fill(hex("#1f2227")); for (k in 0 until 6) c.fillRect(x - 168 + k * 10, gy - 76, 6f, 30f)
        c.fill(hex("#c9ced4")); c.fillRoundRect(x - 226, gy - 52, 40f, 16f, 3f)
        // portique (la trousse passe dessous)
        c.fill(hex("#b8c0c8"))
        c.fillRect(x - 46, gy - 210, 14f, 210f); c.fillRect(x + 32, gy - 210, 14f, 210f); c.fillRect(x - 46, gy - 222, 92f, 16f)
        val ok = e.levels.city.state.securityFlash > 0
        c.fill(if (ok) hex("#3ddc84") else hex("#7b2b2b")); c.fillCircle(x, gy - 230, 8f)
        if (ok) { c.fill(rgba(61, 220, 132, 0.18f)); c.fillRect(x - 32, gy - 206, 64f, 206f) }
    }
    // douaniers crabes, juste après le portique
    drawCrabGuard(1770f, "police", -1, 60f)
    drawCrabGuard(1900f, "police", -1, 60f)
    drawCrabGuard(2080f, "police", 1, 56f)
    // portes coulissantes de sortie
    if (inView(airportEnd - 40, 140f)) {
        val x = airportEnd - 40 - cam
        c.fill(hex("#6f7a86")); c.fillRect(x - 90, gy - 230, 180f, 12f)
        val o = e.levels.city.state.door.toFloat() * 70
        c.fill(rgba(170, 210, 235, 0.55f))
        c.fillRect(x - 80 - o, gy - 218, 78f, 218f); c.fillRect(x + 2 + o, gy - 218, 78f, 218f)
        c.stroke(hex("#6f7a86")); c.lineWidth = 4f
        c.strokeRect(x - 80 - o, gy - 218, 78f, 218f); c.strokeRect(x + 2 + o, gy - 218, 78f, 218f)
        c.fill(hex("#1b5e20")); c.fillRoundRect(x - 36, gy - 262, 72f, 24f, 4f)
        c.fill(Color.White); c.font = Ctx2D.Font(13f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
        c.fillText("SORTIE", x, gy - 245)
    }
    c.textAlign = Ctx2D.Align.LEFT
}

private fun VilleScene.hangSign(x: Float, wTop: Float, text: String, color: Color) {
    if (!inView(x, 150f)) return
    val sx = x - cam
    val y = max(8f, wTop - 22)
    c.font = Ctx2D.Font(14f, FontWeight.Black)
    val w = c.measureText(text) + 30
    c.fill(hex("#4b5563")); c.fillRect(sx - w / 2 + 12, 0f, 3f, y); c.fillRect(sx + w / 2 - 15, 0f, 3f, y)
    c.fill(color); c.fillRoundRect(sx - w / 2, y, w, 30f, 5f)
    c.fill(hex("#ffd23f")); c.textAlign = Ctx2D.Align.CENTER
    c.fillText(text, sx, y + 20)
    c.textAlign = Ctx2D.Align.LEFT
}

internal fun VilleScene.drawPlant(x: Float, k: Float) {
    c.fill(hex("#8d5a3b")); c.fillRect(x - 18 * k, gy - 42 * k, 36 * k, 42 * k)
    c.fill(hex("#2e7d32"))
    for (i in 0 until 5) {
        c.fillEllipse(x + (i - 2) * 9 * k, gy - 62 * k - kotlin.math.abs(i - 2) * -6 * k, 9 * k, 26 * k, (i - 2) * 0.35f)
    }
}

internal fun VilleScene.drawCityFront() {
    val h = vh
    // piliers au premier plan dans l'aéroport
    for (x in floatArrayOf(1180f, 2380f)) {
        if (!inView(x, 40f)) continue
        c.fill(hex("#3f4752")); c.fillRect(x - 26 - cam, 0f, 52f, h)
        c.fill(rgba(255, 255, 255, 0.08f)); c.fillRect(x - 20 - cam, 0f, 8f, h)
    }
    // poteaux et cordons de la file d'attente
    var x = 1380f
    while (x <= 1540f) {
        if (inView(x, 40f)) {
            c.fill(hex("#c9a227")); c.fillRect(x - cam - 3, gy + 20 - 70, 6f, 70f)
            c.fill(hex("#555555")); c.fillEllipse(x - cam, gy + 22, 12f, 4f)
            if (x < 1540f) {
                c.stroke(hex("#b71c1c")); c.lineWidth = 4f
                c.beginPath(); c.moveTo(x - cam, gy - 44); c.quadraticCurveTo(x + 40 - cam, gy - 20, x + 80 - cam, gy - 44); c.stroke()
            }
        }
        x += 80f
    }
    // premier plan de la rue : grands lampadaires et un arbre
    if (inView(3060f, 80f)) drawLamp(3060 - cam, true)
    if (inView(4620f, 80f)) drawLamp(4620 - cam, true)
    if (inView(3560f, 120f)) {
        val tx = 3560 - cam
        c.fill(hex("#4e342e")); c.fillRect(tx - 10, gy - 150, 20f, 170f)
        c.fill(hex("#256d2e"))
        for (b in listOf(floatArrayOf(0f, -210f, 70f), floatArrayOf(-50f, -170f, 52f), floatArrayOf(50f, -170f, 52f), floatArrayOf(0f, -150f, 60f))) {
            c.fillCircle(tx + b[0], gy + b[1], b[2])
        }
        c.fill(hex("#5d4037")); c.fillRect(tx - 44, gy + 12, 88f, 26f)
    }
    // bulles des douaniers
    val px = e.player.x
    if (kotlin.math.abs(px - 1830) < 180) {
        bubble(1770 - cam, gy - 110, "Contrôle terminé. Bienvenue en Ville !")
    } else if (kotlin.math.abs(px - 2080) < 150) {
        bubble(2080 - cam, gy - 110, "Circulez, circulez...")
    }
    val bip = e.levels.city.state.bip.toFloat()
    if (bip > 0) {
        c.saved {
            c.alpha = c01f(bip * 2)
            c.fill(hex("#3ddc84")); c.font = Ctx2D.Font(22f, FontWeight.Black); c.textAlign = Ctx2D.Align.CENTER
            c.fillText("BIP ✅", SECURITY_X.toFloat() - cam, gy - 250 - (1 - bip) * 30)
        }
    }
}
