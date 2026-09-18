package com.bullythetrousse.app

import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SaveCodec
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Synchronisation cloud (Firestore) de la sauvegarde et du classement,
 * portage BEST-EFFORT de `pushCloudSave()`/`syncCloudSaveOnLogin()`/
 * `pushScoreTo()` côté web (index.html, section 12).
 *
 * **Non branché par défaut** : `GameRoot` (MainActivity.kt) n'appelle rien
 * ici. Voir android/README.md pour les étapes restantes avant de le relier
 * (créer un projet Firebase, ajouter `google-services.json`, activer
 * l'authentification anonyme dans la console, décommenter le plugin dans
 * `app/build.gradle.kts`).
 *
 * **Écrit avec soin mais NON VÉRIFIÉ** : ce module dépend du SDK Firebase
 * Android, que ce portage n'a pas pu compiler (voir android/README.md, le
 * bac à sable où il a été écrit n'a pas accès au SDK Android) — à valider
 * dans Android Studio.
 *
 * **Divergence assumée par rapport au web** : là où le web stocke chaque
 * champ de la sauvegarde comme un champ Firestore séparé (ce qui permet
 * des requêtes partielles, ex. le classement lit juste `bestDistance`),
 * ce portage stocke toute la sauvegarde comme UNE chaîne JSON
 * (`SaveCodec.encode`/`decodeOrDefault`, déjà porté et testé dans `:core`)
 * dans un seul champ `saveJson`. Plus simple et plus sûr à écrire/relire
 * fidèlement, au prix de ne pas pouvoir interroger un champ précis
 * directement depuis Firestore (ex. pour un classement lu côté serveur) —
 * voir [pushScore] pour le classement, qui lui reste un champ dédié
 * puisque c'est justement ce qu'on a besoin d'interroger/trier.
 */
class FirebaseSaveRepository {
    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()

    /** `firebase.auth().signInAnonymously()` côté web : un compte "invité"
     *  par appareil tant qu'il n'est lié à aucun code de récupération (voir
     *  `linkAccountToCode()` côté web — la liaison de compte n'est PAS
     *  portée ici, seulement l'essentiel : un uid stable par appareil). */
    suspend fun ensureSignedIn(): String {
        auth.currentUser?.let { return it.uid }
        val result = auth.signInAnonymously().await()
        return requireNotNull(result.user) { "Connexion anonyme réussie sans utilisateur ?!" }.uid
    }

    /** `db.collection("users").doc(uid).get()` côté web. Renvoie `null` si
     *  ce compte n'a encore aucune sauvegarde cloud (première connexion). */
    suspend fun fetchCloudSave(uid: String): CloudSave? {
        val doc = firestore.collection(USERS_COLLECTION).document(uid).get().await()
        val json = doc.getString(SAVE_JSON_FIELD) ?: return null
        val updatedAtMillis = doc.getTimestamp(UPDATED_AT_FIELD)?.toDate()?.time ?: return null
        return CloudSave(SaveCodec.decodeOrDefault(json), updatedAtMillis)
    }

    /** `db.collection("users").doc(uid).set(payload, {merge:true})` côté
     *  web : écrase la sauvegarde cloud de ce compte par celle de cet
     *  appareil (fusion partielle, ne touche pas aux champs qu'on n'écrit
     *  pas ici — il n'y en a pas d'autres dans ce document). */
    suspend fun pushCloudSave(uid: String, save: GameSave) {
        val payload = mapOf(
            SAVE_JSON_FIELD to SaveCodec.encode(save),
            UPDATED_AT_FIELD to FieldValue.serverTimestamp(),
        )
        firestore.collection(USERS_COLLECTION).document(uid).set(payload, SetOptions.merge()).await()
    }

    /** `pushScoreTo("scores"|"scoresPlage", distance)` côté web : un champ
     *  dédié (pas le JSON complet, voir la note de classe) pour permettre
     *  un vrai classement trié côté Firestore. [collection] vaut "scores"
     *  (monde normal) ou "scoresPlage" (monde Plage), comme côté web. */
    suspend fun pushScore(collection: String, uid: String, distanceMeters: Double) {
        val payload = mapOf(
            "distance" to distanceMeters,
            UPDATED_AT_FIELD to FieldValue.serverTimestamp(),
        )
        firestore.collection(collection).document(uid).set(payload, SetOptions.merge()).await()
    }

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val SAVE_JSON_FIELD = "saveJson"
        private const val UPDATED_AT_FIELD = "updatedAt"
    }
}

/** Sauvegarde reçue du cloud, accompagnée de l'horodatage serveur de sa
 *  dernière écriture (voir [CloudSaveSync] dans `:core` pour décider si on
 *  doit l'appliquer par-dessus la sauvegarde locale). */
data class CloudSave(val save: GameSave, val updatedAtMillis: Long)

/**
 * Attend un `Task` Firebase (API à callbacks) comme une coroutine standard,
 * sans dépendre de `kotlinx-coroutines-play-services` (une dépendance de
 * plus pour un besoin très ponctuel).
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: IllegalStateException("Tâche Firebase échouée sans exception"))
        }
    }
}
