package com.bullythetrousse.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    @Test
    fun `la table reprend le site en francais et en anglais`() {
        assertEquals("Puissance", I18n.tr("statPuissance", Lang.FR))
        assertEquals("Power", I18n.tr("statPuissance", Lang.EN))
    }

    @Test
    fun `une cle inconnue renvoie la cle elle meme`() {
        assertEquals("cleQuiNExistePas", I18n.tr("cleQuiNExistePas", Lang.EN))
    }

    @Test
    fun `les placeholders sont remplaces`() {
        assertEquals("🎁 Léa vous a envoyé 50 $", I18n.tr("giftReceivedToast", Lang.FR, "pseudo" to "Léa", "amount" to 50))
        assertEquals("🎁 Léa sent you 50$", I18n.tr("giftReceivedToast", Lang.EN, "pseudo" to "Léa", "amount" to 50))
    }

    @Test
    fun `une langue vide ou inconnue retombe sur le francais`() {
        assertEquals(Lang.FR, Lang.fromId(""))
        assertEquals(Lang.FR, Lang.fromId("de"))
        assertEquals(Lang.EN, Lang.fromId("en"))
    }

    @Test
    fun `la langue de depart suit celle du telephone`() {
        assertEquals(Lang.FR, Lang.forDeviceLanguage("fr"))
        assertEquals(Lang.FR, Lang.forDeviceLanguage("FR-ca"))
        assertEquals(Lang.EN, Lang.forDeviceLanguage("es"))
        assertEquals(Lang.EN, Lang.forDeviceLanguage(null))
    }

    @Test
    fun `chaque succes a un nom et une description dans les deux langues`() {
        for (a in Achievements.ALL) {
            for (lang in Lang.entries) {
                val name = a.name(lang)
                assertTrue(name.isNotBlank() && name != a.id, "${a.id} sans nom en $lang")
                assertTrue(a.description(lang).isNotBlank(), "${a.id} sans description en $lang")
            }
        }
    }

    @Test
    fun `les lettres des jours suivent le site`() {
        assertEquals("L", I18n.weekdayLetter(1, Lang.FR)) // lundi
        assertEquals("D", I18n.weekdayLetter(7, Lang.FR)) // dimanche
        assertEquals("S", I18n.weekdayLetter(7, Lang.EN))
        assertEquals("W", I18n.weekdayLetter(3, Lang.EN))
    }

    @Test
    fun `la table contient toutes les cles du site`() {
        assertTrue(I18nStrings.TABLE.size >= 335)
    }
}
