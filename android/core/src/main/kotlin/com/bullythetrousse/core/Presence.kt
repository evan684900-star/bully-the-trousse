package com.bullythetrousse.core

/** Depuis quand un joueur n'a pas donné signe de vie, voir [Presence.of]. */
sealed interface PresenceStatus {
    /** Vu il y a moins de [Presence.ONLINE_THRESHOLD_MILLIS]. */
    data object Online : PresenceStatus
    data class MinutesAgo(val minutes: Int) : PresenceStatus
    data class HoursAgo(val hours: Int) : PresenceStatus
    data class DaysAgo(val days: Int) : PresenceStatus

    /** Aucun horodatage reçu : profil jamais synchronisé. On préfère dire
     *  "inconnu" plutôt que "hors ligne" à tort. */
    data object Unknown : PresenceStatus
}

/**
 * « En ligne » / « Vu il y a... », porté de `formatPresenceHtml()` côté web.
 *
 * Le site n'a pas de vraie détection de déconnexion (pas de Realtime
 * Database) : il approxime avec un battement régulier qui met à jour
 * `updatedAt` sur le profil public, et compare cet horodatage à un seuil.
 */
object Presence {
    /** `ONLINE_THRESHOLD_MS` côté web : deux minutes. */
    const val ONLINE_THRESHOLD_MILLIS = 2 * 60 * 1000L

    fun of(updatedAtMillis: Long?, nowMillis: Long): PresenceStatus {
        if (updatedAtMillis == null) return PresenceStatus.Unknown
        val diff = nowMillis - updatedAtMillis
        if (diff <= ONLINE_THRESHOLD_MILLIS) return PresenceStatus.Online
        val minutes = (diff / 60_000L).toInt()
        if (minutes < 60) return PresenceStatus.MinutesAgo(minutes)
        val hours = minutes / 60
        if (hours < 24) return PresenceStatus.HoursAgo(hours)
        return PresenceStatus.DaysAgo(hours / 24)
    }
}
