package com.bullythetrousse.core

/** Position écran (en pixels) d'un point du monde, voir [Camera.worldToScreen]. */
data class ScreenPoint(val sx: Double, val sy: Double)

/**
 * Conversion coordonnées monde -> écran et logique de caméra suiveuse,
 * portées depuis index.html :
 * - `worldToScreen(x, y)` : `{ sx: x - cameraX, sy: groundScreenY - y }`.
 * - la mise à jour de `cameraX`/`cameraY` dans le bloc "flying" de
 *   gameLoop() : la caméra garde la trousse à 30% de la largeur d'écran
 *   (jamais en dessous de 0, pour ne pas voir "derrière" le point de départ),
 *   et suit verticalement à partir d'un point de référence fixe.
 */
object Camera {
    /** `const targetCamera = worldX - w * 0.3; cameraX = Math.max(0, targetCamera);` */
    fun followX(worldX: Double, screenWidth: Double): Double =
        maxOf(0.0, worldX - screenWidth * 0.3)

    /** `const groundY = h * 0.68; cameraY = Math.max(0, VERTICAL_SAFE_TOP - groundY + worldY);` */
    fun followY(worldY: Double, screenHeight: Double, verticalSafeTop: Double): Double {
        val groundY = screenHeight * 0.68
        return maxOf(0.0, verticalSafeTop - groundY + worldY)
    }

    /** `{ sx: x - cameraX, sy: groundScreenY - y }` */
    fun worldToScreen(worldX: Double, worldY: Double, cameraX: Double, groundScreenY: Double): ScreenPoint =
        ScreenPoint(sx = worldX - cameraX, sy = groundScreenY - worldY)
}
