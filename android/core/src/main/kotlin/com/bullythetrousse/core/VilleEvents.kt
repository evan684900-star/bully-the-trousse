package com.bullythetrousse.core

/** Les 4 évènements du monde Ville (`EVENTS` dans ville.js), dans le même ordre. */
enum class VilleEventType(val id: String, val emoji: String, val label: String, val effect: String) {
    COUPURE(
        "coupure", "⚡", "Coupure de courant",
        "Boutique et ascenseur hors service : il faudra prendre les escaliers.",
    ),
    FUITE(
        "fuite", "💧", "Fuite d'eau",
        "Des flaques sur le toit : atterris dedans pour glisser 3 s puis repartir plus vite.",
    ),
    PLUIE("pluie", "🌧️", "Pluie", "Comme la fuite d'eau, mais avec bien plus de flaques."),
    CANICULE("canicule", "🥵", "Canicule", "La trousse doit se reposer 2 minutes tous les 5 lancers."),
}

/** Un évènement du calendrier : [startMillis] inclus, [endMillis] exclu. */
data class VilleEvent(val type: VilleEventType, val startMillis: Long, val endMillis: Long) {
    fun isActiveAt(nowMillis: Long): Boolean = nowMillis in startMillis until endMillis
}

/**
 * Le calendrier des évènements de la Ville, portage de `eventsForDay()`/
 * `activeEvent()`/`upcomingEvents()` (ville.js).
 *
 * Chaque jour UTC tire, à partir de son SEUL numéro, 5 à 9 évènements de 2
 * à 20 minutes : tous les joueurs voient exactement le même calendrier,
 * sans serveur. C'est pourquoi ce portage reproduit le générateur du site
 * (mulberry32) à l'entier près — la moindre différence, et le téléphone
 * annoncerait une pluie que le site ne voit pas (voir VilleEventsTest, dont
 * les valeurs attendues viennent du JS du site).
 */
object VilleEvents {
    const val DAY_MILLIS = 86_400_000L
    private const val MINUTE_MILLIS = 60_000L

    /** mulberry32, avec l'arithmétique 32 bits de Math.imul / `| 0` / `>>> 0`. */
    fun mulberry32(seed: Int): () -> Double {
        var a = seed
        return {
            a += 0x6D2B79F5
            var t = (a xor (a ushr 15)) * (1 or a)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL) / 4294967296.0
        }
    }

    /** `(((d * 2654435761) >>> 0) ^ 0x9e3779b9) >>> 0` : la graine du jour [day]. */
    fun seedForDay(day: Long): Int {
        val product = (day * 2654435761L) and 0xFFFFFFFFL
        return (product xor 0x9e3779b9L).toInt()
    }

    private val cache = HashMap<Long, List<VilleEvent>>()

    /** Les évènements du jour UTC [day] (numéro de jour depuis l'epoch). */
    fun eventsForDay(day: Long): List<VilleEvent> = synchronized(cache) {
        cache.getOrPut(day) { computeDay(day) }
    }

    private fun computeDay(day: Long): List<VilleEvent> {
        val rnd = mulberry32(seedForDay(day))
        val count = 5 + (rnd() * 5).toInt()
        data class Draft(val type: VilleEventType, val start: Long, val duration: Long)
        val drafts = List(count) {
            val type = VilleEventType.entries[(rnd() * VilleEventType.entries.size).toInt()]
            val duration = (2 + (rnd() * 19).toInt()) * MINUTE_MILLIS // 2 à 20 min
            val start = day * DAY_MILLIS + (rnd() * 1400).toInt() * MINUTE_MILLIS
            Draft(type, start, duration)
        }.sortedBy { it.start } // tri stable, comme Array.prototype.sort
        val out = ArrayList<VilleEvent>()
        var lastEnd = Long.MIN_VALUE / 2
        for (draft in drafts) {
            // Au moins 10 minutes de calme entre deux évènements.
            val start = maxOf(draft.start, lastEnd + 10 * MINUTE_MILLIS)
            if (start + draft.duration > (day + 1) * DAY_MILLIS) continue
            out += VilleEvent(draft.type, start, start + draft.duration)
            lastEnd = start + draft.duration
        }
        return out
    }

    /** `activeEvent(now)` : la veille compte aussi, un évènement pouvant
     *  commencer juste avant minuit (UTC). */
    fun activeEvent(nowMillis: Long): VilleEvent? {
        val day = Math.floorDiv(nowMillis, DAY_MILLIS)
        for (d in listOf(day - 1, day)) {
            eventsForDay(d).firstOrNull { it.isActiveAt(nowMillis) }?.let { return it }
        }
        return null
    }

    /** `upcomingEvents()` : l'évènement en cours et ceux des 5 prochains jours
     *  (le tableau des annonces de la réception). */
    fun upcomingEvents(nowMillis: Long): List<VilleEvent> {
        val day = Math.floorDiv(nowMillis, DAY_MILLIS)
        return (day - 1..day + 5).flatMap { d ->
            eventsForDay(d).filter { it.endMillis > nowMillis && it.startMillis < nowMillis + 5 * DAY_MILLIS }
        }
    }

    fun isOutage(nowMillis: Long): Boolean = activeEvent(nowMillis)?.type == VilleEventType.COUPURE
}
