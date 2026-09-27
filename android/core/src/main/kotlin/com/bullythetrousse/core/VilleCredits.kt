package com.bullythetrousse.core

import kotlin.math.roundToLong

/**
 * Le générique de fin du mode histoire (`startCredits()` dans ville.js) :
 * quelques lignes d'adieu, toutes les statistiques de la partie et le
 * remerciement final.
 */
data class VilleCredits(
    val intro: List<String>,
    val stats: List<Pair<String, String>>,
    val finalLine: String,
) {
    companion object {
        fun build(save: GameSave, pseudo: String): VilleCredits {
            fun yesNo(value: Boolean) = if (value) "oui" else "non"
            val skinsOwned = Skins.ALL.count { it.id in save.ownedSkins }
            val stats = listOf(
                "Pseudo" to save.pseudo.ifEmpty { "—" },
                "Temps de jeu" to formatDuration(save.playTime),
                "Lancers au total" to save.totalThrows.toString(),
                "Record — Cour d'école" to meters(save.bestDistance),
                "Record — Plage" to meters(save.plageBestDistance),
                "Record — Toit de la Ville" to meters(save.villeBestDistance),
                "Argent gagné depuis le début" to "${save.totalMoneyEarned} $",
                "Argent en poche" to "${save.money} $",
                "Argent offert à d'autres joueurs" to "${save.totalMoneyGifted} $",
                "Niveau de Puissance" to save.puissanceLevel.toString(),
                "Niveau de Vitesse" to save.vitesseLevel.toString(),
                "Durabilité de la trousse" to save.durability.toString(),
                "Skins possédés" to "$skinsOwned / ${Skins.ALL.size}",
                "Traînées possédées" to "${save.ownedTrails.size} / ${Trails.ALL.size}",
                "Succès débloqués" to "${save.unlockedAchievements.size} / ${Achievements.ALL.size}",
                "Lancer parfait réussi" to yesNo(save.hasPerfectThrow),
                "Envolée vers l'espace" to yesNo(save.hasTriggeredSpaceEgg),
                "QTE spatial parfait" to yesNo(save.hasPerfectQte),
                "💩 trouvé" to yesNo(save.hasFoundPoopEgg),
                "Monde Volcan débloqué" to yesNo(save.volcanUnlocked),
                "Lancers à la plage" to save.plageThrows.toString(),
                "Rebonds sur un parasol" to save.plageParasolBounces.toString(),
                "Serviettes trouvées" to save.plageTowelsFound.toString(),
                "Châteaux de sable écroulés" to save.plageCastlesCrushed.toString(),
                "Argent gagné à la plage" to "${save.plageMoneyEarned} $",
                "Billet de bus acheté" to yesNo(save.hasTakenBusBack),
                "Lancers sur le toit de la Ville" to save.villeThrows.toString(),
                "Argent gagné en Ville" to "${save.villeMoneyEarned} $",
                // Les pas sont comptés en pixels : une trousse (64 px) ≈ 0,6 m.
                "Distance parcourue à pied" to "${(save.villeWalked / VILLE_TS * 0.6).roundToLong()} m",
                "Sauts" to save.villeJumps.toString(),
                "Dashs" to save.villeDashes.toString(),
                "Percuté au passage piéton" to "${save.villeCrosswalkDeaths} fois",
                "Étages montés à pied" to save.villeStairs.toString(),
                "Trajets en ascenseur" to save.villeElevator.toString(),
                "Glissades sur une flaque" to save.villePuddles.toString(),
                "Tentatives contre le boss" to save.villeBossAttempts.toString(),
                "Cœurs perdus" to save.villeHeartsLost.toString(),
                "Coups d'épée donnés" to save.villeSwordHits.toString(),
                // Pas encore compté : ce générique EST la fin de la partie en cours.
                "Mode histoire terminé" to "${save.villeStoryRuns + 1} fois",
            )
            return VilleCredits(
                intro = listOf(
                    "oh $pseudo tu as fini mon jeu ?",
                    "je ne pensais pas que ça allait arriver un jour...",
                    "ce jeu a vécu de nombreuses choses mais tu es visiblement allé au bout de ces choses,",
                    "tu y es parvenu avec l'aide d'amis peut-être, qui sait.....",
                ),
                stats = stats,
                finalLine = "Merci d'avoir joué à mon jeu, merci...",
            )
        }

        private fun meters(value: Double) = "${"%.1f".format(value)} m"

        /** `fmtDur()` : "1 h 2 min 3 s", ou "2 min 3 s" sous l'heure. */
        fun formatDuration(totalSeconds: Long): String {
            val sec = maxOf(0L, totalSeconds)
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return (if (h > 0) "$h h " else "") + "$m min $s s"
        }
    }
}
