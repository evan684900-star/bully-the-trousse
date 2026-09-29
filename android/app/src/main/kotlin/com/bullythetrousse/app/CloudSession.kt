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
import com.bullythetrousse.core.AccountRules
import com.bullythetrousse.core.CloudSaveSync
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.IncomingGift
import com.bullythetrousse.core.Leaderboard
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
    /** Connecté à un compte invité : il reçoit son code dès la connexion
     *  (voir [CloudSession.ensureAccountCode]) ; cet état ne dure que si
     *  cette liaison échoue. */
    GUEST,
    /** Connecté au compte d'un code (ou à un ancien compte à vrai e-mail). */
    LINKED,
    /** Réseau coupé, Firebase indisponible, ou tentative échouée : le jeu
     *  continue en local ; [rememberCloudSession] réessaie tout seul avant
     *  de laisser ici la main au joueur (voir [CloudSession.retryConnection]). */
    OFFLINE,
    /** Le code de cet appareil a été régénéré depuis un autre appareil : pas
     *  de compte neuf (la partie locale n'est qu'une copie de celle du compte,
     *  elle finirait en double au classement), on attend le nouveau code. */
    REVOKED,
}

/** Ce qu'a donné la connexion au démarrage (voir [CloudSession.connect]). */
internal sealed interface ConnectResult {
    /** [staleLegacyCode] : le code gardé dans la sauvegarde (avant la 11.3.0)
     *  n'ouvre aucun compte, il faut l'oublier. */
    data class Connected(val uid: String, val staleLegacyCode: Boolean) : ConnectResult

    /** [justNow] : on vient de s'en rendre compte (sinon, déjà su avant). */
    data class Revoked(val justNow: Boolean) : ConnectResult
}

/**
 * La session en ligne : compte, sauvegarde cloud, classement. Portage de la
 * section 12 du site (11.3.0).
 *
 * Le code EST le compte : chaque partie reçoit le sien dès la connexion
 * ([ensureAccountCode]), l'appareil le garde en local ([DevicePrefs]) pour
 * se reconnecter tout seul au même compte si la session Firebase se perd, et
 * le code affiché vient toujours de l'e-mail du compte connecté.
 *
 * Tenue par [rememberCloudSession] et lue par les écrans. Rien ici n'est
 * bloquant : si Firebase n'est pas configuré, si le réseau est coupé ou si
 * une requête échoue, l'état bascule simplement en [CloudState.OFFLINE] et
 * le jeu continue exactement comme avant — c'est aussi ce que fait le site.
 */
class CloudSession(val bridge: FirebaseBridge, private val prefs: DevicePrefs, private val scope: CoroutineScope) {
    var state by mutableStateOf(
        if (bridge.isAvailable) CloudState.CONNECTING else CloudState.NOT_CONFIGURED,
    )
        internal set

    var uid by mutableStateOf<String?>(null)
        internal set

    /** Le code du compte connecté, lu dans son e-mail ("" sans code) : le
     *  SEUL code affiché au joueur. */
    var accountCode by mutableStateOf("")
        internal set

    /**
     * Passe à vrai une fois la sauvegarde du compte chargée. Tant que c'est
     * faux, RIEN ne part vers le cloud : sinon un renvoi tombé pendant la
     * connexion enverrait la partie de cet appareil dans le compte avant même
     * qu'on ait lu la sienne, et l'écraserait (`cloudReady` côté site).
     */
    var cloudReady by mutableStateOf(false)
        internal set

    /** Changement de compte en cours (connexion à un autre, déconnexion,
     *  régénération) : la session qui se ferme ne doit pas relancer la
     *  reconnexion automatique (voir [onSignedOutUnexpectedly]). */
    internal var switching = false

    /** Le pseudo déjà publié dans les deux classements : un nouveau pseudo
     *  les republie tous les deux (voir [pushAllScores]). */
    internal var lastPushedPseudo: String? = null

    internal var retryTrigger by mutableIntStateOf(0)

