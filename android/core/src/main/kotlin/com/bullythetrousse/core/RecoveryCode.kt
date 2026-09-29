package com.bullythetrousse.core

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Le code de récupération à 16 chiffres, porté de `generateRecoveryCode()`/
 * `formatRecoveryCode()`/`normalizeRecoveryCode()`/`accountEmailForCode()`/
 * `accountPasswordForCode()` côté web (index.html, section 12).
 *
 * Ce code n'est pas qu'un mot de passe : côté web, il EST l'identité du
 * joueur. L'app dérive de lui un couple e-mail/mot de passe Firebase Auth,
 * donc un même code ouvre exactement le même compte — même uid, même
 * document de sauvegarde, une seule entrée de classement — que le joueur
 * arrive du site ou du téléphone.
 *
 * D'où l'importance de ce fichier : la moindre différence dans la dérivation
 * (domaine, préfixe, casse) créerait un compte SÉPARÉ au lieu de rejoindre
 * celui du site, en silence. C'est précisément ce que couvrent les tests.
 */
object RecoveryCode {
    const val DIGITS = 16

    /** `"c" + code + "@" + ACCOUNT_EMAIL_DOMAIN` côté web. */
    const val EMAIL_DOMAIN = "players.bullythetrousse.app"

    /** 16 chiffres tirés au hasard, chacun uniforme de 0 à 9 (le site écarte
     *  lui aussi les octets qui fausseraient la répartition). Tirage
     *  cryptographique par défaut : c'est un secret, il ne doit pas être
     *  devinable (Random.Default ne l'est pas). */
    fun generate(random: Random = SecureRandom().asKotlinRandom()): String =
        (1..DIGITS).joinToString("") { random.nextInt(10).toString() }

    /** "1234567890123456" → "1234 5678 9012 3456" (plus facile à recopier). */
    fun format(code: String): String = code.chunked(4).joinToString(" ")

    /** Ne garde que les chiffres : le joueur peut coller son code avec des
     *  espaces, des tirets, ou rien du tout. */
    fun normalize(input: String): String = input.filter { it.isDigit() }

    /** Un code n'est utilisable que s'il fait exactement [DIGITS] chiffres. */
    fun isValid(code: String): Boolean = code.length == DIGITS && code.all { it.isDigit() }

    /** L'adresse e-mail technique du compte Firebase désigné par ce code. */
    fun emailFor(code: String): String = "c$code@$EMAIL_DOMAIN"

    /**
     * L'inverse de [emailFor] : le code d'un compte à partir de son e-mail
     * Firebase (`codeFromEmail()` côté site), "" si l'e-mail ne vient pas
     * d'un code (compte invité, ancien compte créé avec un vrai e-mail).
     *
     * C'est la SEULE source du code affiché au joueur : par construction,
     * c'est celui qui ouvre vraiment son compte. (Avant la 11.3.0, le code
     * était une copie gardée dans la sauvegarde, qui pouvait ne plus
     * correspondre à rien.)
     */
    fun codeFromEmail(email: String?): String {
        val e = email.orEmpty().lowercase()
        val suffix = "@$EMAIL_DOMAIN"
        if (!e.startsWith("c") || !e.endsWith(suffix)) return ""
        val code = e.substring(1, e.length - suffix.length)
        return if (isValid(code)) code else ""
    }

    /**
     * Le mot de passe technique. Il n'ajoute aucun secret (il se déduit du
     * code) : c'est le code lui-même qui protège le compte, comme côté web.
     */
    fun passwordFor(code: String): String = "bt-$code"
}
