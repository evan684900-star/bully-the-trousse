package com.bullythetrousse.app

import android.content.Context
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.Gifts
import com.bullythetrousse.core.IncomingGift
import com.bullythetrousse.core.Leaderboard
import com.bullythetrousse.core.LegacySave
import com.bullythetrousse.core.RecoveryCode
import com.bullythetrousse.core.SaveCodec
import com.bullythetrousse.core.SkinStats
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Le pont vers le projet Firebase du jeu (`bully-the-trousse`) : comptes,
 * sauvegarde cloud, classement, profil public. Portage de la section 12
 * d'index.html.
 *
 * **Le modèle de données est celui du site, au champ près.** C'est la
 * contrainte qui gouverne tout ce fichier : l'app ne crée pas son propre
 * univers Firebase, elle rejoint celui qui tourne déjà. Concrètement —
 *
 * - `users/{uid}` : un champ Firestore PAR champ de la sauvegarde (voir
 *   [SaveCodec.toFieldMap]), et non une chaîne JSON. Le site relit ce
 *   document tel quel.
 * - `scores` / `scoresPlage` : `{ pseudo, bestDistance, android, updatedAt }`, un
 *   document par joueur. Le nom `bestDistance` n'est pas négociable : c'est
 *   le champ sur lequel le site trie le classement.
 * - `profiles/{uid}` : le sous-ensemble public de la sauvegarde.
 * - `follows/{abonné_suivi}` : une relation par document.
 *
 * Le compte suit la même règle : le code de récupération EST l'identité
 * (voir [RecoveryCode]). Un joueur qui entre sur le téléphone le code de
 * son compte du site retrouve le même `uid`, donc la même sauvegarde, le
 * même profil et une seule entrée de classement.
 *
 * **Tout est facultatif.** Tant que `app/google-services.json` n'est pas là,
 * [isAvailable] vaut `false` et chaque appel est un no-op : le jeu reste
 * entièrement jouable hors ligne, comme le site quand les scripts Firebase
 * ne chargent pas.
 *
 * **Non vérifié à la compilation** : ce fichier dépend du SDK Firebase
 * Android, hors de portée du bac à sable où il a été écrit. La logique
 * testable (dérivation du compte, conversion de la sauvegarde) vit dans
 * `:core` et est couverte par des tests ; ici il ne reste que de la
 * plomberie réseau.
 */
