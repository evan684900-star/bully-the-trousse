package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bullythetrousse.core.CloudSaveSync
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.RecoveryCode
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Ce que l'app sait de sa connexion au compte en ligne, pour l'afficher
 *  (`#online-status` côté site) et pour savoir quoi proposer au joueur. */
enum class CloudState {
    /** `app/google-services.json` absent : tout le volet en ligne est coupé. */
    NOT_CONFIGURED,
    CONNECTING,
    /** Connecté à un compte invité : la partie est sauvegardée en ligne,
     *  mais elle disparaîtrait avec l'app tant qu'aucun code ne la rattache. */
    GUEST,
    /** Connecté au compte d'un code : la même partie que sur le site. */
    LINKED,
    /** Réseau coupé ou Firebase indisponible : le jeu continue en local. */
    OFFLINE,
}

/**
 * La session en ligne : compte, sauvegarde cloud, classement.
 *
 * Tenue par [rememberCloudSession] et lue par les écrans. Rien ici n'est
 * bloquant : si Firebase n'est pas configuré, si le réseau est coupé ou si
 * une requête échoue, l'état bascule simplement en [CloudState.OFFLINE] et
 * le jeu continue exactement comme avant — c'est aussi ce que fait le site.
 */
class CloudSession(val bridge: FirebaseBridge) {
    var state by mutableStateOf(
        if (bridge.isAvailable) CloudState.CONNECTING else CloudState.NOT_CONFIGURED,
    )
        internal set

    var uid by mutableStateOf<String?>(null)
        internal set

    /**
     * Identifie CET appareil dans le document partagé (`SESSION_ID` côté
     * site) : l'écoute temps réel du site s'en sert pour distinguer « c'est
     * moi qui viens d'écrire » de « un autre appareil a écrit ».
     */
    val sessionId: String = buildString {
        append(Random.nextLong(0, Long.MAX_VALUE).toString(36))
        append('-')
        append(System.currentTimeMillis().toString(36))
    }

    /**
     * Rejoint le compte désigné par [code] : la partie, le profil et
     * l'entrée de classement deviennent ceux de ce compte, sur cet appareil
     * comme sur le site.
     *
     * La sauvegarde du compte l'emporte sur celle du téléphone : c'est le
     * sens de la démarche (« je veux retrouver MA partie ici »), et le site
     * fait le même choix quand le joueur ne demande pas explicitement
     * l'inverse. Renvoie `null` si tout s'est bien passé, sinon le message
     * à montrer au joueur.
     */
    suspend fun joinAccount(
        code: String,
        repository: SaveRepository,
        currentSave: GameSave,
        onSaveChange: (GameSave) -> Unit,
    ): String? {
        if (!bridge.isAvailable) return "Le compte en ligne n'est pas configuré dans cette version."
        if (!RecoveryCode.isValid(code)) return "Un code fait 16 chiffres."
        val joined = try {
            bridge.signInWithCode(code)
        } catch (e: Exception) {
            null
        } ?: return "Aucun compte ne correspond à ce code."

        uid = joined
        state = CloudState.LINKED
        return try {
            val cloud = bridge.fetchCloudSave(joined)
            if (cloud != null) {
                val restored = cloud.save.copy(recoveryCode = code)
                repository.saveFromCloud(restored, cloud.updatedAtMillis)
                onSaveChange(restored)
            } else {
                // Compte existant mais sans sauvegarde (cas rare) : on y
                // installe la partie de cet appareil plutôt que de repartir
                // de zéro.
                val adopted = currentSave.copy(recoveryCode = code)
                onSaveChange(adopted)
                bridge.pushCloudSave(joined, adopted, sessionId)
            }
            null
        } catch (e: Exception) {
            "Connecté, mais la partie du compte n'a pas pu être lue. Réessaie plus tard."
        }
    }

    /**
     * Le code de ce joueur, créé à la première demande — il ne sert à rien
     * tant qu'il n'a pas été montré, donc inutile d'en générer un pour
     * quelqu'un qui n'ouvre jamais cet écran.
     *
     * Le créer rattache aussi le compte invité de cet appareil à ce code, en
     * **gardant le même identifiant** : la partie en cours et la place au
     * classement sont conservées.
     */
    suspend fun revealOrCreateCode(
        currentSave: GameSave,
        onSaveChange: (GameSave) -> Unit,
    ): String {
        val existing = currentSave.recoveryCode
        if (RecoveryCode.isValid(existing)) {
            if (bridge.isAnonymous && bridge.linkToCode(existing)) state = CloudState.LINKED
            return existing
        }
        val code = RecoveryCode.generate()
        val updated = currentSave.copy(
            recoveryCode = code,
            recoveryRetireToken = RecoveryCode.generate(),
        )
        onSaveChange(updated)
        if (bridge.linkToCode(code)) {
            state = CloudState.LINKED
            uid?.let { id ->
                runCatching { bridge.pushRecoverySnapshot(id, updated) }
            }
        }
        return code
    }

