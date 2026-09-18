package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Valeurs de référence calculées directement à partir des formules JS de
 * index.html (voir onLanded/upgradeCost), pour garantir que ce portage
 * Kotlin donne les mêmes montants que la version web.
 */
class EconomyTest {
    @Test
    fun `argent gagne sur un lancer normal`() {
        assertEquals(200, Economy.moneyEarned(distanceMeters = 100.0, isPerfect = false, totalLevels = 0))
    }

    @Test
    fun `argent gagne sur un lancer parfait avec des niveaux`() {
        assertEquals(416, Economy.moneyEarned(distanceMeters = 100.0, isPerfect = true, totalLevels = 20))
    }

    @Test
    fun `argent minimum garanti meme sur une distance nulle`() {
        assertEquals(1, Economy.moneyEarned(distanceMeters = 0.0, isPerfect = false, totalLevels = 0))
    }

    @Test
    fun `cout d'amelioration avant, au, et apres le plafond exponentiel`() {
        assertEquals(25, Economy.upgradeCost(0))
        assertEquals(112497, Economy.upgradeCost(25))
        assertEquals(127497, Economy.upgradeCost(26))
        assertEquals(187497, Economy.upgradeCost(30))
    }
}
