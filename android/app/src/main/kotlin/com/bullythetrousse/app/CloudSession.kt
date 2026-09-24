package com.bullythetrousse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bullythetrousse.core.CloudSaveSync
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.IncomingGift
import com.bullythetrousse.core.MergeChoice
import com.bullythetrousse.core.Pseudo
import com.bullythetrousse.core.RecoveryCode
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Ce que l'app sait de sa connexion au compte en ligne, pour l'afficher
 *  (`#online-status`/`#account-status` côté site) et pour savoir quoi
 *  proposer au joueur. */
enum class CloudState {
    /** `app/google-services.json` absent : tout le volet en ligne est coupé. */
    NOT_CONFIGURED,
    CONNECTING,
    /** Connecté à un compte invité : la partie est sauvegardée en ligne,
     *  mais elle disparaîtrait avec l'app tant qu'aucun code ne la rattache. */
    GUEST,
    /** Connecté au compte d'un code : la même partie que sur le site. */
    LINKED,
    /** Réseau coupé, Firebase indisponible, ou tentative échouée : le jeu
     *  continue en local ; [rememberCloudSession] réessaie tout seul avant
     *  de laisser ici la main au joueur (voir [CloudSession.retryConnection]). */
    OFFLINE,
}

/**
 * La session en ligne : compte, sauvegarde cloud, classement.
 *
 * Tenue par [rememberCloudSession] et lue par les écrans. Rien ici n'est
 * bloquant : si Firebase n'est pas configuré, si le réseau est coupé ou si
 * une requête échoue, l'état bascule simplement en [CloudState.OFFLINE] et
 * le jeu continue exactement comme avant — c'est aussi ce que fait le site.
 *
 * Une tentative de connexion peut échouer pour une raison purement
 * transitoire (le réseau pas encore prêt au tout premier lancement, un
 * DNS lent...) : rester bloqué en OFFLINE pour le reste de la session
 * serait une régression par rapport au site, qui retente sans même que le
 * joueur s'en rende compte. [retryTrigger] est ce qui permet à
 * [rememberCloudSession] de relancer la connexion, automatiquement au
 * démarrage (voir [AUTO_RETRY_DELAYS_MS]) et à la demande depuis l'écran
 * Compte.
 */
class CloudSession(val bridge: FirebaseBridge, private val scope: CoroutineScope) {
    var state by mutableStateOf(
        if (bridge.isAvailable) CloudState.CONNECTING else CloudState.NOT_CONFIGURED,
    )
        internal set

    var uid by mutableStateOf<String?>(null)
        internal set

    internal var retryTrigger by mutableIntStateOf(0)

