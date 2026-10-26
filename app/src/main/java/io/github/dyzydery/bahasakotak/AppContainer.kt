package io.github.dyzydery.bahasakotak

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import io.github.dyzydery.bahasakotak.data.BahasaDatabase
import io.github.dyzydery.bahasakotak.data.ContentRepository
import io.github.dyzydery.bahasakotak.data.StatsRepository
import io.github.dyzydery.bahasakotak.data.StudyRepository
import io.github.dyzydery.bahasakotak.io.backup.BackupManager
import io.github.dyzydery.bahasakotak.io.export.ExportService
import io.github.dyzydery.bahasakotak.io.pack.PackImportService
import io.github.dyzydery.bahasakotak.io.pack.PackInbox
import io.github.dyzydery.bahasakotak.io.pack.PackReader
import io.github.dyzydery.bahasakotak.io.transport.ClipboardTransport
import io.github.dyzydery.bahasakotak.io.transport.ContentTransport
import io.github.dyzydery.bahasakotak.io.transport.FileTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency wiring; one instance per process, owned by [BahasaKotakApp]. */
class AppContainer(private val context: Context) {
    val database by lazy { BahasaDatabase.open(context) }
    val contentRepository by lazy { ContentRepository(database) }
    val studyRepository by lazy { StudyRepository(database) }
    val statsRepository by lazy { StatsRepository(database) }
    val shareTransport: ContentTransport by lazy { FileTransport(context) }
    val clipboardTransport: ContentTransport by lazy { ClipboardTransport(context) }
    val backupManager by lazy { BackupManager(context) { database } }
    val exportService by lazy {
        ExportService({ database }, { appVersion }, File(context.filesDir, LATEST_EXPORT_PATH))
    }
    val packInbox by lazy { PackInbox(File(context.filesDir, INBOX_DIR)) }
    val packImportService by lazy {
        PackImportService(PackReader(asset(PACK_SCHEMA_ASSET)), contentRepository)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Imports the bundled starter pack when the app ships a newer version than installed. Started with the
     * process (not with a screen, which may never draw while the phone is locked); readers await it.
     */
    val bundledImport: Deferred<Unit> by lazy {
        appScope.async { packImportService.importBundled(asset(STARTER_PACK_ASSET)) }
    }

    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }

    /** Process start: bundled import, then a fresh export for the laptop loop. */
    fun start() {
        appScope.launch {
            bundledImport.await()
            refreshLatestExport()
        }
    }

    /** Best effort: a failed background export must never break studying. */
    fun refreshLatestExportAsync() {
        appScope.launch { refreshLatestExport() }
    }

    private suspend fun refreshLatestExport() {
        runCatching { exportService.writeLatest() }
    }

    fun asset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    fun clipboardText(): String? {
        val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return null
        return (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context) }
            .takeIf { it.isNotBlank() }
    }

    fun openWriter(uri: Uri) = context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()

    fun readUri(uri: Uri): String =
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
            .bufferedReader().use { it.readText() }

    companion object {
        const val PACK_SCHEMA_ASSET = "pack.schema.json"
        const val STARTER_PACK_ASSET = "starter.json"
        const val ANALYSIS_PROMPT_ASSET = "ANALYSIS_PROMPT.md"

        /** Relative to filesDir; the scripts in tools/loop rely on these paths. */
        const val LATEST_EXPORT_PATH = "export/latest.json"
        const val INBOX_DIR = "inbox"
    }
}
