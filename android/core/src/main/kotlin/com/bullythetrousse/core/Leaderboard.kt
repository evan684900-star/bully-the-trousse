package com.bullythetrousse.core

/**
 * Ce que le joueur envoie aux classements en ligne, porté de
 * `mainLeaderboardRecord()` / `scoresCollection()` côté site.
 *
 * - Classement mondial (`scores`) : la Cour d'école (Volcans compris) ET le
 *   toit de la Ville y comptent ensemble, avec le meilleur des deux records
 *   (depuis la 11.5.0 ; avant, le record de la Ville n'allait nulle part).
 * - Classement Plage (`scoresPlage`) : à part, avec le record de la plage.
 *
 * Chaque record reste gardé séparément dans la sauvegarde (succès, fiche
 * trousse) : seul ce qui part au classement est regroupé.
 */
object Leaderboard {
    const val WORLD = "scores"
    const val PLAGE = "scoresPlage"

    /** `mainLeaderboardRecord()` : le record du classement mondial. */
    fun worldRecord(save: GameSave): Double = maxOf(save.bestDistance, save.villeBestDistance)

    /** `scoresCollection()` : le classement du monde où l'on joue. */
    fun collectionFor(save: GameSave): String = if (save.inPlage) PLAGE else WORLD

    /** Le record à publier dans [collectionFor]. */
    fun recordFor(save: GameSave): Double = if (save.inPlage) save.plageBestDistance else worldRecord(save)

    /** Les deux classements avec chacun SON record (`pushAllScores()`) :
     *  à la connexion et au changement de pseudo. */
    fun allRecords(save: GameSave): List<Pair<String, Double>> =
        listOf(WORLD to worldRecord(save), PLAGE to save.plageBestDistance)
}
