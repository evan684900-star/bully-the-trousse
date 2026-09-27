package com.bullythetrousse.core

/**
 * Vibrations du jeu — pure addition Android, sans équivalent côté site
 * (le web n'a pas de retour haptique). Les évènements suivent les mêmes
 * points d'appel que les bruitages (voir [SfxCatalog]) : un geste qui a un
 * son a en général aussi une vibration, pour renforcer la même information
 * plutôt que d'en ajouter une autre.
 */
enum class HapticEvent {
    /** Puissance verrouillée pendant la charge : un tic bref. */
    CHARGE,

    /** Lancer parti : un déclic net. */
    LAUNCH,

    /** Atterrissage normal : un impact sourd. */
    LAND,

    /** Écrasement au retour de l'espace, mort dans la cinématique du volcan :
     *  un impact plus long et plus marqué que [LAND]. */
    CRASH,

    /** Nouveau record ou combo QTE parfait : une double pulsation. */
    RECORD,

    /** Achat réussi, équipement d'un skin/traînée : un tic léger. */
    BUY,

    /** Achat refusé, anneau du QTE raté : deux tics courts. */
    ERROR,

    /** Anneau du QTE touché : un tic très bref. */
    QTE_HIT,

    /** Succès débloqué : une pulsation distincte, plus longue que [RECORD]. */
    ACHIEVEMENT,

    /** Bouton 🦇 : un tic léger à l'appui, pas de vibration au maintien. */
    VAMPIRE_BOOST,

    /** Tremblement de terre (cinématique du volcan) : longue vibration continue. */
    QUAKE,
}

/**
 * Un motif de vibration : des segments alternés (vibration, silence), en
 * millisecondes, éventuellement avec une amplitude par segment (voir
 * [HapticPattern.amplitudes]) — équivalent d'un `VibrationEffect.createWaveform`.
 *
 * Le premier segment de [timingsMillis] est TOUJOURS une vibration (jamais un
 * silence de tête), comme `VibrationEffect.createWaveform` côté Android.
 */
data class HapticPattern(
    val timingsMillis: List<Long>,
    /** Une amplitude par segment de vibration (1..255), ou `null` pour
     *  l'amplitude par défaut du moteur. Toujours de la même taille que
     *  [timingsMillis] côté segments "vibration" (indices pairs). */
    val amplitudes: List<Int>? = null,
) {
    init {
        require(timingsMillis.isNotEmpty()) { "un motif vide ne vibre jamais" }
        require(timingsMillis.all { it >= 0 }) { "une durée négative n'a pas de sens" }
    }

    /** Durée totale du motif, silences compris. */
    val totalMillis: Long get() = timingsMillis.sum()
}

/** Les motifs du jeu, un par [HapticEvent]. */
object HapticCatalog {
    val CHARGE = HapticPattern(listOf(12L))
    val LAUNCH = HapticPattern(listOf(30L))
    val LAND = HapticPattern(listOf(40L))
    val CRASH = HapticPattern(listOf(70L))
    val RECORD = HapticPattern(listOf(30L, 60L, 30L))
    val BUY = HapticPattern(listOf(15L))
    val ERROR = HapticPattern(listOf(25L, 50L, 25L))
    val QTE_HIT = HapticPattern(listOf(10L))
    val ACHIEVEMENT = HapticPattern(listOf(25L, 40L, 25L, 40L, 40L))

    /** Amplitude réduite : un simple tic, pas un impact. */
    val VAMPIRE_BOOST = HapticPattern(listOf(12L), amplitudes = listOf(90))

    /** Grondement continu pendant le tremblement de terre : un motif répété
     *  vibration/pause tant que la phase dure (voir VolcanoCinematicScreen,
     *  qui le redéclenche à intervalles réguliers plutôt qu'une seule fois). */
    val QUAKE = HapticPattern(listOf(120L, 80L), amplitudes = listOf(160))

    fun forEvent(event: HapticEvent): HapticPattern = when (event) {
        HapticEvent.CHARGE -> CHARGE
        HapticEvent.LAUNCH -> LAUNCH
        HapticEvent.LAND -> LAND
        HapticEvent.CRASH -> CRASH
        HapticEvent.RECORD -> RECORD
        HapticEvent.BUY -> BUY
        HapticEvent.ERROR -> ERROR
        HapticEvent.QTE_HIT -> QTE_HIT
        HapticEvent.ACHIEVEMENT -> ACHIEVEMENT
        HapticEvent.VAMPIRE_BOOST -> VAMPIRE_BOOST
        HapticEvent.QUAKE -> QUAKE
    }
}
