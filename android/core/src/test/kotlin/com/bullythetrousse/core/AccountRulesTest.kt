package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRulesTest {
    @Test
    fun `un compte quitte est efface seulement si personne ne peut y revenir`() {
        assertTrue(AccountRules.isUnreachable(isAnonymous = true, hasCode = false, codeShown = false))
        assertTrue(AccountRules.isUnreachable(isAnonymous = false, hasCode = true, codeShown = false))
        assertFalse(AccountRules.isUnreachable(isAnonymous = false, hasCode = true, codeShown = true))
        // Ancien compte à vrai e-mail : son propriétaire connaît ses identifiants.
        assertFalse(AccountRules.isUnreachable(isAnonymous = false, hasCode = false, codeShown = false))
    }

    @Test
    fun `une partie neuve n a pas de progression`() {
        assertFalse(AccountRules.hasProgress(GameSave()))
        assertTrue(AccountRules.hasProgress(GameSave(totalThrows = 1)))
        assertTrue(AccountRules.hasProgress(GameSave(money = 5)))
    }

    @Test
    fun `garder la partie de l appareil conserve le pseudo arc-en-ciel du compte`() {
        val kept = AccountRules.keepLocal(GameSave(money = 3), GameSave(playedOnAndroid = true))
        assertTrue(kept.playedOnAndroid)
        assertTrue(kept.accountCodeShown)
        assertEquals(3, kept.money)
    }

    @Test
    fun `les nouveaux champs du compte ont les valeurs par defaut du site`() {
        val fresh = SaveCodec.fromFieldMap(emptyMap())
        assertFalse(fresh.accountCodeShown)
        assertFalse(fresh.playedOnAndroid)
        val fromWeb = SaveCodec.fromFieldMap(mapOf("accountCodeShown" to true, "playedOnAndroid" to true))
        assertTrue(fromWeb.accountCodeShown)
        assertTrue(fromWeb.playedOnAndroid)
    }
}
