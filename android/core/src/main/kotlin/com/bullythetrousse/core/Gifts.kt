package com.bullythetrousse.core

/** Un cadeau reçu, tel que stocké dans la collection `gifts`. */
data class IncomingGift(val senderUid: String, val senderPseudo: String, val amount: Int)

/** Ce que la validation d'un envoi décide, dans l'ordre des tests du site. */
sealed interface GiftCheck {
    data class Ok(val amount: Int) : GiftCheck
    /** Montant vide, nul, négatif ou illisible (`giftInvalidAmount`). */
    data object InvalidAmount : GiftCheck
    /** Plus que l'argent disponible (`giftNotEnoughMoney`). */
    data object NotEnoughMoney : GiftCheck
    /** Encore trop tôt après le dernier envoi (`giftCooldown`). */
    data class Cooldown(val secondsLeft: Int) : GiftCheck
}

/**
 * Cadeaux d'argent entre joueurs, portage de `sendGift()`,
 * `checkIncomingGifts()` et des boutons rapides de la modale d'envoi.
 */
object Gifts {
    /**
     * Délai global entre deux envois, tous destinataires confondus : sans
     * lui, rien n'empêche un double-clic répété d'enchaîner les écritures
     * Firestore, chacune facturée.
     */
    const val COOLDOWN_MS = 30_000L

    /** Nombre maximal de cadeaux en attente ramassés à la connexion. */
    const val PENDING_LIMIT = 20L

    /**
     * Valide un envoi. [input] est le texte saisi : `Math.floor(Number(...))`
     * côté site, donc une partie décimale est simplement tronquée.
     */
    fun check(input: String, money: Int, lastSentAtMillis: Long, nowMillis: Long): GiftCheck {
        val value = input.trim().replace(',', '.').toDoubleOrNull()
        if (value == null || !value.isFinite()) return GiftCheck.InvalidAmount
        val amount = kotlin.math.floor(value).toLong()
        if (amount <= 0) return GiftCheck.InvalidAmount
        if (amount > money) return GiftCheck.NotEnoughMoney
        val since = nowMillis - lastSentAtMillis
        if (since < COOLDOWN_MS) {
            val left = ((COOLDOWN_MS - since) + 999) / 1000
            return GiftCheck.Cooldown(left.toInt())
        }
        return GiftCheck.Ok(amount.toInt())
    }

    /** Bouton `+10`/`+100`/`+1000` : ajoute au montant saisi sans dépasser l'argent disponible. */
    fun quickAdd(input: String, add: Int, money: Int): Int {
        val current = input.trim().toDoubleOrNull()?.let { kotlin.math.floor(it).toLong() }?.coerceAtLeast(0) ?: 0L
        return minOf(money.toLong(), current + add).toInt()
    }

    /** Bouton `Max` : tout l'argent disponible. */
    fun max(money: Int): Int = money.coerceAtLeast(0)

    /**
     * La modale des cadeaux reçus fusionne les dons d'un même expéditeur en
     * une seule ligne (montant cumulé), dans l'ordre de première apparition.
     * Les montants négatifs ou nuls ne comptent pas.
     */
    fun mergeBySender(gifts: List<IncomingGift>): List<Pair<String, Int>> {
        val merged = LinkedHashMap<String, Pair<String, Int>>()
        for (gift in gifts) {
            val amount = gift.amount.coerceAtLeast(0)
            val previous = merged[gift.senderUid]
            merged[gift.senderUid] = (previous?.first ?: gift.senderPseudo) to ((previous?.second ?: 0) + amount)
        }
        return merged.values.filter { it.second > 0 }
    }

    fun total(gifts: List<IncomingGift>): Int = gifts.sumOf { it.amount.coerceAtLeast(0) }
}