    val statusText: String
        get() = when (state) {
            CloudState.NOT_CONFIGURED -> "Hors ligne : compte en ligne non configuré"
            CloudState.CONNECTING -> "Connexion…"
            CloudState.GUEST -> "En ligne (compte invité)"
            CloudState.LINKED -> "En ligne"
            CloudState.OFFLINE -> "Hors ligne : le jeu reste jouable"
        }
}

/**
 * Ouvre la session en ligne et la tient à jour.
 *
 * Au démarrage : connexion (au compte du code si la sauvegarde en porte un,
 * sinon un compte invité), puis lecture de la sauvegarde du cloud. Celle-ci
 * ne remplace la partie locale que si elle est PLUS RÉCENTE que la dernière
 * écriture de cet appareil (voir `CloudSaveSync`, porté et testé dans
 * `:core`) — sans quoi ouvrir l'app après une partie hors ligne effacerait
 * ce qui vient d'être joué.
 *
 * Ensuite : chaque changement de la sauvegarde est renvoyé au cloud, mais
 * seulement une fois le calme revenu ([PUSH_DEBOUNCE_MS]). Un lancer fait
 * bouger la sauvegarde plusieurs fois par seconde ; écrire à chaque fois
 * ferait autant d'écritures Firestore pour un seul résultat utile.
 */
@Composable
fun rememberCloudSession(
    save: GameSave,
    repository: SaveRepository,
    onSaveChange: (GameSave) -> Unit,
): CloudSession {
    val context = LocalContext.current
    val session = remember { CloudSession(FirebaseBridge(context)) }

    LaunchedEffect(Unit) {
        if (!session.bridge.isAvailable) return@LaunchedEffect
        try {
            val code = save.recoveryCode
            // Un code déjà présent dans la sauvegarde désigne un vrai compte :
            // on le rejoint plutôt que d'en créer un nouveau, sinon le joueur
            // se retrouverait avec deux entrées au classement.
            val signedIn = if (RecoveryCode.isValid(code)) {
                runCatching { session.bridge.signInWithCode(code) }.getOrNull()
                    ?: session.bridge.signInAnonymously()
            } else {
                session.bridge.signInAnonymously()
            }
            session.uid = signedIn
            if (signedIn == null) {
                session.state = CloudState.OFFLINE
                return@LaunchedEffect
            }

            val cloud = session.bridge.fetchCloudSave(signedIn)
            if (cloud != null &&
                CloudSaveSync.shouldApplyRemoteSave(repository.lastPersistAtMillis, cloud.updatedAtMillis)
            ) {
                repository.saveFromCloud(cloud.save, cloud.updatedAtMillis)
                onSaveChange(cloud.save)
            }
            session.state = if (session.bridge.isAnonymous) CloudState.GUEST else CloudState.LINKED
        } catch (e: Exception) {
            // Réseau coupé, règles Firestore, authentification désactivée dans
            // la console : aucune de ces situations ne doit empêcher de jouer.
            session.state = CloudState.OFFLINE
        }
    }

    // Renvoi de la sauvegarde vers le cloud, une fois les changements calmés.
    LaunchedEffect(save, session.uid) {
        val id = session.uid ?: return@LaunchedEffect
        if (session.state == CloudState.OFFLINE) return@LaunchedEffect
        delay(PUSH_DEBOUNCE_MS)
        try {
            session.bridge.pushCloudSave(id, save, session.sessionId)
            session.bridge.pushPublicProfile(id, save)
            session.bridge.pushRecoverySnapshot(id, save)
            val collection = session.bridge.scoresCollectionFor(save)
            val best = if (save.inPlage) save.plageBestDistance else save.bestDistance
            session.bridge.pushScore(collection, id, save.pseudo, best)
        } catch (e: Exception) {
            // Best-effort, exactement comme côté site : on réessaiera au
            // prochain changement de la sauvegarde.
        }
    }

    return session
}

/** Le site écrit à chaque `persist()` ; ici on attend que la sauvegarde
 *  arrête de bouger, pour ne pas écrire dix fois pendant un seul lancer. */
private const val PUSH_DEBOUNCE_MS = 1500L
