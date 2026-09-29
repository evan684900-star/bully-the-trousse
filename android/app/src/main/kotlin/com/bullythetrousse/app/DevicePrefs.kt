package com.bullythetrousse.app

import android.content.Context
import com.bullythetrousse.core.ThrowSpeed

/**
 * Réglages propres à CET appareil, gardés à part de la sauvegarde (qui, elle,
 * se synchronise entre appareils) : les clés locales du site
 * (`bullyTrousseAccountCode`, `bullyTrousseAccountRevoked`,
 * `bullyTrousseJoinSuggestion`, `bullyTrousseThrowSpeed`).
 */
class DevicePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("device", Context.MODE_PRIVATE)

    /**
     * Le code du compte de cet appareil, pour s'y reconnecter tout seul si la
     * session Firebase se perd. Jamais envoyé dans le cloud : un code
     * régénéré ne doit pas se propager aux autres appareils, puisque c'est
     * justement eux qu'on veut déconnecter.
     */
    var accountCode: String
        get() = prefs.getString(KEY_CODE, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_CODE, value).apply()
        }

    /** Déconnecté parce que le code a été régénéré depuis un autre appareil :
     *  l'appareil attend le nouveau code au lieu de se créer un compte neuf. */
    var accountRevoked: Boolean
        get() = prefs.getBoolean(KEY_REVOKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_REVOKED, value).apply()
        }

    /** Un ancien code déjà relié à un autre compte, proposé pour le rejoindre. */
    var joinSuggestion: String
        get() = prefs.getString(KEY_SUGGESTION, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SUGGESTION, value).apply()
        }

    /** Vitesse du bouton ⏩, gardée d'un lancer à l'autre (voir [ThrowSpeed]). */
    var throwSpeed: Int
        get() = ThrowSpeed.sanitize(prefs.getInt(KEY_SPEED, 1))
        set(value) {
            prefs.edit().putInt(KEY_SPEED, value).apply()
        }

    /** Déconnexion : l'appareil oublie tout de son compte. */
    fun clearAccount() {
        prefs.edit().remove(KEY_CODE).remove(KEY_REVOKED).remove(KEY_SUGGESTION).apply()
    }

    private companion object {
        const val KEY_CODE = "accountCode"
        const val KEY_REVOKED = "accountRevoked"
        const val KEY_SUGGESTION = "joinSuggestion"
        const val KEY_SPEED = "throwSpeed"
    }
}
