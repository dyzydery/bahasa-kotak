package io.github.dyzydery.bahasakotak.io.transport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.dyzydery.bahasakotak.io.export.ExportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shares the export through the system share sheet, with the analysis prompt as the message text. */
class FileTransport(private val context: Context) : ContentTransport {
    override suspend fun sendExport(file: ExportFile, prompt: String): String? {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the latest export around
            val out = File(dir, file.fileName).apply { writeText(file.json) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, prompt)
            clipData = ClipData.newRawUri(file.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Wyślij eksport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return null
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