    /**
     * Lance [block] pour la durée de vie de l'app, pas celle d'un écran.
     *
     * Pour les écritures qui ne doivent pas être abandonnées à mi-chemin : un
     * cadeau, par exemple, part vers Firestore même si le joueur ferme la
     * fenêtre, et l'annuler de notre côté ferait rembourser un cadeau bel et
     * bien envoyé.
     */
    fun launchDetached(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    /** Heure du dernier cadeau envoyé (`lastGiftSentAt` côté site) : le délai
     *  de [Gifts.COOLDOWN_MS] vaut pour toute la session, pas par écran. */
    var lastGiftSentAtMillis: Long = 0L

    /** Reçoit les cadeaux crédités : tous ceux en attente à la connexion
     *  (`atLogin` = vrai, affichés dans une modale), puis un par un en temps
     *  réel (toast). Branché par [rememberCloudSession]. */
    internal var giftSink: ((gifts: List<IncomingGift>, atLogin: Boolean) -> Unit)? = null

    private var giftsListener: ListenerRegistration? = null

    /**
     * `checkIncomingGifts()` puis `startGiftsListener()`, dans cet ordre : le
     * ramassage marque d'abord « vus » les cadeaux en attente, pour que
     * l'écoute ne les crédite pas une deuxième fois.
     */
    internal suspend fun startGifts(uid: String) {
        stopGifts()
        val pending = runCatching { bridge.collectIncomingGifts(uid) }.getOrDefault(emptyList())
        if (pending.isNotEmpty()) giftSink?.invoke(pending, true)
        giftsListener = bridge.listenIncomingGifts(uid) { gift -> giftSink?.invoke(listOf(gift), false) }
    }

    internal fun stopGifts() {
        giftsListener?.remove()
        giftsListener = null
    }

    /**
     * `pushAchvUnlock()` pour chaque succès qui vient de tomber : alimente les
     * pourcentages de la liste des succès. Best-effort, en arrière-plan — un
     * échec réseau ne doit ni bloquer ni retarder la partie.
     */
    fun publishUnlocks(achievementIds: List<String>, totalThrows: Int) {
        val id = uid ?: return
        if (achievementIds.isEmpty()) return
        scope.launch {
            for (achievementId in achievementIds) {
                runCatching { bridge.pushAchvUnlock(id, achievementId, totalThrows) }
            }
        }
    }

    /** `syncAchievementStats()` : à chaque connexion, renvoie tous les succès
     *  déjà obtenus, au cas où ce compte date d'avant ce système. */
    internal suspend fun syncAchievementStats(save: GameSave) {
        val id = uid ?: return
        runCatching { bridge.markPlayerActive(id, save.totalThrows) }
        for (achievementId in save.unlockedAchievements) {
            runCatching { bridge.pushAchvUnlock(id, achievementId, save.totalThrows) }
        }
    }

    /** Redemande une connexion (nouvelle tentative automatique épuisée, ou
     *  bouton "Réessayer" de l'écran Compte). Sans effet si déjà connecté. */
    fun retryConnection() {
        if (state == CloudState.GUEST || state == CloudState.LINKED) return
        if (!bridge.isAvailable) return
        state = CloudState.CONNECTING
        retryTrigger++
    }

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

    /** `lookupAccountByCode()` : ce que désigne ce code, sans quitter le compte actuel. */
    suspend fun lookupCode(code: String): CodeLookup? = bridge.lookupCode(code)

    /**
     * `joinAccount(code, choice)` : connecte cet appareil au compte du code.
     *
     * [choice] vient de la question « quelle partie garder ? » : avec
     * [MergeChoice.OTHER], la partie du compte remplace celle du téléphone ;
     * avec [MergeChoice.CURRENT], c'est la partie du téléphone qui écrase
     * celle du compte. Jamais de mélange champ par champ : l'une OU l'autre,
     * en entier.
     *
     * Renvoie `null` si tout s'est bien passé, sinon la clé du message à
     * montrer au joueur.
     */
    suspend fun joinAccount(
        code: String,
        choice: MergeChoice,
        repository: SaveRepository,
        currentSave: GameSave,
        onSaveChange: (GameSave) -> Unit,
    ): String? {
        if (!bridge.isAvailable) return "recoveryErrOffline"

        // Le compte invité de cet appareil ne sert plus à rien une fois qu'on
        // bascule sur un vrai compte : le nettoyer évite un pseudo fantôme au
        // classement (cleanupAbandonedAnonymousAccount() côté site).
        if (bridge.isAnonymous) {
            uid?.let { oldUid ->
                runCatching { bridge.cleanupAbandonedAnonymousAccount(oldUid, currentSave.unlockedAchievements) }
            }
        }

        stopGifts()
        val joined = try {
            bridge.signInWithCode(code)
        } catch (e: Exception) {
            null
        } ?: return "recoveryErrWrongCode"

        uid = joined
        state = CloudState.LINKED
        scope.launch { startGifts(joined) }
        return try {
            val cloud = bridge.fetchCloudSave(joined)
            if (choice == MergeChoice.OTHER && cloud != null) {
                val restored = cloud.save.copy(recoveryCode = code)
                repository.saveFromCloud(restored, cloud.updatedAtMillis)
                onSaveChange(restored)
            } else {
                // Partie du téléphone gardée (ou compte sans partie) : elle
                // devient celle du compte.
                val adopted = currentSave.copy(recoveryCode = code)
                onSaveChange(adopted)
                bridge.pushCloudSave(joined, adopted, sessionId)
                pushBothScores(joined, adopted)
            }
            null
        } catch (e: Exception) {
            "leaderboardError"
        }
    }

    /**
     * `restoreLegacyCode()` : un ancien code ne désigne pas un compte mais une
     * COPIE de partie. On retire l'entrée de classement de l'ancien compte,
     * on garde la partie choisie, puis on rattache le compte de cet appareil
     * à ce code, pour que les prochains appareils s'y connectent vraiment.
     */
    suspend fun restoreLegacy(
        code: String,
        lookup: CodeLookup.Legacy,
        choice: MergeChoice,
        currentSave: GameSave,
        onSaveChange: (GameSave) -> Unit,
    ): String? {
        if (!bridge.isAvailable) return "recoveryErrOffline"
        return try {
            bridge.retireOldLeaderboardEntry(lookup.oldUid, lookup.retireToken, lookup.save.pseudo)
            val chosen = if (choice == MergeChoice.OTHER) lookup.save else currentSave
            val restored = chosen.copy(
                recoveryCode = code,
                recoveryRetireToken = chosen.recoveryRetireToken.ifBlank { RecoveryCode.generate() },
            )
            onSaveChange(restored)
            uid?.let { id ->
                // Les deux classements tout de suite, sans attendre un record.
                pushBothScores(id, restored)
                runCatching { bridge.pushRecoverySnapshot(id, restored) }
            }
            if (bridge.linkToCode(code)) state = CloudState.LINKED
            null
        } catch (e: Exception) {
            "recoveryErrWrongCode"
        }
    }

    private suspend fun pushBothScores(id: String, save: GameSave) {
        runCatching { bridge.pushScore("scores", id, save.pseudo, save.bestDistance) }
        runCatching { bridge.pushScore("scoresPlage", id, save.pseudo, save.plageBestDistance) }
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

    /**
     * `btn-logout` côté site : repart de zéro sur CET appareil avec un
     * compte tout neuf. Un compte lié (un code existe) n'est jamais détruit
     * — seul l'appareil oublie la partie, elle reste accessible par son code
     * — un compte purement invité, lui, n'a aucun moyen d'y revenir, donc
     * son entrée de classement et son profil partent avec lui (même geste
     * que [joinAccount]).
     *
     * Renvoie la sauvegarde neuve à appliquer localement.
     */
    suspend fun logout(currentSave: GameSave): GameSave {
        if (bridge.isAvailable && bridge.isAnonymous) {
            uid?.let { id ->
                runCatching { bridge.cleanupAbandonedAnonymousAccount(id, currentSave.unlockedAchievements) }
            }
        }
        // Les cadeaux de l'ancien compte ne doivent plus être crédités ici.
        stopGifts()
        bridge.signOut()
        uid = null
        state = if (bridge.isAvailable) CloudState.CONNECTING else CloudState.NOT_CONFIGURED
        retryTrigger++ // relance une connexion (nouveau compte invité), voir rememberCloudSession
        return GameSave()
    }

    /** La clé de traduction de l'état du compte (`#account-status` côté site). */
    val statusKey: String
        get() = when (state) {
            CloudState.NOT_CONFIGURED -> "onlineOfflineNoConfig"
            CloudState.CONNECTING -> "onlineConnecting"
            CloudState.GUEST -> "accountStatusLocal"
            CloudState.LINKED -> "accountStatusLinked"
            CloudState.OFFLINE -> "accountStatusOffline"
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
 * Si cette première tentative échoue (réseau pas encore prêt au tout
 * premier lancement, par exemple), quelques nouvelles tentatives
 * automatiques suivent ([AUTO_RETRY_DELAYS_MS]) avant de laisser le joueur
 * réessayer lui-même depuis l'écran Compte — rester bloqué en OFFLINE pour
 * le reste de la session serait une régression par rapport au site.
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
    onGifts: (gifts: List<IncomingGift>, atLogin: Boolean) -> Unit,
): CloudSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { CloudSession(FirebaseBridge(context), scope) }
    val currentOnGifts by rememberUpdatedState(onGifts)
    DisposableEffect(session) {
        session.giftSink = { gifts, atLogin -> currentOnGifts(gifts, atLogin) }
        onDispose {
            session.giftSink = null
            session.stopGifts()
        }
    }

    LaunchedEffect(session.retryTrigger) {
        if (!session.bridge.isAvailable) return@LaunchedEffect
        var attempt = 0
        while (true) {
            try {
                val code = save.recoveryCode
                // Un code déjà présent dans la sauvegarde désigne un vrai
                // compte : on le rejoint plutôt que d'en créer un nouveau,
                // sinon le joueur se retrouverait avec deux entrées au
                // classement.
                val signedIn = if (RecoveryCode.isValid(code)) {
                    runCatching { session.bridge.signInWithCode(code) }.getOrNull()
                        ?: session.bridge.signInAnonymously()
                } else {
                    session.bridge.signInAnonymously()
                }
                session.uid = signedIn
                if (signedIn == null) throw IllegalStateException("Connexion Firebase sans utilisateur")

                val cloud = session.bridge.fetchCloudSave(signedIn)
                if (cloud != null &&
                    CloudSaveSync.shouldApplyRemoteSave(repository.lastPersistAtMillis, cloud.updatedAtMillis)
                ) {
                    repository.saveFromCloud(cloud.save, cloud.updatedAtMillis)
                    onSaveChange(cloud.save)
                }
                // Jamais de pseudo vide au classement : le site en attribue un
                // à la première connexion (`if (!save.pseudo) save.pseudo =
                // "Trousse-" + ...`), sans quoi l'entrée s'affiche "Anonyme"
                // pour tout le monde.
                if (save.pseudo.isBlank()) onSaveChange(save.copy(pseudo = Pseudo.generateDefault()))

                session.state = if (session.bridge.isAnonymous) CloudState.GUEST else CloudState.LINKED
                // Rattrape les succès obtenus hors ligne ou avant ce système.
                session.syncAchievementStats(save)
                // Cadeaux reçus pendant l'absence, puis écoute temps réel.
                session.startGifts(signedIn)
                return@LaunchedEffect
            } catch (e: Exception) {
                // Réseau coupé, règles Firestore, authentification désactivée
                // dans la console : aucune de ces situations ne doit
                // empêcher de jouer. On retente quelques fois tout seul
                // avant de laisser la main (voir AUTO_RETRY_DELAYS_MS) —
                // sans ça, un simple faux départ réseau au premier lancement
                // laisserait le joueur "hors ligne" pour toute la session.
                session.state = CloudState.OFFLINE
                if (attempt >= AUTO_RETRY_DELAYS_MS.size) return@LaunchedEffect
                delay(AUTO_RETRY_DELAYS_MS[attempt])
                attempt++
                session.state = CloudState.CONNECTING
            }
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

/** Délais entre les tentatives de connexion automatiques après un premier
 *  échec (2 s, 5 s, 12 s) : assez vite pour rattraper un réseau qui vient
 *  tout juste de répondre, sans marteler Firebase si le problème persiste. */
private val AUTO_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 12_000L)
