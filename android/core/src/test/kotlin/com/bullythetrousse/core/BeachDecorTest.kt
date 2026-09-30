package com.bullythetrousse.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Valeurs de référence calculées depuis drawBeachBackground()/
 *  generateBeachDecor() côté web, pour cameraX=500, screenWidth=800. */
class BeachDecorTest {
    @Test
    fun `les dunes visibles couvrent bien l'ecran`() {
        assertEquals(-1..4, BeachDecor.visibleDuneIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `position ecran d'une dune tient compte de sa parallaxe`() {
        assertClose(-90.0, BeachDecor.duneScreenX(index = 0, cameraX = 500.0))
    }

    @Test
    fun `largeur et hauteur d'une dune varient autour de sa taille de base`() {
        assertClose(61.423619420746945, BeachDecor.duneHeight(index = 2))
        assertClose(225.73440641928755, BeachDecor.duneWidth(index = 2))
    }

    @Test
    fun `les bosses de sable visibles couvrent bien l'ecran`() {
        assertEquals(2..9, BeachDecor.visibleSandBumpIndices(cameraX = 500.0, screenWidth = 800.0))
    }

    @Test
    fun `position taille d'une bosse de sable varient autour de sa base`() {
        assertClose(427.3292335015958, BeachDecor.sandBumpWorldX(index = 3))
        assertClose(11.856913278141292, BeachDecor.sandBumpHeight(index = 3))
        assertClose(150.62461797715514, BeachDecor.sandBumpWidth(index = 3))
    }

    @Test
    fun `un coquillage n'apparait que sur certaines bosses`() {
        assertFalse(BeachDecor.hasShell(index = 1))
        assertTrue(BeachDecor.hasShell(index = 2))
    }

    @Test
    fun `decorativeProps genere le bon nombre d'accessoires avec le bon type et la bonne position`() {
        val props = BeachDecor.decorativeProps(count = 1)
        assertEquals(1, props.size)
        val prop = props.first()
        assertEquals(BeachPropType.PARASOL, prop.type)
        assertClose(431.7780750681777, prop.worldX)
        assertClose(973.7480448238784, prop.seed)
        assertClose(0.8141596261382802, prop.scale)
        assertClose(10.962707183425664, prop.depth)
    }

    @Test
    fun `le decor couvre jusqu'a 2 000 000 m et s'arrete apres`() {
        val last = BeachDecor.propInSlot(BeachDecor.PROP_COUNT - 1)
        assertTrue(last != null && last.worldX >= 2_000_000.0 * PhysicsConstants.SCALE - BeachDecor.PROP_SLOT)
        assertNull(BeachDecor.propInSlot(BeachDecor.PROP_COUNT))
        assertNull(BeachDecor.propInSlot(-1))
        assertEquals(BeachDecor.PROP_COUNT, BeachDecor.decorativeProps().size)
    }

    @Test
    fun `propInSlot donne les memes accessoires que decorativeProps`() {
        val props = BeachDecor.decorativeProps(count = 50)
        props.forEachIndexed { i, prop -> assertEquals(prop, BeachDecor.propInSlot(i)) }
    }

    @Test
    fun `visiblePropIndices n'oublie aucun accessoire a l'ecran`() {
        val screenWidth = 412.0
        var cameraX = 0.0
        while (cameraX < 2_000_100.0 * PhysicsConstants.SCALE) {
            val range = BeachDecor.visiblePropIndices(cameraX, screenWidth)
            val around = (range.first - 3)..(range.last + 3)
            for (i in around) {
                val prop = BeachDecor.propInSlot(i) ?: continue
                val sx = prop.worldX - cameraX
                if (sx >= -160.0 && sx <= screenWidth + 160.0) assertTrue(i in range, "case $i oubliée à cameraX=$cameraX")
            }
            cameraX += 7919.0
        }
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
