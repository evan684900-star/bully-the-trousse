package com.bullythetrousse.core

/**
 * Les deux chemins vers le secret du jeu. Il ne débloque plus de trousse :
 * « secret » reste un simple drapeau dans `ownedSkins`, qui déclenche le
 * succès caché « secretTrouve » (voir `unlockSecretSkin()` côté site).
 */
object Secrets {
    const val SECRET_SKIN_ID = "secret"

    /** Ajoute le drapeau (sans doublon). */
    fun unlock(save: GameSave): GameSave =
        if (SECRET_SKIN_ID in save.ownedSkins) save else save.copy(ownedSkins = save.ownedSkins + SECRET_SKIN_ID)
}

/**
 * Le calcul mental caché au bas des Réglages (`SECRET_MATH_QUESTIONS`) :
 * 4 opérations à enchaîner, 5 s chacune. Les réponses suivent l'ordre
 * 1, 5, 1, 5 — un clin d'œil au +15 de la trousse qu'il débloquait.
 * Une erreur ou un temps écoulé referment tout sans un mot.
 */
object SecretMath {
    data class Question(val text: String, val answer: Int)

    val QUESTIONS = listOf(
        Question("38 × 17 − 645 = ?", 1),
        Question("47 × 23 − 1076 = ?", 5),
        Question("63 × 29 − 1826 = ?", 1),
        Question("84 × 19 − 1591 = ?", 5),
    )

    const val SECONDS_PER_QUESTION = 5

    /** `parseInt(input, 10) === q.answer` : les espaces autour sont tolérés. */
    fun isCorrect(index: Int, input: String): Boolean =
        QUESTIONS.getOrNull(index)?.answer == input.trim().toIntOrNull()

    /** Secondes affichées (`Math.ceil(remaining / 1000)`). */
    fun secondsLeft(remainingMillis: Long): Int = ((remainingMillis + 999) / 1000).toInt().coerceAtLeast(0)
}

/** Un geste de la séquence secrète : un glissé franc, ou un tapoti. */
enum class SecretGesture { UP, DOWN, LEFT, RIGHT, TAP }

/**
 * La séquence secrète au doigt (`SECRET_SEQUENCE` + `secretStep()`) :
 * ↓ ↓ ↑ ↑ → ← → ← puis deux tapotis (A et B côté clavier ; un doigt ne
 * distingue pas deux boutons, donc un tapoti vaut l'un ou l'autre).
 */
class SecretSequence {
    var progress: Int = 0
        private set

    /**
     * Fait avancer la séquence ; renvoie vrai quand elle vient d'être
     * complétée. Un mauvais geste la remet à 0 — ou à 1 si c'est le premier
     * pas attendu (↓), pour qu'on puisse la reprendre aussitôt.
     */
    fun step(gesture: SecretGesture): Boolean {
        val expected = SEQUENCE[progress]
        progress = when {
            gesture == expected -> progress + 1
            gesture == SEQUENCE[0] -> 1
            else -> 0
        }
        if (progress == SEQUENCE.size) {
            progress = 0
            return true
        }
        return false
    }

    companion object {
        val SEQUENCE = listOf(
            SecretGesture.DOWN, SecretGesture.DOWN, SecretGesture.UP, SecretGesture.UP,
            SecretGesture.RIGHT, SecretGesture.LEFT, SecretGesture.RIGHT, SecretGesture.LEFT,
            SecretGesture.TAP, SecretGesture.TAP,
        )

        /** Au-delà, ce n'est plus un tapoti (px / ms, comme le site). */
        const val TAP_MAX_DIST = 12f
        const val TAP_MAX_MS = 300L

        /** En deçà, le glissé est trop court pour compter. */
        const val SWIPE_MIN_DIST = 40f

        /**
         * Classe un geste d'après son déplacement et sa durée, ou null s'il
         * est ambigu (trop lent pour un tapoti, trop court pour un glissé) :
         * un geste ambigu ne casse pas une séquence en cours.
         */
        fun classify(dx: Float, dy: Float, elapsedMillis: Long): SecretGesture? {
            val dist = kotlin.math.hypot(dx, dy)
            return when {
                dist <= TAP_MAX_DIST && elapsedMillis <= TAP_MAX_MS -> SecretGesture.TAP
                dist >= SWIPE_MIN_DIST ->
                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        if (dx > 0) SecretGesture.RIGHT else SecretGesture.LEFT
                    } else {
                        if (dy > 0) SecretGesture.DOWN else SecretGesture.UP
                    }
                else -> null
            }
        }
    }
}
