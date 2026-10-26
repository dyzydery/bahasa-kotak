package io.github.dyzydery.bahasakotak.io.transport

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.dyzydery.bahasakotak.io.export.ExportFile

/**
 * Copies the prompt and the export to the clipboard as one message. Unlike the share sheet, the clipboard
 * normally crosses Android profiles, so this reaches a Claude app installed in a work profile.
 */
class ClipboardTransport(private val context: Context) : ContentTransport {
    override suspend fun sendExport(file: ExportFile, prompt: String): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(file.fileName, message(file, prompt)))
        return null
    }

    companion object {
        fun message(file: ExportFile, prompt: String) =
            "$prompt\n\n## Eksport (${file.fileName})\n\n```json\n${file.json}\n```\n"
    }
}
