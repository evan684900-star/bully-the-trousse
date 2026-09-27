package com.bullythetrousse.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Les boutons « ⬇️ Télécharger » des musiques, dans les Réglages (liens
 * `download` côté site).
 *
 * Android 10 et plus : le fichier est enregistré dans Téléchargements via
 * MediaStore, sans aucune permission. Android 8 et 9 n'ont pas cette voie
 * sans demander l'accès au stockage : on ouvre plutôt la feuille de partage,
 * d'où le joueur peut l'enregistrer où il veut.
 *
 * @return ce qui s'est passé, pour choisir le toast à afficher.
 */
fun exportMusic(context: Context, rawId: Int, fileName: String): MusicExportResult = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching MusicExportResult.FAILED
        resolver.openOutputStream(uri)?.use { out ->
            context.resources.openRawResource(rawId).use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        MusicExportResult.SAVED
    } else {
        val dir = File(context.cacheDir, "music").apply { mkdirs() }
        val file = File(dir, fileName)
        context.resources.openRawResource(rawId).use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        MusicExportResult.SHARED
    }
}.getOrDefault(MusicExportResult.FAILED)

enum class MusicExportResult {
    /** Enregistrée dans Téléchargements. */
    SAVED,

    /** Feuille de partage ouverte : le joueur choisit lui-même où la mettre. */
    SHARED,
    FAILED,
}