    /** Un ancien code (d'avant la refonte) déjà relié à un autre compte,
     *  proposé pour rejoindre ce compte (voir [ensureAccountCode]). */
    val joinSuggestion: String get() = prefs.joinSuggestion

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

    /** Reçoit une partie arrivée d'un autre appareil pendant la session
     *  (voir [startSaveListener]). Branché par [rememberCloudSession]. */
    internal var remoteSaveSink: ((CloudSave) -> Unit)? = null

    private var saveListener: ListenerRegistration? = null

    /** `startCloudSaveListener()` : suit la partie de ce compte en temps réel. */
    internal fun startSaveListener(uid: String) {
        stopSaveListener()
        saveListener = bridge.listenCloudSave(uid, sessionId) { remote -> remoteSaveSink?.invoke(remote) }
    }

    internal fun stopSaveListener() {
        saveListener?.remove()
        saveListener = null
    }

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
        if (achievementIds.isEmpty() || !cloudReady) return
        scope.launch {
            for (achievementId in achievementIds) {
                runCatching { bridge.pushAchvUnlock(id, achievementId, totalThrows) }
            }
        }
    }

    /** `syncAchievementStats()` : à chaque connexion, renvoie tous les succès
     *  déjà obtenus, au cas où ils l'aient été hors ligne. */
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
        if (state != CloudState.OFFLINE) return
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

    /** Relit le compte connecté : son code (gardé en local pour s'y
     *  reconnecter) et s'il est encore invité. */
    internal fun refreshAccount() {
        accountCode = bridge.accountCode
        if (accountCode.isNotEmpty()) prefs.accountCode = accountCode
        state = if (bridge.isAnonymous) CloudState.GUEST else CloudState.LINKED
    }

    /**
     * Firebase vient de fermer la session sans qu'on l'ait demandé : le code
     * a été régénéré depuis un autre appareil (Firebase révoque alors les
     * sessions des autres). Comme `onAuthUser(null)` côté site : on relance
     * la connexion, qui essaiera le code gardé en local — et passera en
     * [CloudState.REVOKED] s'il n'ouvre plus rien.
     */
    internal fun onSignedOutUnexpectedly() {
        if (switching || uid == null) return
        stopGifts()
        stopSaveListener()
        uid = null
        accountCode = ""
        cloudReady = false
        state = CloudState.CONNECTING
        retryTrigger++
    }

    /**
     * `startSession()` côté site : la session Firebase gardée par l'appareil
     * s'il y en a une ; sinon le code gardé en local (session perdue, ou
     * coupée parce que le code a changé) ; sinon un ancien code gardé dans la
     * sauvegarde (joueurs d'avant la 11.3.0) ; sinon un compte neuf, qui
     * reçoit son code juste après (voir [ensureAccountCode]).
     */
    internal suspend fun connect(legacyCode: String): ConnectResult {
        bridge.uid?.let { return ConnectResult.Connected(it, staleLegacyCode = false) }
        if (prefs.accountRevoked) return ConnectResult.Revoked(justNow = false)
        val stored = prefs.accountCode
        if (stored.isNotEmpty()) {
            val signedIn = try {
                bridge.signInWithCode(stored)
            } catch (e: Exception) {
                if (!FirebaseBridge.isBadCredential(e)) throw e
                // Le code de cet appareil n'ouvre plus rien : il a été
                // régénéré ailleurs. Surtout PAS de compte neuf : l'appareil
                // attend le nouveau code (ou une déconnexion volontaire).
                prefs.accountCode = ""
                prefs.accountRevoked = true
                return ConnectResult.Revoked(justNow = true)
            }
            return ConnectResult.Connected(signedIn ?: error("Connexion Firebase sans utilisateur"), staleLegacyCode = false)
        }
        var staleLegacy = false
        val legacy = RecoveryCode.normalize(legacyCode)
        if (RecoveryCode.isValid(legacy)) {
            try {
                bridge.signInWithCode(legacy)?.let { return ConnectResult.Connected(it, staleLegacyCode = false) }
            } catch (e: Exception) {
                if (!FirebaseBridge.isBadCredential(e)) throw e
                // Ancien code qui n'ouvre aucun compte : périmé, on l'oublie.
                // S'il date d'avant la refonte, « J'ai déjà un code » sait
                // encore récupérer sa copie (voir lookupCode).
                staleLegacy = true
            }
        }
        val guest = bridge.signInAnonymously() ?: error("Connexion Firebase sans utilisateur")
        return ConnectResult.Connected(guest, staleLegacyCode = staleLegacy)
    }

    /**
     * `ensureAccountCode()` : donne un code à un compte encore invité, sans
     * changer d'uid (la partie, le classement, le profil et les abonnés
     * restent les siens). Un code déjà présent dans la sauvegarde (créé avant
     * la refonte des comptes) est réutilisé en priorité : le joueur l'a
     * peut-être noté. [markShown] : le joueur l'a demandé lui-même (bouton
     * « Créer mon code »), il va donc le voir.
     */
    internal suspend fun ensureAccountCode(
        currentSave: () -> GameSave,
        onLocalChange: (GameSave) -> Unit,
        markShown: Boolean = false,
    ): Boolean {
        val id = uid ?: return false
        if (!bridge.isAnonymous) return true
        val legacy = RecoveryCode.normalize(currentSave().recoveryCode)
        var linkedLegacy = false
        if (RecoveryCode.isValid(legacy)) {
            when (bridge.linkToCode(legacy)) {
                LinkResult.LINKED -> linkedLegacy = true
                // Relié entre-temps à un autre compte, depuis un autre
                // appareil : proposé pour rejoindre ce compte, et cet
                // appareil reçoit son propre code.
                LinkResult.CODE_TAKEN -> prefs.joinSuggestion = legacy
                LinkResult.FAILED -> return false
            }
        }
        if (!linkedLegacy && bridge.linkToCode(RecoveryCode.generate()) != LinkResult.LINKED) return false
        if (uid != id) return false
        refreshAccount()
        // Personne ne connaît encore un code tout neuf (il est masqué tant
        // que le joueur ne l'affiche pas) : voir AccountRules.isUnreachable.
        onLocalChange(
            currentSave().copy(
                accountCodeShown = linkedLegacy || markShown,
                recoveryCode = if (linkedLegacy) legacy else "",
            ),
        )
        return true
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
        switching = true
        cloudReady = false // plus aucune écriture vers le compte qu'on quitte
        stopGifts()
        // Une partie reçue de l'ANCIEN compte n'a plus rien à faire ici.
        stopSaveListener()
        val oldUid = uid
        if (oldUid != null) {
            val unreachable = AccountRules.isUnreachable(bridge.isAnonymous, accountCode.isNotEmpty(), currentSave.accountCodeShown)
            runCatching { bridge.abandonAccount(oldUid, currentSave.unlockedAchievements, unreachable) }
        }
        val joined = try {
            bridge.signInWithCode(code)
        } catch (e: Exception) {
            null
        }
        switching = false
        if (joined == null) {
            // Connexion échouée après coup (réseau...) : on reste sur le
            // compte courant, qui reprend là où il en était (son entrée de
            // classement, retirée juste avant, est republiée).
            if (oldUid != null) {
                cloudReady = true
                startSaveListener(oldUid)
                scope.launch { startGifts(oldUid) }
                pushAllScores(oldUid, currentSave)
            }
            return "recoveryErrWrongCode"
        }

        uid = joined
        prefs.accountRevoked = false
        prefs.joinSuggestion = ""
        refreshAccount()
        return try {
            val cloud = bridge.fetchCloudSave(joined)
            val chosen = if (choice == MergeChoice.OTHER && cloud != null) {
                // Le joueur vient de taper ce code : il est donc connu.
                val restored = cloud.save.copy(accountCodeShown = true, playedOnAndroid = true)
                repository.saveFromCloud(restored, cloud.updatedAtMillis)
                onSaveChange(restored)
                restored
            } else {
                // Partie du téléphone gardée (ou compte sans partie) : elle
                // devient celle du compte.
                val adopted = AccountRules.keepLocal(currentSave, cloud?.save ?: GameSave()).copy(playedOnAndroid = true)
                onSaveChange(adopted)
                bridge.pushCloudSave(joined, adopted, sessionId)
                adopted
            }
            cloudReady = true
            startSaveListener(joined)
            scope.launch { startGifts(joined) }
            syncAchievementStats(chosen)
            // La partie gardée n'a peut-être pas de record dans un des deux
            // mondes : l'ancienne entrée du compte n'a alors plus lieu d'être.
            pushAllScores(joined, chosen, removeEmpty = choice == MergeChoice.CURRENT)
            null
        } catch (e: Exception) {
            cloudReady = true
            "leaderboardError"
        }
    }

    /**
     * `restoreLegacyCode()` : un ancien code ne désigne pas un compte mais une
     * COPIE de partie. On retire l'entrée de classement de l'ancien compte,
     * on garde la partie choisie, puis ce code devient celui du compte de cet
     * appareil — c'est celui que le joueur a noté.
     */
    suspend fun restoreLegacy(
        code: String,
        lookup: CodeLookup.Legacy,
        choice: MergeChoice,
        currentSave: GameSave,
        onSaveChange: (GameSave) -> Unit,
    ): String? {
        val id = uid
        if (!bridge.isAvailable || id == null || !cloudReady) return "recoveryErrOffline"
        return try {
            bridge.retireOldLeaderboardEntry(lookup.oldUid, lookup.retireToken, lookup.save.pseudo)
            val chosen = if (choice == MergeChoice.OTHER) lookup.save else currentSave
            val restored = chosen.copy(
                recoveryCode = code,
                recoveryRetireToken = chosen.recoveryRetireToken.ifBlank { RecoveryCode.generate() },
                accountCodeShown = true,
                playedOnAndroid = true,
            )
            onSaveChange(restored)
            // Les deux classements tout de suite, sans attendre un record.
            pushAllScores(id, restored, removeEmpty = true)
            switching = true
            val changed = runCatching { bridge.changeCode(code) }.isSuccess
            switching = false
            if (changed) refreshAccount()
            null
        } catch (e: Exception) {
            switching = false
            "recoveryErrWrongCode"
        }
    }

    /**
     * Bouton « Créer mon code » : compte connecté mais encore sans code
     * (compte invité dont la liaison automatique a échoué, ancien compte à
     * vrai e-mail). Renvoie vrai si le compte a maintenant son code.
     */
    suspend fun createCode(currentSave: () -> GameSave, onSaveChange: (GameSave) -> Unit): Boolean {
        if (uid == null || !cloudReady) return false
        if (bridge.isAnonymous) return ensureAccountCode(currentSave, onSaveChange, markShown = true)
        switching = true
        val ok = runCatching { bridge.changeCode(RecoveryCode.generate()) }.isSuccess
        switching = false
        if (!ok) return false
        refreshAccount()
        onSaveChange(currentSave().copy(accountCodeShown = true))
        return true
    }

    /**
     * Bouton « Régénérer » : nouveau code, l'ancien ne marche plus nulle
     * part, les autres appareils sont déconnectés. La partie ne change pas.
     */
    suspend fun regenerateCode(currentSave: () -> GameSave, onSaveChange: (GameSave) -> Unit): Boolean {
        val oldCode = accountCode
        if (uid == null || !cloudReady || oldCode.isEmpty()) return false
        switching = true
        val ok = runCatching { bridge.changeCode(RecoveryCode.generate()) }.isSuccess
        switching = false
        if (!ok) return false
        refreshAccount()
        val save = currentSave()
        // Copie du code dans la sauvegarde (versions d'avant la 11.3.0) :
        // l'ancien code n'y a plus sa place.
        onSaveChange(save.copy(accountCodeShown = true, recoveryCode = if (save.recoveryCode == oldCode) "" else save.recoveryCode))
        return true
    }

    /** Afficher ou copier le code, c'est le transmettre au joueur : le compte
     *  cesse d'être « connu de personne » (voir AccountRules.isUnreachable). */
    fun markCodeShown(currentSave: GameSave, onSaveChange: (GameSave) -> Unit) {
        if (!currentSave.accountCodeShown) onSaveChange(currentSave.copy(accountCodeShown = true))
    }

    /**
     * `btn-logout` côté site : repart de zéro sur CET appareil avec un
     * compte tout neuf. Le compte quitté perd son entrée de classement (il
     * la republiera à sa prochaine connexion ailleurs), et tout le reste de
     * ses traces publiques si personne ne connaît son code.
     *
     * Renvoie la sauvegarde neuve à appliquer localement.
     */
    suspend fun logout(currentSave: GameSave): GameSave {
        switching = true
        cloudReady = false
        // Les cadeaux et la partie de l'ancien compte ne doivent plus arriver ici.
        stopGifts()
        stopSaveListener()
        val id = uid
        if (bridge.isAvailable && id != null) {
            val unreachable = AccountRules.isUnreachable(bridge.isAnonymous, accountCode.isNotEmpty(), currentSave.accountCodeShown)
            runCatching { bridge.abandonAccount(id, currentSave.unlockedAchievements, unreachable) }
        }
        // uid oublié AVANT de fermer la session : l'écouteur de session voit
        // alors une déconnexion attendue (voir onSignedOutUnexpectedly).
        uid = null
        accountCode = ""
        lastPushedPseudo = null
        bridge.signOut()
        prefs.clearAccount()
        switching = false
        state = if (bridge.isAvailable) CloudState.CONNECTING else CloudState.NOT_CONFIGURED
        retryTrigger++ // relance une connexion (compte neuf), voir rememberCloudSession
        return GameSave(playedOnAndroid = true)
    }

    /**
     * `pushAllScores()` : les deux classements, chacun avec SON record (la
     * Ville compte dans le classement mondial, voir `Leaderboard` dans
     * `:core`). [removeEmpty] : retire l'entrée d'un monde sans record.
     */
    internal suspend fun pushAllScores(id: String, save: GameSave, removeEmpty: Boolean = false) {
        for ((collection, record) in Leaderboard.allRecords(save)) {
            if (record > 0.0) {
                runCatching { bridge.pushScore(collection, id, save.pseudo, record, save.playedOnAndroid) }
            } else if (removeEmpty) {
                runCatching { bridge.deleteScore(collection, id) }
            }
        }
        lastPushedPseudo = save.pseudo
    }

    /** La clé de traduction de l'état du compte (`#account-status` côté site). */
    val statusKey: String
        get() = when (state) {
            CloudState.NOT_CONFIGURED -> "onlineOfflineNoConfig"
            CloudState.CONNECTING -> "onlineConnecting"
            CloudState.GUEST -> "accountStatusLocal"
            CloudState.LINKED -> if (accountCode.isEmpty()) "accountStatusEmail" else "accountStatusLinked"
            CloudState.OFFLINE -> "accountStatusOffline"
            CloudState.REVOKED -> "accountStatusRevoked"
        }
}

