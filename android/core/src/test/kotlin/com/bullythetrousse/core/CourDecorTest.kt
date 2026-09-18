package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Valeurs de référence calculées directement depuis drawBackground()/
 * seededRand() côté web, pour cameraX=500, screenWidth=800.
 */
class CourDecorTest {
    @Test
    fun `seededRand est deterministe et reproduit les memes valeurs que le JS`() {
        assertClose(0.9216903898159217, CourDecor.seededRand(1.0))
        assertClose(0.05721816934965318, CourDecor.seededRand(2.0))
    }

    @Test
    fun `les batiments visibles couvrent bien l'ecran avec la parallaxe`() {
        // parX = 500*0.25 = 125 ; start = floor((125-220)/260) = -1 ; end = ceil((125+800+40)/260) = 4
        assertEquals(-1..4, CourDecor.visibleBuildingIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `position ecran d'un batiment tient compte de la parallaxe`() {
        // bx = 0 * 260 - 125 = -125
        assertClose(-125.0, CourDecor.buildingScreenX(index = 0, cameraX = 500.0))
    }

    @Test
    fun `les dessins a la craie visibles couvrent bien l'ecran`() {
        // start = floor(500/210)-1 = 1 ; count = ceil(800/210)+2 = 6 -> 1..6
        assertEquals(1..6, CourDecor.visibleChalkIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `les arbres visibles couvrent bien l'ecran`() {
        // start = floor(500/300)-1 = 0 ; count = ceil(800/300)+2 = 5 -> 0..4
        assertEquals(0..4, CourDecor.visibleTreeIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
