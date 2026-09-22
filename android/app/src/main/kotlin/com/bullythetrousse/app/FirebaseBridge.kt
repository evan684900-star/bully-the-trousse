package com.bullythetrousse.app

import android.content.Context
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.RecoveryCode
import com.bullythetrousse.core.SaveCodec
import com.bullythetrousse.core.SkinStats
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
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
 * - `scores` / `scoresPlage` : `{ pseudo, bestDistance, updatedAt }`, un
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
     * `linkAccountToCode()` : transforme le compte anonyme de cet appareil
     * en compte permanent identifié par ce code, **en gardant le même uid**
     * — donc sans perdre la partie ni créer une deuxième entrée au
     * classement. Renvoie `false` si un compte existe déjà pour ce code
     * (cas normal : le joueur l'a créé sur le site).
     */
    suspend fun linkToCode(code: String): Boolean {
        if (!isAvailable) return false
        val user = auth.currentUser ?: return false
        if (!user.isAnonymous) return false
        val credential = EmailAuthProvider.getCredential(
            RecoveryCode.emailFor(code),
            RecoveryCode.passwordFor(code),
        )
        return try {
            user.linkWithCredential(credential).await()
            true
        } catch (e: Exception) {
            // Compte déjà pris, e-mail/mot de passe désactivé dans la console,
            // réseau coupé : dans tous les cas on reste sur le compte courant,
            // le jeu continue. L'appelant décide quoi dire au joueur.
            false
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
        return CloudSave(SaveCodec.fromFieldMap(data), updatedAt)
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

    /**
     * `pushRecoverySnapshot()` : la copie rattachée au code, que le site lit
     * pour les anciens codes (créés avant que le code ne devienne un vrai
     * compte). On la tient à jour pour rester interchangeable avec lui.
     */
    suspend fun pushRecoverySnapshot(uid: String, save: GameSave) {
        if (!isAvailable || save.recoveryCode.isBlank()) return
        val payload = mapOf(
            "uid" to uid,
            "pseudo" to save.pseudo,
            "retireToken" to save.recoveryRetireToken,
            "save" to SaveCodec.toFieldMap(save),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(RECOVERY_CODES).document(save.recoveryCode).set(payload).await()
        firestore.collection(RECOVERY_TOKENS).document(uid)
            .set(mapOf("token" to save.recoveryRetireToken)).await()
    }

    // ---- Classement ----

    /** `scoresCollection()` : la plage a son propre classement. */
    fun scoresCollectionFor(save: GameSave): String = if (save.inPlage) SCORES_PLAGE else SCORES

    /**
     * `pushScoreTo()` : un document par joueur, réécrit à chaque record (et
     * non à chaque lancer, pour limiter les écritures).
     */
    suspend fun pushScore(collection: String, uid: String, pseudo: String, bestDistance: Double) {
        if (!isAvailable || bestDistance <= 0.0) return
        val payload = mapOf(
            "pseudo" to pseudo.ifBlank { "Anonyme" },
            "bestDistance" to bestDistance,
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(collection).document(uid).set(payload, SetOptions.merge()).await()
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
            "bestDistance" to save.bestDistance,
            "puissance" to SkinStats.totalPuissance(save).toLong(),
            "vitesse" to SkinStats.totalVitesse(save).toLong(),
            "achievementsCount" to save.unlockedAchievements.size.toLong(),
            UPDATED_AT to FieldValue.serverTimestamp(),
        )
        firestore.collection(PROFILES).document(uid).set(payload, SetOptions.merge()).await()
    }

    /** `countFollowers()` / `countFollowing()` : une collection plate, un
     *  document par relation. */
    suspend fun countFollowers(uid: String): Int = countFollows("following", uid)

    suspend fun countFollowing(uid: String): Int = countFollows("follower", uid)

    private suspend fun countFollows(field: String, uid: String): Int {
        if (!isAvailable) return 0
        return firestore.collection(FOLLOWS).whereEqualTo(field, uid).get().await().size()
    }

    companion object {
        private const val USERS = "users"
        private const val PROFILES = "profiles"
        private const val FOLLOWS = "follows"
        private const val SCORES = "scores"
        private const val SCORES_PLAGE = "scoresPlage"
        private const val RECOVERY_CODES = "recoveryCodes"
        private const val RECOVERY_TOKENS = "recoveryTokens"
        private const val UPDATED_AT = "updatedAt"
    }
}

/** Sauvegarde reçue du cloud, avec l'horodatage serveur de sa dernière
 *  écriture (voir `CloudSaveSync` dans `:core`, qui décide si on l'applique). */
data class CloudSave(val save: GameSave, val updatedAtMillis: Long)

/** Une ligne du classement (`.leaderboard-row` côté site). */
data class LeaderboardEntry(val uid: String, val pseudo: String, val distanceMeters: Double)

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
