package io.github.dyzydery.bahasakotak.io.export

import io.github.dyzydery.bahasakotak.data.BahasaDatabase
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Builds exports, and keeps an always-fresh copy in [latestFile] (app-private storage) so the laptop loop
 * (`/petla` in Claude Code) can pull it over adb without anyone tapping "export" first.
 */
class ExportService(
    private val database: () -> BahasaDatabase,
    private val appVersion: () -> String,
    val latestFile: File,
) {
    suspend fun build(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): ExportFile {
        val data = database().exportDao().exportData()
        return ExportBuilder.toFile(ExportBuilder.build(data, appVersion(), now, zone), now, zone)
    }

    /** Rewrites [latestFile] atomically, so a pull in progress never sees half a file. */
    suspend fun writeLatest() {
        val file = build()
        latestFile.parentFile?.mkdirs()
        val tmp = File(latestFile.path + ".tmp")
        tmp.writeText(file.json)
        tmp.renameTo(latestFile)
    }
}
