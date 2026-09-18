package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Valeurs de référence calculées depuis drawVolcanoBackground() côté web,
 *  pour cameraX=500, screenWidth=800. */
class VolcanoDecorTest {
    @Test
    fun `couche lointaine visible couvre bien l'ecran`() {
        assertEquals(-1..4, VolcanoDecor.visibleVolcanoIndices(VolcanoDecor.LAYERS[0], cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `couche proche visible couvre bien l'ecran`() {
        assertEquals(-1..5, VolcanoDecor.visibleVolcanoIndices(VolcanoDecor.LAYERS[1], cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `position ecran d'un volcan tient compte de sa parallaxe et de son decalage`() {
        assertClose(-60.0, VolcanoDecor.volcanoScreenX(VolcanoDecor.LAYERS[0], index = 0, cameraX = 500.0))
    }

    @Test
    fun `le seed combine l'index et la couche`() {
        assertClose(18.4, VolcanoDecor.volcanoSeed(index = 2, layerIndex = 1))
    }

    @Test
    fun `largeur et hauteur d'un volcan varient autour de la taille de base de la couche`() {
        assertClose(259.09004740633463, VolcanoDecor.volcanoWidth(VolcanoDecor.LAYERS[0], seed = 18.4))
        assertClose(202.6635411477982, VolcanoDecor.volcanoHeight(VolcanoDecor.LAYERS[0], seed = 18.4))
    }

    @Test
    fun `les fissures visibles couvrent bien l'ecran`() {
        assertEquals(1..7, VolcanoDecor.visibleCrackIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `position d'une fissure varie legerement autour de sa position de base`() {
        assertClose(2.564689989958424, VolcanoDecor.crackWorldX(index = 0))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
