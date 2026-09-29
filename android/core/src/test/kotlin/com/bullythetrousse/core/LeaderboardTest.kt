package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals

class LeaderboardTest {
    @Test
    fun `le toit de la Ville compte dans le classement mondial`() {
        // Le cas remonté par un joueur : 19 000 m en Ville, 17 547 m dans la
        // cour, et le classement restait bloqué sur 17 547.
        val save = GameSave(bestDistance = 17547.5, villeBestDistance = 19000.0)
        assertEquals(19000.0, Leaderboard.worldRecord(save))
        assertEquals(Leaderboard.WORLD, Leaderboard.collectionFor(save))
        assertEquals(19000.0, Leaderboard.recordFor(save))
    }

    @Test
    fun `un meilleur record de la cour reste celui du classement`() {
        val save = GameSave(bestDistance = 25000.0, villeBestDistance = 19000.0, currentWorld = Ville.WORLD_ID)
        assertEquals(25000.0, Leaderboard.recordFor(save))
    }

    @Test
    fun `la plage garde son propre classement avec son propre record`() {
        val save = GameSave(bestDistance = 25000.0, villeBestDistance = 30000.0, plageBestDistance = 800.0, inPlage = true)
        assertEquals(Leaderboard.PLAGE, Leaderboard.collectionFor(save))
        assertEquals(800.0, Leaderboard.recordFor(save))
    }

    @Test
    fun `le changement de pseudo republie les deux classements avec chacun son record`() {
        val save = GameSave(bestDistance = 100.0, villeBestDistance = 250.0, plageBestDistance = 40.0, inPlage = true)
        assertEquals(listOf(Leaderboard.WORLD to 250.0, Leaderboard.PLAGE to 40.0), Leaderboard.allRecords(save))
    }
}
