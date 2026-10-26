package io.github.dyzydery.bahasakotak

import android.content.Context
import android.net.Uri
import io.github.dyzydery.bahasakotak.data.BahasaDatabase
import io.github.dyzydery.bahasakotak.data.ContentRepository
import io.github.dyzydery.bahasakotak.data.StudyRepository
import io.github.dyzydery.bahasakotak.io.backup.BackupManager
import io.github.dyzydery.bahasakotak.io.transport.ContentTransport
import io.github.dyzydery.bahasakotak.io.transport.FileTransport
import io.github.dyzydery.bahasakotak.io.pack.PackImportService
import io.github.dyzydery.bahasakotak.io.pack.PackReader

/** Manual dependency wiring; one instance per process, owned by [BahasaKotakApp]. */
class AppContainer(private val context: Context) {
    val database by lazy { BahasaDatabase.open(context) }
    val contentRepository by lazy { ContentRepository(database) }
    val studyRepository by lazy { StudyRepository(database) }
    val transport: ContentTransport by lazy { FileTransport(context) }
    val backupManager by lazy { BackupManager(context) { database } }
    val appVersion: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    val packImportService by lazy {
        PackImportService(PackReader(asset(PACK_SCHEMA_ASSET)), contentRepository)
    }

    fun asset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    fun openWriter(uri: Uri) = context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()

    fun readUri(uri: Uri): String =
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
            .bufferedReader().use { it.readText() }

    companion object {
        const val PACK_SCHEMA_ASSET = "pack.schema.json"
        const val STARTER_PACK_ASSET = "starter.json"
        const val ANALYSIS_PROMPT_ASSET = "ANALYSIS_PROMPT.md"
    }
}
