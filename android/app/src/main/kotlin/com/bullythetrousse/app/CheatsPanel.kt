@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bullythetrousse.app

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bullythetrousse.core.Cheats
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SaveCodec
import com.bullythetrousse.core.Skins
import com.bullythetrousse.core.Trails
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * `window.cheats` côté site : le site les range dans la console du
 * navigateur, invisible pour un joueur qui ne va pas la fouiller. Un
 * téléphone n'a pas de console : ici elles s'ouvrent d'un appui long sur le
 * numéro de version du menu, tout aussi discret.
 *
 * Comme sur le site, chaque utilisation envoie une notification (ntfy.sh)
 * avec le pseudo du joueur — best-effort, jamais bloquant.
 */
@Composable
internal fun CheatsPanel(
    save: GameSave,
    onSaveChange: (GameSave) -> Unit,
    onGoTo: (Screen) -> Unit,
    onMenuDialog: (MenuDialog) -> Unit,
    onCoinPopup: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val notOnBeach = tr("app.notOnBeach")
    var number by remember { mutableStateOf("") }
    var number2 by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf<String?>(null) }

    /** Exécute une triche en la signalant, puis ferme le panneau si demandé. */
    fun run(name: String, args: List<String> = emptyList(), close: Boolean = false, action: () -> Unit) {
        notifyCheatUsed(save.pseudo, name, args)
        action()
        if (close) onDismiss()
    }

    fun unknown(kind: String, valid: List<String>) = toaster("$kind inconnu(e). IDs valides : ${valid.joinToString(", ")}")

    InfoDialog(title = "🎮 cheats", onDismiss = onDismiss) {
        CheatSection("Économie / progression")
        CheatField("n / puissance", number) { number = it }
        CheatField("vitesse", number2) { number2 = it }
        CheatField("id (skin, traînée)", id, numeric = false) { id = it }
        CheatButtons {
            GameButton("addMoney(n)", small = true) {
                val n = number.toIntOrNull() ?: return@GameButton
                run("addMoney", listOf(n.toString())) { onSaveChange(Cheats.addMoney(save, n)) }
            }
            GameButton("setLevels(p, v)", small = true) {
                run("setLevels", listOf(number, number2)) {
                    onSaveChange(Cheats.setLevels(save, number.toIntOrNull(), number2.toIntOrNull()))
                }
            }
            GameButton("setDurability(n)", small = true) {
                val n = number.toIntOrNull() ?: return@GameButton
                run("setDurability", listOf(n.toString())) { onSaveChange(Cheats.setDurability(save, n)) }
            }
            GameButton("unlockSkin(id)", small = true) {
                run("unlockSkin", listOf(id)) {
                    Cheats.unlockSkin(save, id.trim())?.let(onSaveChange) ?: unknown("Skin", Skins.ALL.map { it.id })
                }
            }
            GameButton("unlockAllSkins()", small = true) { run("unlockAllSkins") { onSaveChange(Cheats.unlockAllSkins(save)) } }
            GameButton("unlockTrail(id)", small = true) {
                run("unlockTrail", listOf(id)) {
                    Cheats.unlockTrail(save, id.trim())?.let(onSaveChange) ?: unknown("Traînée", Trails.ALL.map { it.id })
                }
            }
            GameButton("resetChallenges()", small = true) {
                run("resetChallenges") { onSaveChange(Cheats.resetChallenges(save, LocalDate.now().toString())) }
            }
        }

        CheatSection("Évènements / popups")
        CheatButtons {
            GameButton("coinBonus(n)", small = true) {
                val m = number.toDoubleOrNull() ?: 2.0
                run("coinBonus", listOf(m.toString()), close = true) { onCoinPopup(m) }
            }
            GameButton("milestonePopup(m)", small = true) {
                val m = number.toIntOrNull() ?: 100
                run("milestonePopup", listOf(m.toString())) {
                    toaster("🎉 Palier des ${m}m atteint ! +${50 * maxOf(1, Math.round(m / 100.0).toInt())}$")
                }
            }
            GameButton("trousseInfo()", small = true) { run("trousseInfo", close = true) { onMenuDialog(MenuDialog.TrousseInfo) } }
            GameButton("aidePopup()", small = true) { run("aidePopup", close = true) { onMenuDialog(MenuDialog.Help) } }
            GameButton("repairBrokenPopup()", small = true) { run("repairBrokenPopup", close = true) { onMenuDialog(MenuDialog.RepairBroken) } }
            GameButton("volcanReplayPopup()", small = true) { run("volcanReplayPopup", close = true) { onMenuDialog(MenuDialog.VolcanReplay) } }
            GameButton("changelogPopup()", small = true) { run("changelogPopup", close = true) { onGoTo(Screen.Changelog) } }
            GameButton("cinematique()", small = true) {
                run("cinematique", close = true) {
                    onSaveChange(Cheats.clearVolcanoCooldown(save))
                    onGoTo(Screen.VolcanoCinematic)
                }
            }
            GameButton("cinematiquePlage()", small = true) { run("cinematiquePlage", close = true) { onGoTo(Screen.BeachCinematic) } }
            GameButton("quakeSound()", small = true) { run("quakeSound") { playRaw(context, R.raw.sfx_quake) } }
        }

        CheatSection("Monde / thème")
        CheatButtons {
            GameButton("resetVolcan()", small = true) { run("resetVolcan") { onSaveChange(Cheats.resetVolcan(save)) } }
            GameButton("resetPlage()", small = true) { run("resetPlage") { onSaveChange(Cheats.resetPlage(save)) } }
            GameButton("goPlage()", small = true) { run("goPlage") { onSaveChange(Cheats.goPlage(save)) } }
            GameButton("retourBus()", small = true) {
                run("retourBus") { Cheats.retourBus(save)?.let(onSaveChange) ?: toaster(notOnBeach) }
            }
            GameButton("toggleTheme()", small = true) { run("toggleTheme") { onSaveChange(Cheats.toggleTheme(save)) } }
        }

        CheatSection("Divers")
        CheatButtons {
            GameButton("show()", small = true) { run("show") { shown = SaveCodec.encode(save) } }
            GameButton("resetSave()", small = true) {
                run("resetSave", close = true) { onSaveChange(GameSave()) }
            }
        }
        shown?.let { Text(it, color = TextDim, fontSize = 10.sp) }
    }
}

@Composable
private fun CheatSection(title: String) {
    Text(title, color = Money, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CheatButtons(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRowCentered(gap = 6.dp, content = content)
}

@Composable
private fun CheatField(label: String, value: String, numeric: Boolean = true, onChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label, fontSize = 11.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Joue un son brut une fois, puis libère le lecteur. */
private fun playRaw(context: Context, rawId: Int) {
    runCatching {
        MediaPlayer.create(context, rawId)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}

/** `notifyCheatUsed()` : best-effort, sur un fil à part, jamais bloquant. */
private fun notifyCheatUsed(pseudo: String, name: String, args: List<String>) {
    val message = Cheats.notifyMessage(pseudo, name, args)
    thread(isDaemon = true) {
        runCatching {
            val connection = URL("https://ntfy.sh/${Cheats.NOTIFY_TOPIC}").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.outputStream.use { it.write(message.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
            connection.disconnect()
        }
    }
}
