package com.bullythetrousse.core

import kotlin.random.Random

/**
 * Le pseudo affiché au classement et sur le profil public, porté de
 * `save.pseudo` côté web : le nom par défaut attribué à la première
 * connexion (`"Trousse-" + 1000..9999`) et le nettoyage appliqué à une
 * saisie (`next.trim().slice(0, 24)`).
 *
 * Mis dans `:core` plutôt que dans l'écran qui l'édite : la longueur
 * maximale et le format du nom par défaut décident de ce qui part dans
 * Firestore, donc de ce que voient les autres joueurs — c'est une règle du
 * jeu, pas de l'habillage.
 */
object Pseudo {
    /** `next.trim().slice(0, 24)` côté web. */
    const val MAX_LENGTH = 24

    /** `"Trousse-" + Math.floor(1000 + Math.random() * 9000)` côté web :
     *  quatre chiffres, donc jamais de pseudo vide au classement. */
    fun generateDefault(random: Random = Random.Default): String = "Trousse-${1000 + random.nextInt(9000)}"

    /** Ce qu'on garde d'une saisie : espaces de bord retirés, longueur
     *  plafonnée. Renvoie `null` si rien d'utilisable ne reste — l'appelant
     *  garde alors le pseudo courant plutôt que d'en effacer un bon. */
    fun sanitize(input: String): String? = input.trim().take(MAX_LENGTH).ifBlank { null }
}
