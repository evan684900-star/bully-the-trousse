package com.bullythetrousse.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpaceSequenceTest {
    private val classique = Skins.find("classique")
    private val lunaire = Skins.find("lunaire")
    private val fusee = Skins.find("fusee")
    private val avion = Skins.find("avion")

    @Test
    fun `l easter egg demande la puissance maximale ET la zone verte`() {
        assertTrue(SpaceSequence.shouldTrigger(0.95, -0.8, classique))
        assertFalse(SpaceSequence.shouldTrigger(0.85, -0.8, classique)) // pas assez puissant
        assertFalse(SpaceSequence.shouldTrigger(0.95, -0.4, classique)) // pas assez à gauche
        // Pile sur les seuils : accepté (>= et <=, comme côté web).
        assertTrue(SpaceSequence.shouldTrigger(0.9, -0.6, classique))
    }

    @Test
    fun `le Boeing 747 ne part jamais en apesanteur`() {
        assertTrue(avion.isPlane)
        assertFalse(SpaceSequence.shouldTrigger(1.0, -1.0, avion))
    }

    @Test
    fun `l anneau retrecit lineairement de 110 a 0`() {
        assertEquals(110.0, SpaceSequence.currentRingRadius(0.0))
        assertEquals(55.0, SpaceSequence.currentRingRadius(SpaceSequence.RING_DURATION / 2), 1e-9)
        assertEquals(0.0, SpaceSequence.currentRingRadius(SpaceSequence.RING_DURATION), 1e-9)
        // Au-delà de la durée, il ne repart pas dans le négatif.
        assertEquals(0.0, SpaceSequence.currentRingRadius(5.0), 1e-9)
    }

    @Test
    fun `un tap compte comme reussi dans la marge autour de la cible`() {
        // Cible 46 : l'anneau vaut 46 quand t = (1 - 46/110) * 0.9.
        val target = 46.0
        val exact = (1.0 - target / SpaceSequence.RING_START_RADIUS) * SpaceSequence.RING_DURATION
        assertTrue(SpaceSequence.isHit(exact, target))
        // 15 px de marge => une petite erreur de timing passe encore.
        val slightlyEarly = (1.0 - (target + 14.0) / SpaceSequence.RING_START_RADIUS) * SpaceSequence.RING_DURATION
        assertTrue(SpaceSequence.isHit(slightlyEarly, target))
        // 30 px d'écart : raté.
        val tooEarly = (1.0 - (target + 30.0) / SpaceSequence.RING_START_RADIUS) * SpaceSequence.RING_DURATION
        assertFalse(SpaceSequence.isHit(tooEarly, target))
    }

    @Test
    fun `la sequence traverse transition puis flottement puis QTE`() {
        var state = SpaceState()
        state = SpaceSequence.step(state, SpaceSequence.TRANSITION_DURATION - 0.01, classique)
        assertEquals(SpacePhase.TRANSITION, state.phase)
        state = SpaceSequence.step(state, 0.02, classique)
        assertEquals(SpacePhase.FLOATING, state.phase)
        state = SpaceSequence.step(state, SpaceSequence.FLOAT_DURATION, classique)
        assertEquals(SpacePhase.QTE, state.phase)
        assertEquals(0, state.ringResults.size)
    }

    @Test
    fun `un anneau dont le temps est ecoule compte comme rate`() {
        var state = SpaceState(phase = SpacePhase.QTE)
        state = SpaceSequence.step(state, SpaceSequence.RING_DURATION + 0.01, classique)
        assertEquals(listOf(false), state.ringResults)
        assertEquals(0.0, state.phaseElapsed)
        assertEquals(SpacePhase.QTE, state.phase) // il en reste 4
    }

    @Test
    fun `cinq anneaux terminent la sequence`() {
        var state = SpaceState(phase = SpacePhase.QTE)
        repeat(SpaceSequence.RING_COUNT) {
            assertEquals(SpacePhase.QTE, state.phase)
            state = SpaceSequence.step(state, SpaceSequence.RING_DURATION + 0.01, classique)
        }
        assertEquals(SpacePhase.DONE, state.phase)
        assertEquals(5, state.ringResults.size)
    }

    @Test
    fun `la Trousse Lunaire simplifie le QTE a trois anneaux de taille moyenne`() {
        assertEquals(3, SpaceSequence.ringCount(lunaire))
        assertEquals(5, SpaceSequence.ringCount(classique))
        // Cible toujours moyenne, quel que soit le tirage.
        val random = Random(42)
        repeat(30) { assertEquals(SpaceSequence.RING_SIZES[1], SpaceSequence.pickTargetRadius(lunaire, random)) }

        var state = SpaceState(phase = SpacePhase.QTE)
        repeat(3) { state = SpaceSequence.step(state, SpaceSequence.RING_DURATION + 0.01, lunaire) }
        assertEquals(SpacePhase.DONE, state.phase)
    }

    @Test
    fun `la cible varie d un anneau a l autre pour les autres trousses`() {
        val random = Random(1)
        val drawn = (1..60).map { SpaceSequence.pickTargetRadius(classique, random) }.toSet()
        assertEquals(SpaceSequence.RING_SIZES.toSet(), drawn)
    }

    @Test
    fun `tout rater donne quand meme 30 pour cent du boost`() {
        val (vx, vy) = SpaceSequence.boostVelocity(hits = 0, skin = classique)
        assertEquals(SpaceSequence.BOOST_VX * 0.3, vx, 1e-9)
        assertEquals(SpaceSequence.BOOST_VY, vy, 1e-9)
    }

    @Test
    fun `tout reussir donne le boost complet`() {
        val (vx, vy) = SpaceSequence.boostVelocity(hits = 5, skin = classique)
        assertEquals(SpaceSequence.BOOST_VX, vx, 1e-9)
        assertEquals(SpaceSequence.BOOST_VY, vy, 1e-9)
    }

    @Test
    fun `la Trousse Fusee va une fois et demie plus vite en apesanteur`() {
        val (vx, vy) = SpaceSequence.boostVelocity(hits = 5, skin = fusee)
        assertEquals(SpaceSequence.BOOST_VX * SpaceSequence.ROCKET_SPEED_MULT, vx, 1e-9)
        assertEquals(SpaceSequence.BOOST_VY * SpaceSequence.ROCKET_SPEED_MULT, vy, 1e-9)
    }

    @Test
    fun `le succes des reflexes parfaits demande tous les anneaux`() {
        val perfect = SpaceState(phase = SpacePhase.DONE, ringResults = List(5) { true })
        assertTrue(SpaceSequence.isPerfect(perfect, classique))
        val almost = SpaceState(phase = SpacePhase.DONE, ringResults = listOf(true, true, true, true, false))
        assertFalse(SpaceSequence.isPerfect(almost, classique))
        // Trois anneaux suffisent avec la Lunaire, mais il les faut tous.
        val lunarPerfect = SpaceState(phase = SpacePhase.DONE, ringResults = List(3) { true })
        assertTrue(SpaceSequence.isPerfect(lunarPerfect, lunaire))
    }

    @Test
    fun `un tap bien place fait progresser le QTE`() {
        val target = SpaceSequence.RING_SIZES[1]
        val exact = (1.0 - target / SpaceSequence.RING_START_RADIUS) * SpaceSequence.RING_DURATION
        val state = SpaceState(phase = SpacePhase.QTE, phaseElapsed = exact, ringTargetRadius = target)
        val after = SpaceSequence.tap(state, classique)
        assertEquals(listOf(true), after.ringResults)
    }

    @Test
    fun `tap ne fait rien hors de la phase QTE`() {
        val floating = SpaceState(phase = SpacePhase.FLOATING, phaseElapsed = 0.4)
        assertEquals(floating, SpaceSequence.tap(floating, classique))
    }
}
