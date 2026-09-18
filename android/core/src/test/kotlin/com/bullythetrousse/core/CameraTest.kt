package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Valeurs de référence calculées directement depuis worldToScreen() et la
 * mise à jour de cameraX/cameraY dans le bloc "flying" de gameLoop() côté web.
 */
class CameraTest {
    @Test
    fun `la camera suit la trousse a 30 pourcent de la largeur d'ecran`() {
        // worldX=1000, w=800 -> targetCamera = 1000 - 240 = 760
        assertClose(760.0, Camera.followX(worldX = 1000.0, screenWidth = 800.0))
    }

    @Test
    fun `la camera ne recule jamais sous 0`() {
        // worldX=100, w=800 -> targetCamera = 100 - 240 = -140 -> clampé à 0
        assertClose(0.0, Camera.followX(worldX = 100.0, screenWidth = 800.0))
    }

    @Test
    fun `la camera suit verticalement a partir du point de reference`() {
        // h=1000, groundY=680, verticalSafeTop=200, worldY=50
        // cameraY = max(0, 200 - 680 + 50) = max(0, -430) = 0
        assertClose(0.0, Camera.followY(worldY = 50.0, screenHeight = 1000.0, verticalSafeTop = 200.0))
        // worldY=600 -> max(0, 200 - 680 + 600) = 120
        assertClose(120.0, Camera.followY(worldY = 600.0, screenHeight = 1000.0, verticalSafeTop = 200.0))
    }

    @Test
    fun `worldToScreen soustrait la camera en x et inverse l'axe y`() {
        val point = Camera.worldToScreen(worldX = 500.0, worldY = 80.0, cameraX = 200.0, groundScreenY = 680.0)
        assertClose(300.0, point.sx)
        assertClose(600.0, point.sy)
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