class FirebaseBridge(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Firebase s'initialise tout seul au démarrage, MAIS seulement si le
     * plugin `google-services` a généré les ressources correspondantes
     * depuis `app/google-services.json`. Sans ce fichier, la liste est vide
     * et toucher à `FirebaseAuth.getInstance()` lèverait une exception —
     * d'où ce garde-fou lu une fois pour toutes.
     */
    val isAvailable: Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val uid: String? get() = if (isAvailable) auth.currentUser?.uid else null

    /** Un compte anonyme n'a pas encore de code : il n'est rattaché à aucun
     *  joueur, et disparaîtrait avec l'app. */
    val isAnonymous: Boolean get() = isAvailable && auth.currentUser?.isAnonymous != false

    /**
     * Le code du compte connecté, lu dans son e-mail (`codeFromEmail()` côté
     * site) : "" pour un compte invité ou un ancien compte à vrai e-mail.
     * C'est le SEUL code affiché au joueur : par construction, celui qui
     * ouvre vraiment son compte.
     */
    val accountCode: String
        get() {
            if (!isAvailable) return ""
            val user = auth.currentUser ?: return ""
            return if (user.isAnonymous) "" else RecoveryCode.codeFromEmail(user.email)
        }

    /**
     * Suit la session Firebase : [onSignedOut] est appelé quand plus personne
     * n'est connecté — c'est ainsi qu'on apprend que le code a été régénéré
     * depuis un autre appareil (Firebase révoque alors cette session).
     */
    fun addSignOutListener(onSignedOut: () -> Unit): FirebaseAuth.AuthStateListener? {
        if (!isAvailable) return null
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) onSignedOut()
        }
        auth.addAuthStateListener(listener)
        return listener
    }

    fun removeAuthListener(listener: FirebaseAuth.AuthStateListener) {
        if (isAvailable) auth.removeAuthStateListener(listener)
    }

    // ---- Comptes ----

    /** `firebase.auth().signInAnonymously()` : le compte invité de départ. */
    suspend fun signInAnonymously(): String? {
        if (!isAvailable) return null
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user?.uid
    }

    /**
     * `signInWithEmailAndPassword(accountEmailForCode(code), ...)` : rejoint
     * le compte désigné par ce code. C'est ce qui rend l'app et le site
     * indiscernables pour un même joueur.
     */
    suspend fun signInWithCode(code: String): String? {
        if (!isAvailable) return null
        val result = auth.signInWithEmailAndPassword(
            RecoveryCode.emailFor(code),
            RecoveryCode.passwordFor(code),
        ).await()
        return result.user?.uid
    }

    /**
     * Transforme le compte invité de cet appareil en compte identifié par ce
     * code, **en gardant le même uid** — donc sans perdre la partie ni créer
     * une deuxième entrée au classement (`ensureAccountCode()` côté site).
     */
    suspend fun linkToCode(code: String): LinkResult {
        if (!isAvailable) return LinkResult.FAILED
        val user = auth.currentUser ?: return LinkResult.FAILED
        if (!user.isAnonymous) return LinkResult.FAILED
        return try {
            user.linkWithCredential(credentialFor(code)).await()
            LinkResult.LINKED
        } catch (e: FirebaseAuthUserCollisionException) {
            // Ce code appartient déjà à un compte (relié depuis un autre appareil).
            LinkResult.CODE_TAKEN
        } catch (e: Exception) {
            // E-mail/mot de passe désactivé dans la console, réseau coupé : on
            // reste sur le compte courant, le jeu continue.
            LinkResult.FAILED
        }
    }

    /**
     * Fait de [newCode] le code du compte connecté, sans changer d'uid
     * (`changeAccountCode()` côté site) : relie un compte invité, ou change
     * l'e-mail ET le mot de passe d'un compte existant EN UNE SEULE requête
     * (API REST `accounts:update`) — avec deux appels séparés, une coupure
     * entre les deux laisserait un compte que ni l'ancien ni le nouveau code
     * n'ouvrent. Firebase révoque alors les sessions des autres appareils :
     * ils sont déconnectés et devront entrer le nouveau code.
     *
     * Nécessite que la protection contre l'énumération des e-mails reste
     * désactivée dans la console Firebase, comme pour le site.
     */
    suspend fun changeCode(newCode: String) {
        check(isAvailable) { "Firebase indisponible" }
        val user = auth.currentUser ?: throw IllegalStateException("Aucun compte connecté")
        if (user.isAnonymous) {
            user.linkWithCredential(credentialFor(newCode)).await()
            return
        }
        val oldCode = RecoveryCode.codeFromEmail(user.email)
        // Changer d'identifiants exige une connexion récente : on la refait
        // avec l'ancien code (impossible pour un ancien compte à vrai e-mail :
        // on tente alors directement).
        if (oldCode.isNotEmpty()) user.reauthenticate(credentialFor(oldCode)).await()
        val idToken = user.getIdToken(false).await().token ?: throw IllegalStateException("Jeton introuvable")
        withContext(Dispatchers.IO) { updateCredentials(idToken, newCode) }
        // Les jetons de CETTE session viennent d'être révoqués eux aussi :
        // reconnexion au même compte (même uid).
        auth.signInWithEmailAndPassword(RecoveryCode.emailFor(newCode), RecoveryCode.passwordFor(newCode)).await()
        if (oldCode.isNotEmpty()) retireLegacyCodeCopy(oldCode, user.uid)
    }

    /**
     * `accounts:update` de l'API REST Firebase Auth : e-mail et mot de passe
     * changés en une seule requête. Avec la clé d'API du SITE (publique,
     * sans restriction d'application) : celle de l'app Android peut être
     * limitée aux appels du SDK signés par l'app, ce que ne fait pas cette
     * requête faite à la main.
     */
    private fun updateCredentials(idToken: String, newCode: String) {
        val url = URL(ACCOUNTS_UPDATE_URL + URLEncoder.encode(WEB_API_KEY, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("idToken", idToken)
                .put("email", RecoveryCode.emailFor(newCode))
                .put("password", RecoveryCode.passwordFor(newCode))
                .put("returnSecureToken", true)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                throw IllegalStateException("accounts:update a répondu $status : $error")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Ancien code : sa copie de partie éventuelle (système d'avant la refonte
     * des comptes, voir [lookupCode]) ne doit plus pouvoir être récupérée
     * avec lui. Les règles permettent de réécrire le document à condition
     * d'y mettre son propre uid.
     */
    private suspend fun retireLegacyCodeCopy(code: String, uid: String) {
        runCatching {
            val ref = firestore.collection(RECOVERY_CODES).document(code)
            val doc = ref.get().await()
            if (doc.exists() && doc.get("save") != null) {
                ref.set(mapOf("uid" to uid, "retired" to true)).await()
            }
        }
    }

    private fun credentialFor(code: String) =
        EmailAuthProvider.getCredential(RecoveryCode.emailFor(code), RecoveryCode.passwordFor(code))

    /**
     * `lookupAccountByCode()` : regarde ce que désigne un code SANS toucher à
     * la connexion en cours, grâce à une deuxième instance Firebase isolée
     * (`getVerifyApp()` côté site). Si le code est faux ou que le joueur
     * annule ensuite, rien n'a bougé.
     *
     * - un vrai compte : sa partie, lue dans `users/{uid}` ;
     * - un ANCIEN code (copie de partie dans `recoveryCodes`, d'avant la
     *   refonte des comptes) ;
     * - `null` : code inconnu.
     */
    suspend fun lookupCode(code: String): CodeLookup? {
        if (!isAvailable) return null
        val app = FirebaseApp.getApps(appContext).firstOrNull { it.name == VERIFY_APP }
            ?: FirebaseApp.initializeApp(appContext, FirebaseApp.getInstance().options, VERIFY_APP)
        val verifyAuth = FirebaseAuth.getInstance(app)
        try {
            val user = try {
                verifyAuth.signInWithEmailAndPassword(RecoveryCode.emailFor(code), RecoveryCode.passwordFor(code)).await().user
            } catch (e: FirebaseAuthException) {
                // Pas de compte pour ce code : soit il est faux, soit il date
                // d'avant la refonte, soit la connexion e-mail est désactivée
                // dans la console. Dans les trois cas, on essaie l'ancien
                // mécanisme plutôt que d'échouer (la protection anti-
                // énumération de Firebase masque « introuvable » derrière
                // « identifiants invalides », d'où le traitement groupé).
                null
            }
            if (user != null) {
                val doc = FirebaseFirestore.getInstance(app).collection(USERS).document(user.uid).get().await()
                val save = if (doc.exists()) SaveCodec.fromFieldMap(doc.data.orEmpty()) else GameSave()
                return CodeLookup.Account(user.uid, save)
            }
            val legacy = firestore.collection(RECOVERY_CODES).document(code).get().await()
            val raw = legacy.get("save") as? Map<*, *> ?: return null
            val rawSave = raw.entries.associate { (k, v) -> k.toString() to v }
            return CodeLookup.Legacy(
                oldUid = legacy.getString("uid"),
                retireToken = legacy.getString("retireToken"),
                save = LegacySave.migrate(SaveCodec.fromFieldMap(rawSave), rawSave),
            )
        } finally {
            // `closeVerifyApp()` : on ne garde jamais cette session ouverte.
            runCatching { verifyAuth.signOut() }
            runCatching { app.delete() }
        }
    }

    /**
     * `retireOldLeaderboardEntry()` : un ancien code restauré ailleurs laisse
     * l'entrée de classement de l'ANCIEN compte. On la remet à zéro, le
     * jeton de retrait prouvant aux règles Firestore qu'on en a le droit.
     */
    suspend fun retireOldLeaderboardEntry(oldUid: String?, retireToken: String?, pseudo: String) {
        if (!isAvailable || oldUid.isNullOrBlank() || retireToken.isNullOrBlank() || oldUid == uid) return
        for (collection in listOf(SCORES, SCORES_PLAGE)) {
            runCatching {
                firestore.collection(collection).document(oldUid).set(
                    mapOf("pseudo" to pseudo, "bestDistance" to 0.0, "retireToken" to retireToken),
                ).await()
            }
        }
    }

    /** `firebase.auth().signOut()` : ferme la session en cours. */
    fun signOut() {
        if (isAvailable) auth.signOut()
    }

    /**
     * `abandonCurrentAccount()` : à appeler juste avant que cet appareil
     * quitte son compte (connexion à un autre, déconnexion), TANT QU'IL Y EST
     * ENCORE CONNECTÉ — les règles n'autorisent chacun qu'à supprimer ses
     * propres documents.
     *
     * - Son entrée de classement part toujours : si le compte sert encore
     *   sur un autre appareil, celui-ci la republie à sa prochaine connexion.
     *   Plus de pseudo en double au classement.
     * - [unreachable] (voir `AccountRules.isUnreachable`, `:core`) : plus
     *   personne ne pourra y revenir, tout le reste de ses traces publiques
     *   part aussi.
     *
     * Ne supprime PAS le compte Firebase Auth lui-même : si la suite
     * échouait, l'appareil se retrouverait sans aucune session utilisable.
     */
    suspend fun abandonAccount(uid: String, unlockedAchievements: List<String>, unreachable: Boolean) {
        if (!isAvailable) return
        val docWipes = if (unreachable) {
            listOf(USERS, PROFILES, SCORES, SCORES_PLAGE, ACTIVE_PLAYERS, RECOVERY_TOKENS)
        } else {
            listOf(SCORES, SCORES_PLAGE)
        }
        for (collection in docWipes) {
            runCatching { firestore.collection(collection).document(uid).delete().await() }
        }
        if (!unreachable) return
        // Les succès déjà marqués doivent partir avec le reste : "activePlayers"
        // est le dénominateur du pourcentage de joueurs par succès et
        // "achvUnlocks" le numérateur — n'effacer que le premier ferait
        // grimper les pourcentages au-dessus de 100 %.
        for (id in unlockedAchievements) {
            runCatching {
                firestore.collection(ACHV_UNLOCKS).document(id).collection("players").document(uid).delete().await()
            }
        }
        // Les abonnements de ce compte jetable, sinon ils gonflent le nombre
        // d'abonnés des joueurs suivis avec un compte qui n'existe plus.
        runCatching {
            val follows = firestore.collection(FOLLOWS).whereEqualTo("follower", uid).get().await()
            for (doc in follows.documents) runCatching { doc.reference.delete().await() }
        }
    }

    // ---- Sauvegarde cloud ----

    /** `db.collection("users").doc(uid).get()`. */
    suspend fun fetchCloudSave(uid: String): CloudSave? {
        if (!isAvailable) return null
        val doc = firestore.collection(USERS).document(uid).get().await()
        val data = doc.data ?: return null
        if (data.isEmpty()) return null
        val updatedAt = doc.getTimestamp(UPDATED_AT)?.toDate()?.time ?: 0L
        return CloudSave(SaveCodec.fromFieldMap(data), updatedAt, codeShownKnown = data.containsKey("accountCodeShown"))
    }

    /**
     * `startCloudSaveListener()` : suit la partie en temps réel, pour qu'une
     * partie jouée sur le site (ou un autre téléphone) arrive ici sans
     * relancer l'app. Sont ignorés : le tout premier instantané (c'est ce que
     * la connexion vient de charger), les écritures pas encore confirmées,
     * et celles de CET appareil (même `sessionId`).
     */
    fun listenCloudSave(uid: String, sessionId: String, onRemote: (CloudSave) -> Unit): ListenerRegistration? {
        if (!isAvailable) return null
        var first = true
        return firestore.collection(USERS).document(uid).addSnapshotListener { doc, error ->
            if (error != null || doc == null) return@addSnapshotListener
            if (first) {
                first = false
                return@addSnapshotListener
            }
            if (!doc.exists() || doc.metadata.hasPendingWrites()) return@addSnapshotListener
            val data = doc.data ?: return@addSnapshotListener
            if (data["sessionId"] == sessionId) return@addSnapshotListener
            val updatedAt = doc.getTimestamp(UPDATED_AT)?.toDate()?.time ?: System.currentTimeMillis()
            onRemote(CloudSave(SaveCodec.fromFieldMap(data), updatedAt))
        }
    }

    /**
     * `pingPresence()` : rafraîchit l'horodatage du profil public, que les
     * autres joueurs lisent pour afficher « En ligne » ou « Vu il y a... ».
     * Best-effort.
     */
    suspend fun pingPresence(uid: String) {
        if (!isAvailable) return
        runCatching {
            firestore.collection(PROFILES).document(uid)
                .set(mapOf(UPDATED_AT to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        }
    }

    /**
     * `pushCloudSave()` : la sauvegarde champ par champ, plus les deux
     * métadonnées du site. `sessionId` identifie CET appareil, pour que
     * l'écoute temps réel du site sache ignorer sa propre écriture.
     */
    suspend fun pushCloudSave(uid: String, save: GameSave, sessionId: String) {
        if (!isAvailable) return
        val payload = SaveCodec.toFieldMap(save) + mapOf(
            "sessionId" to sessionId,
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(USERS).document(uid).set(payload, SetOptions.merge()).await()
    }

    // ---- Classement ----

    /** `scoresCollection()` : la plage a son propre classement (voir [Leaderboard]). */
    fun scoresCollectionFor(save: GameSave): String = Leaderboard.collectionFor(save)

    /**
     * `pushScoreTo()` : un document par joueur. [android] fait s'afficher son
     * pseudo en arc-en-ciel, sur le site comme dans l'app.
     */
    suspend fun pushScore(collection: String, uid: String, pseudo: String, bestDistance: Double, android: Boolean) {
        if (!isAvailable || bestDistance <= 0.0) return
        val payload = mapOf(
            "pseudo" to pseudo.ifBlank { "Anonyme" },
            "android" to android,
            "bestDistance" to bestDistance,
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(collection).document(uid).set(payload, SetOptions.merge()).await()
    }

    /** Retire l'entrée de ce joueur d'un classement (la partie qui l'a
     *  remplacée n'a pas de record dans ce monde). */
    suspend fun deleteScore(collection: String, uid: String) {
        if (!isAvailable) return
        firestore.collection(collection).document(uid).delete().await()
    }

    /**
     * `loadLeaderboard()` : tout le classement, trié par distance
     * décroissante. Les entrées à 0 sont écartées comme sur le site — ce
     * sont d'anciens comptes « retirés » après une récupération, dont la
     * distance a été remise à zéro faute de pouvoir supprimer le document.
     */
    suspend fun loadLeaderboard(collection: String): List<LeaderboardEntry> {
        if (!isAvailable) return emptyList()
        val snapshot = firestore.collection(collection)
            .orderBy("bestDistance", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val distance = doc.getDouble("bestDistance") ?: return@mapNotNull null
            if (distance <= 0.0) return@mapNotNull null
            LeaderboardEntry(
                uid = doc.id,
                pseudo = doc.getString("pseudo").orEmpty().ifBlank { "???" },
                distanceMeters = distance,
                android = doc.getBoolean("android") == true,
            )
        }
    }

    // ---- Profil public et abonnements ----

    /** `pushPublicProfile()` : le sous-ensemble de la sauvegarde qu'on
     *  accepte de rendre visible, sans jamais exposer `users/{uid}`. */
    suspend fun pushPublicProfile(uid: String, save: GameSave) {
        if (!isAvailable) return
        val payload = mapOf(
            "pseudo" to save.pseudo.ifBlank { "Anonyme" },
            "avatarEmoji" to save.avatarEmoji,
            "isPrivate" to save.isPrivate,
            "equippedSkin" to save.equippedSkin,
            "equippedSkinFilter" to (SKIN_FILTERS[save.equippedSkin] ?: "none"),
            "ownedSkins" to save.ownedSkins,
            "totalMoneyEarned" to save.totalMoneyEarned.toLong(),
            "dailyEarnings" to save.dailyEarnings.mapValues { it.value.toLong() },
            "dailyBestDistance" to save.dailyBestDistance,
            // Le record du classement mondial (Cour ET Ville), voir computeRank.
            "bestDistance" to Leaderboard.worldRecord(save),
            "android" to save.playedOnAndroid,
            "puissance" to SkinStats.totalPuissance(save).toLong(),
            "vitesse" to SkinStats.totalVitesse(save).toLong(),
            "achievementsCount" to save.unlockedAchievements.size.toLong(),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(PROFILES).document(uid).set(payload, SetOptions.merge()).await()
    }

    /**
     * `renderProfile(uid)` côté site : le profil PUBLIC d'un autre joueur,
     * lu dans `profiles/{uid}` — jamais dans `users/{uid}`, qui reste privé.
     * Renvoie `null` si ce joueur n'a pas encore de profil publié.
     */
    suspend fun fetchPublicProfile(uid: String): PublicProfile? {
        if (!isAvailable) return null
        val doc = firestore.collection(PROFILES).document(uid).get().await()
        if (!doc.exists()) return null
        return PublicProfile(
            uid = uid,
            pseudo = doc.getString("pseudo").orEmpty().ifBlank { "?" },
            avatarEmoji = doc.getString("avatarEmoji").orEmpty(),
            isPrivate = doc.getBoolean("isPrivate") ?: false,
            equippedSkin = doc.getString("equippedSkin").orEmpty().ifBlank { "classique" },
            // `safeOwnedSkins()` côté site : seuls des identifiants texte, le
            // document n'étant pas validé côté serveur.
            ownedSkins = (doc.get("ownedSkins") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            dailyEarnings = numberMap(doc.get("dailyEarnings")).mapValues { it.value.toLong() },
            dailyBestDistance = numberMap(doc.get("dailyBestDistance")),
            bestDistance = doc.getDouble("bestDistance") ?: 0.0,
            totalMoneyEarned = (doc.getLong("totalMoneyEarned") ?: 0L),
            puissance = (doc.getLong("puissance") ?: 0L).toInt(),
            vitesse = (doc.getLong("vitesse") ?: 0L).toInt(),
            achievementsCount = (doc.getLong("achievementsCount") ?: 0L).toInt(),
            android = doc.getBoolean("android") == true,
            // `pingPresence()` côté site tient ce champ à jour : il sert à
            // afficher "En ligne" ou "Vu il y a...".
            updatedAtMillis = doc.getTimestamp(UPDATED_AT)?.toDate()?.time,
        )
    }

    /** Une map `{ "AAAA-MM-JJ": nombre }` lue dans un document, sans faire
     *  confiance à son contenu (`num()` côté site : tout ce qui n'est pas un
     *  nombre fini est ignoré). */
    private fun numberMap(raw: Any?): Map<String, Double> =
        (raw as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val value = (v as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            key to value
        }.toMap()

    /**
     * `openFollowListModal()` : les comptes que [uid] suit (FOLLOWING) ou qui
     * le suivent (FOLLOWERS), 50 au plus, avec leur profil public pour le
     * pseudo et l'avatar (null si ce joueur n'a jamais publié de profil).
     */
    suspend fun listFollows(uid: String, direction: FollowDirection): List<Pair<String, PublicProfile?>> {
        if (!isAvailable) return emptyList()
        val (matchField, otherField) = when (direction) {
            FollowDirection.FOLLOWING -> "follower" to "following"
            FollowDirection.FOLLOWERS -> "following" to "follower"
        }
        val others = firestore.collection(FOLLOWS).whereEqualTo(matchField, uid).limit(50).get().await()
            .documents.mapNotNull { it.getString(otherField) }
        return others.map { other -> other to runCatching { fetchPublicProfile(other) }.getOrNull() }
    }

    /** `isFollowing()` côté site : un `get()` direct sur l'identifiant de la
     *  relation, sans requête — c'est pour ça que l'id est composé. */
    suspend fun isFollowing(followerUid: String, targetUid: String): Boolean {
        if (!isAvailable) return false
        return firestore.collection(FOLLOWS).document(followDocId(followerUid, targetUid)).get().await().exists()
    }

    /** `followUser()` côté site. Sans effet si on essaie de se suivre soi-même. */
    suspend fun follow(followerUid: String, targetUid: String) {
        if (!isAvailable || followerUid == targetUid) return
        val payload = mapOf(
            "follower" to followerUid,
            "following" to targetUid,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        firestore.collection(FOLLOWS).document(followDocId(followerUid, targetUid)).set(payload).await()
    }

    /** `unfollowUser()` côté site. */
    suspend fun unfollow(followerUid: String, targetUid: String) {
        if (!isAvailable) return
        firestore.collection(FOLLOWS).document(followDocId(followerUid, targetUid)).delete().await()
    }

    /** `followDocId()` côté site : "<abonné>_<suivi>", ce qui interdit les
     *  doublons par construction. */
    private fun followDocId(followerUid: String, targetUid: String) = "${followerUid}_$targetUid"

    /** `countFollowers()` / `countFollowing()` : une collection plate, un
     *  document par relation. */
    suspend fun countFollowers(uid: String): Int = countFollows("following", uid)

    suspend fun countFollowing(uid: String): Int = countFollows("follower", uid)

    private suspend fun countFollows(field: String, uid: String): Int {
        if (!isAvailable) return 0
        return countDocs(firestore.collection(FOLLOWS).whereEqualTo(field, uid))
    }

    /** `countDocs()` côté site : l'agrégation `count()` compte côté serveur
     *  sans télécharger les documents — un seul document lu facturé, quel que
     *  soit le nombre de résultats. */
    private suspend fun countDocs(query: Query): Int =
        query.count().get(AggregateSource.SERVER).await().count.toInt()

    // ---- Cadeaux d'argent entre joueurs (collection "gifts") ----

    /**
     * `openGiftModal()` : à qui je peux offrir de l'argent. « Abonnés » couvre
     * ici les deux sens du suivi — ceux qui me suivent ET ceux que je suis —
     * car les règles Firestore autorisent le cadeau dans les deux cas.
     */
    suspend fun listGiftTargets(uid: String): List<Pair<String, PublicProfile?>> {
        if (!isAvailable) return emptyList()
        val followers = firestore.collection(FOLLOWS).whereEqualTo("following", uid).limit(50).get().await()
            .documents.mapNotNull { it.getString("follower") }
        val following = firestore.collection(FOLLOWS).whereEqualTo("follower", uid).limit(50).get().await()
            .documents.mapNotNull { it.getString("following") }
        return (followers + following).distinct().map { other ->
            other to runCatching { fetchPublicProfile(other) }.getOrNull()
        }
    }

    /** `sendGift()` : écrit le cadeau que le destinataire ramassera. */
    suspend fun sendGift(fromUid: String, toUid: String, fromPseudo: String, fromAndroid: Boolean, amount: Int) {
        check(isAvailable) { "Firebase indisponible" }
        firestore.collection(GIFTS).add(
            mapOf(
                "from" to fromUid,
                "to" to toUid,
                "fromPseudo" to fromPseudo.ifBlank { "Anonyme" },
                "fromAndroid" to fromAndroid,
                "amount" to amount.toLong(),
                "seen" to false,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /**
     * `checkIncomingGifts()` : ramasse les cadeaux reçus depuis la dernière
     * connexion et les marque « vus » d'un seul lot, pour ne jamais les
     * créditer deux fois. Le marquage est best-effort, comme côté site.
     */
    suspend fun collectIncomingGifts(uid: String): List<IncomingGift> {
        if (!isAvailable) return emptyList()
        val snap = firestore.collection(GIFTS)
            .whereEqualTo("to", uid).whereEqualTo("seen", false)
            .limit(Gifts.PENDING_LIMIT).get().await()
        if (snap.isEmpty) return emptyList()
        val batch = firestore.batch()
        val gifts = snap.documents.map { doc ->
            batch.update(doc.reference, "seen", true)
            giftFrom(doc.id, doc.data.orEmpty())
        }
        runCatching { batch.commit().await() }
        return gifts
    }

    /**
     * `startGiftsListener()` : pendant la session, crédite chaque nouveau
     * cadeau dès son écriture par l'expéditeur. À démarrer seulement après
     * [collectIncomingGifts], pour ne jamais créditer le même document deux
     * fois. Renvoie de quoi arrêter l'écoute.
     */
    fun listenIncomingGifts(uid: String, onGift: (IncomingGift) -> Unit): ListenerRegistration? {
        if (!isAvailable) return null
        return firestore.collection(GIFTS)
            .whereEqualTo("to", uid).whereEqualTo("seen", false)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                for (change in snap.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val gift = giftFrom(change.document.id, change.document.data)
                    if (gift.amount <= 0) continue
                    change.document.reference.update("seen", true)
                    onGift(gift)
                }
            }
    }

    /** Un document de `gifts`, sans faire confiance à son contenu. */
    private fun giftFrom(docId: String, data: Map<String, Any?>): IncomingGift {
        val amount = (data["amount"] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: 0.0
        return IncomingGift(
            senderUid = data["from"] as? String ?: docId,
            senderPseudo = (data["fromPseudo"] as? String).orEmpty().ifBlank { "Anonyme" },
            amount = kotlin.math.floor(amount).toInt().coerceAtLeast(0),
            senderAndroid = data["fromAndroid"] == true,
        )
    }

    // ---- Statistiques publiques des succès (% de joueurs) ----

    /** `markPlayerActive()` : un compte est « actif » dès son premier lancer.
     *  Dénominateur des pourcentages, sans exposer la moindre sauvegarde. */
    suspend fun markPlayerActive(uid: String, totalThrows: Int) {
        if (!isAvailable || totalThrows < 1) return
        firestore.collection(ACTIVE_PLAYERS).document(uid).set(mapOf("active" to true), SetOptions.merge()).await()
    }

    /** `pushAchvUnlock(id)` : signale que ce compte a débloqué ce succès. */
    suspend fun pushAchvUnlock(uid: String, achievementId: String, totalThrows: Int) {
        if (!isAvailable) return
        markPlayerActive(uid, totalThrows)
        firestore.collection(ACHV_UNLOCKS).document(achievementId).collection("players").document(uid)
            .set(mapOf("unlocked" to true), SetOptions.merge()).await()
    }

    /** Nombre de joueurs actifs, dénominateur de `loadAchievementStats()`. */
    suspend fun countActivePlayers(): Int {
        if (!isAvailable) return 0
        return countDocs(firestore.collection(ACTIVE_PLAYERS))
    }

    /** Nombre de joueurs ayant débloqué [achievementId]. */
    suspend fun countAchievementUnlocks(achievementId: String): Int {
        if (!isAvailable) return 0
        return countDocs(firestore.collection(ACHV_UNLOCKS).document(achievementId).collection("players"))
    }

    /** `computeRank()` : nombre de joueurs avec un meilleur record, plus un. */
    suspend fun computeRank(collection: String, bestDistance: Double): Int {
        if (!isAvailable) return 0
        return countDocs(firestore.collection(collection).whereGreaterThan("bestDistance", bestDistance)) + 1
    }

    companion object {
        /** Ce code n'ouvre (plus) aucun compte : il est faux, ou il a été
         *  régénéré depuis un autre appareil. */
        fun isBadCredential(e: Throwable): Boolean =
            e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException

        /** Réseau coupé ou trop d'essais : à retenter plus tard. */
        fun isRetryable(e: Throwable): Boolean =
            e is FirebaseNetworkException || e is FirebaseTooManyRequestsException

        /** `firebaseConfig.apiKey` du site (publique, déjà dans index.html). */
        private const val WEB_API_KEY = "AIzaSyADulCVz8u8yVb9MGR2N9EYXs4DDtFydbs"
        private const val ACCOUNTS_UPDATE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:update?key="
        private const val USERS = "users"
        private const val PROFILES = "profiles"
        private const val FOLLOWS = "follows"
        private const val SCORES = "scores"
        private const val SCORES_PLAGE = "scoresPlage"
        private const val RECOVERY_CODES = "recoveryCodes"
        private const val RECOVERY_TOKENS = "recoveryTokens"
        private const val UPDATED_AT = "updatedAt"
        private const val GIFTS = "gifts"
        private const val VERIFY_APP = "verify"
        private const val ACTIVE_PLAYERS = "activePlayers"
        private const val ACHV_UNLOCKS = "achvUnlocks"
    }
}

/** Sauvegarde reçue du cloud, avec l'horodatage serveur de sa dernière
 *  écriture (voir `CloudSaveSync` dans `:core`, qui décide si on l'applique).
 *  [codeShownKnown] : le document porte déjà `accountCodeShown` (sinon il date
 *  d'avant la 11.3.0, voir `rememberCloudSession`). */
data class CloudSave(val save: GameSave, val updatedAtMillis: Long, val codeShownKnown: Boolean = true)

/** Résultat d'une liaison du compte invité à un code (voir [FirebaseBridge.linkToCode]). */
enum class LinkResult { LINKED, CODE_TAKEN, FAILED }

/** Ce que désigne un code de récupération (voir [FirebaseBridge.lookupCode]). */
sealed interface CodeLookup {
    val save: GameSave

    /** Un vrai compte : on s'y connectera. */
    data class Account(val uid: String, override val save: GameSave) : CodeLookup

    /** Un ancien code : une simple copie de partie, qu'on restaure puis
     *  qu'on rattache au compte de cet appareil. */
    data class Legacy(val oldUid: String?, val retireToken: String?, override val save: GameSave) : CodeLookup
}

/** Sens d'une liste d'abonnements : ceux que je suis, ou ceux qui me suivent. */
enum class FollowDirection { FOLLOWING, FOLLOWERS }

/** Une ligne du classement (`.leaderboard-row` côté site). [android] : pseudo
 *  en arc-en-ciel. */
data class LeaderboardEntry(val uid: String, val pseudo: String, val distanceMeters: Double, val android: Boolean = false)

/**
 * Le profil public d'un joueur, tel que `profiles/{uid}` le porte : le
 * sous-ensemble de la sauvegarde que le site accepte de rendre visible.
 * Volontairement sans les champs privés de `users/{uid}`.
 */
data class PublicProfile(
    val uid: String,
    val pseudo: String,
    val avatarEmoji: String,
    val isPrivate: Boolean,
    val equippedSkin: String,
    val ownedSkins: List<String>,
    val dailyEarnings: Map<String, Long>,
    val dailyBestDistance: Map<String, Double>,
    val bestDistance: Double,
    val totalMoneyEarned: Long,
    val puissance: Int,
    val vitesse: Int,
    val achievementsCount: Int,
    val updatedAtMillis: Long?,
    /** Joue sur l'app Android : pseudo en arc-en-ciel. */
    val android: Boolean = false,
)

/**
 * Attend un `Task` Firebase (API à callbacks) comme une coroutine standard,
 * sans ajouter `kotlinx-coroutines-play-services` pour ce seul besoin.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Tâche Firebase échouée sans exception"),
            )
        }
    }
}