/**
 * Ouvre la session en ligne et la tient à jour.
 *
 * Au démarrage : connexion (voir [CloudSession.connect]), puis lecture de la
 * sauvegarde du cloud. Celle-ci ne remplace la partie locale que si elle est
 * PLUS RÉCENTE que la dernière écriture de cet appareil (voir
 * `CloudSaveSync`, porté et testé dans `:core`) — sans quoi ouvrir l'app
 * après une partie hors ligne effacerait ce qui vient d'être joué. Puis le
 * compte reçoit son code s'il n'en a pas encore.
 *
 * Si cette première tentative échoue (réseau pas encore prêt au tout
 * premier lancement, par exemple), quelques nouvelles tentatives
 * automatiques suivent ([AUTO_RETRY_DELAYS_MS]) avant de laisser le joueur
 * réessayer lui-même depuis l'écran Compte.
 *
 * Ensuite : chaque changement de la sauvegarde est renvoyé au cloud, mais
 * seulement une fois le calme revenu ([PUSH_DEBOUNCE_MS]) et jamais avant
 * que la sauvegarde du compte soit chargée ([CloudSession.cloudReady]).
 *
 * [onSaveChange] reçoit une partie venue du cloud (déjà écrite sur le disque
 * avec l'horodatage du serveur) ; [onLocalChange] une modification faite ici
 * (pseudo par défaut, code...), à enregistrer comme n'importe quelle autre.
 */
