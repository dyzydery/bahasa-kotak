package io.github.dyzydery.bahasakotak.io.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.dyzydery.bahasakotak.data.BahasaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full database backup as a plain copy of the SQLite file (docs/SPEC.md §11), independent of the Claude export.
 */
class BackupManager(private val context: Context, private val database: () -> BahasaDatabase) {

    suspend fun backup(target: Uri) = withContext(Dispatchers.IO) {
        // Fold the WAL into the main file so a single-file copy is complete.
        database().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = checkNotNull(context.contentResolver.openOutputStream(target, "wt")) { "Cannot write $target" }
        out.use { stream -> dbFile().inputStream().use { it.copyTo(stream) } }
    }

    /**
     * Validates [source] as a Bahasa Kotak database, then swaps it in. Returns an error message, or null when the
     * file was installed and the caller should [restartApp].
     */
    suspend fun restore(source: Uri): String? = withContext(Dispatchers.IO) {
        val candidate = File(context.cacheDir, "restore-candidate.db")
        val input = context.contentResolver.openInputStream(source) ?: return@withContext "Nie udało się otworzyć pliku."
        input.use { stream -> candidate.outputStream().use { stream.copyTo(it) } }
        validate(candidate)?.let {
            candidate.delete()
            return@withContext it
        }
        database().close()
        listOf("", "-wal", "-shm").forEach { File(dbFile().path + it).delete() }
        candidate.copyTo(dbFile(), overwrite = true)
        candidate.delete()
        null
    }

    /** Relaunches the app so Room reopens (and migrates, if needed) the restored file. */
    fun restartApp() {
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }

    private fun validate(file: File): String? {
        val header = ByteArray(SQLITE_HEADER.size).also { buf -> file.inputStream().use { it.read(buf) } }
        if (!header.contentEquals(SQLITE_HEADER)) return "To nie jest plik bazy danych."
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                when {
                    !tables.containsAll(REQUIRED_TABLES) -> "To nie jest kopia zapasowa Bahasa Kotak."
                    db.version > CURRENT_VERSION -> "Kopia pochodzi z nowszej wersji aplikacji. Zaktualizuj aplikację."
                    else -> null
                }
            }
        } catch (e: Exception) {
            "Plik jest uszkodzony: ${e.message}"
        }
    }

    private fun dbFile() = context.getDatabasePath(BahasaDatabase.FILE_NAME)

    private companion object {
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val REQUIRED_TABLES = setOf("packs", "topics", "lessons", "items", "cards", "answers", "sessions")
        const val CURRENT_VERSION = BahasaDatabase.VERSION
    }
}
