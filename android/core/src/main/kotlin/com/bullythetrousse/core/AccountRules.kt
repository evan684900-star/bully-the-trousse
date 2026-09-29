package com.bullythetrousse.core

/**
 * Règles du compte à code (section 12 du site, 11.3.0), sans rien de
 * Firebase : ce qui décide de ce qu'on efface, de ce qu'on affiche et de ce
 * qu'on garde quand un appareil change de compte.
 */
object AccountRules {
    /**
     * `abandonCurrentAccount()` : plus personne ne pourra revenir sur un
     * compte qu'on quitte s'il est invité (aucun code) ou si son code n'a
     * jamais été montré (connu de personne, et oublié par cet appareil dès
     * qu'il change de compte). Tout le reste de ses traces publiques
     * (profil, sauvegarde, succès, abonnements) part alors avec lui. Dans
     * tous les cas, son entrée de classement est retirée : si le compte sert
     * encore ailleurs, l'autre appareil la republie à sa prochaine connexion.
     */
    fun isUnreachable(isAnonymous: Boolean, hasCode: Boolean, codeShown: Boolean): Boolean =
        isAnonymous || (hasCode && !codeShown)

    /** Une partie qui n'a encore rien fait : inutile de demander laquelle
     *  garder en connectant l'appareil (`saveHasProgress()`). */
    fun hasProgress(save: GameSave): Boolean =
        save.totalThrows > 0 || save.money > 0 || save.totalMoneyEarned > 0

    /**
     * La partie gardée quand le joueur choisit celle de CET appareil en
     * rejoignant un compte : son code est désormais connu (il vient de le
     * taper), et un compte qui a déjà joué sur Android garde son pseudo
     * arc-en-ciel.
     */
    fun keepLocal(local: GameSave, account: GameSave): GameSave =
        local.copy(accountCodeShown = true, playedOnAndroid = local.playedOnAndroid || account.playedOnAndroid)
}