@Composable
fun rememberCloudSession(
    save: GameSave,
    repository: SaveRepository,
    onSaveChange: (GameSave) -> Unit,
    onLocalChange: (GameSave) -> Unit,
    onGifts: (gifts: List<IncomingGift>, atLogin: Boolean) -> Unit,
    onRemoteSave: (CloudSave) -> Unit,
    onRevoked: () -> Unit,
    inForeground: Boolean,
): CloudSession {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { CloudSession(FirebaseBridge(context), DevicePrefs(context), scope) }
    val currentSave by rememberUpdatedState(save)
    val currentOnGifts by rememberUpdatedState(onGifts)
    val currentOnRemoteSave by rememberUpdatedState(onRemoteSave)
    val currentOnLocalChange by rememberUpdatedState(onLocalChange)
    val currentOnRevoked by rememberUpdatedState(onRevoked)
    DisposableEffect(session) {
        session.giftSink = { gifts, atLogin -> currentOnGifts(gifts, atLogin) }
        session.remoteSaveSink = { remote -> currentOnRemoteSave(remote) }
        // Session fermée par Firebase (code régénéré ailleurs) : voir
        // CloudSession.onSignedOutUnexpectedly.
        val authListener = session.bridge.addSignOutListener { session.onSignedOutUnexpectedly() }
        onDispose {
            authListener?.let { session.bridge.removeAuthListener(it) }
            session.giftSink = null
            session.remoteSaveSink = null
            session.stopGifts()
            session.stopSaveListener()
        }
    }

    // pingPresence() : toutes les 60 s tant que l'app est à l'écran, et dès
    // qu'elle y revient (le `visibilitychange` du site).
    LaunchedEffect(session.uid, session.cloudReady, inForeground) {
        val id = session.uid ?: return@LaunchedEffect
        if (!inForeground || !session.cloudReady) return@LaunchedEffect
        while (true) {
            session.bridge.pingPresence(id)
            delay(PRESENCE_PING_INTERVAL_MS)
        }
    }

    LaunchedEffect(session.retryTrigger) {
        if (!session.bridge.isAvailable) return@LaunchedEffect
        var attempt = 0
        while (true) {
            try {
                session.cloudReady = false
                val result = session.connect(currentSave.recoveryCode)
                if (result is ConnectResult.Revoked) {
                    session.state = CloudState.REVOKED
                    if (result.justNow) currentOnRevoked()
                    return@LaunchedEffect
                }
                val connected = result as ConnectResult.Connected
                session.uid = connected.uid
                session.refreshAccount()

                // La partie sur laquelle on travaille pendant la connexion :
                // `currentSave` ne suit un changement qu'à la recomposition
                // suivante, trop tard pour les étapes qui s'enchaînent ici.
                var working = currentSave
                val cloud = session.bridge.fetchCloudSave(connected.uid)
                if (cloud != null &&
                    CloudSaveSync.shouldApplyRemoteSave(repository.lastPersistAtMillis, cloud.updatedAtMillis)
                ) {
                    working = CloudSaveSync.mergeRemote(cloud.save, working)
                    repository.saveFromCloud(working, cloud.updatedAtMillis)
                    onSaveChange(working)
                }
                var changed = false
                // Compte relié à un code avant l'apparition de ce champ : son
                // code a forcément déjà été affiché (seul moyen de le créer).
                if (cloud != null && !cloud.codeShownKnown && !session.bridge.isAnonymous && !working.accountCodeShown) {
                    working = working.copy(accountCodeShown = true)
                    changed = true
                }
                if (connected.staleLegacyCode) {
                    working = working.copy(recoveryCode = "")
                    changed = true
                }
                // Jamais de pseudo vide au classement : le site en attribue un
                // à la première connexion (`"Trousse-" + ...`), sans quoi
                // l'entrée s'affiche "Anonyme" pour tout le monde.
                if (working.pseudo.isBlank()) {
                    working = working.copy(pseudo = Pseudo.generateDefault())
                    changed = true
                }
                // Joué sur Android : pseudo arc-en-ciel, partout et pour de bon.
                if (!working.playedOnAndroid) {
                    working = working.copy(playedOnAndroid = true)
                    changed = true
                }
                if (changed) currentOnLocalChange(working)
                session.cloudReady = true
                // Rattrape les succès obtenus hors ligne ou avant ce système.
                session.syncAchievementStats(working)
                // Republie les deux classements à chaque connexion : un
                // classement incohérent se répare tout seul, sans attendre un
                // nouveau record (et la Ville y compte désormais).
                session.pushAllScores(connected.uid, working)
                // Parties jouées ailleurs pendant la session, en temps réel.
                session.startSaveListener(connected.uid)
                // Cadeaux reçus pendant l'absence, puis écoute temps réel.
                session.startGifts(connected.uid)
                // Compte encore invité (première ouverture, ou joueur d'avant
                // la 11.3.0) : il reçoit son code maintenant, sans changer d'uid.
                session.ensureAccountCode({ currentSave }, { currentOnLocalChange(it) })
                return@LaunchedEffect
            } catch (e: Exception) {
                // Réseau coupé, règles Firestore, authentification désactivée
                // dans la console : aucune de ces situations ne doit
                // empêcher de jouer. On retente quelques fois tout seul
                // avant de laisser la main (voir AUTO_RETRY_DELAYS_MS).
                session.state = CloudState.OFFLINE
                if (attempt >= AUTO_RETRY_DELAYS_MS.size) return@LaunchedEffect
                delay(AUTO_RETRY_DELAYS_MS[attempt])
                attempt++
                session.state = CloudState.CONNECTING
            }
        }
    }

    // Renvoi de la sauvegarde vers le cloud, une fois les changements calmés.
    LaunchedEffect(save, session.uid, session.cloudReady) {
        val id = session.uid ?: return@LaunchedEffect
        if (!session.cloudReady || session.state == CloudState.OFFLINE) return@LaunchedEffect
        delay(PUSH_DEBOUNCE_MS)
        try {
            session.bridge.pushCloudSave(id, save, session.sessionId)
            session.bridge.pushPublicProfile(id, save)
            if (save.pseudo != session.lastPushedPseudo) {
                // Nouveau pseudo : les DEUX classements, chacun avec son record.
                session.pushAllScores(id, save)
            } else {
                session.bridge.pushScore(Leaderboard.collectionFor(save), id, save.pseudo, Leaderboard.recordFor(save), save.playedOnAndroid)
            }
        } catch (e: Exception) {
            // Best-effort, exactement comme côté site : on réessaiera au
            // prochain changement de la sauvegarde.
        }
    }

    return session
}

/** `PRESENCE_PING_INTERVAL_MS` côté site. */
private const val PRESENCE_PING_INTERVAL_MS = 60_000L

/** Le site écrit à chaque `persist()` ; ici on attend que la sauvegarde
 *  arrête de bouger, pour ne pas écrire dix fois pendant un seul lancer. */
private const val PUSH_DEBOUNCE_MS = 1500L

/** Délais entre les tentatives de connexion automatiques après un premier
 *  échec (2 s, 5 s, 12 s) : assez vite pour rattraper un réseau qui vient
 *  tout juste de répondre, sans marteler Firebase si le problème persiste. */
private val AUTO_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 12_000L)
