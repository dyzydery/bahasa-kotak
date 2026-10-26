package io.github.dyzydery.bahasakotak.io.transport

import io.github.dyzydery.bahasakotak.io.export.ExportFile

/**
 * The swappable channel that carries an export to Claude (docs/SPEC.md §2.1).
 *
 * - [FileTransport] (today): hands the file to the user via the Android share sheet; the pack comes back
 *   later as a file the user picks, so [sendExport] returns null.
 * - A future Frog API transport: POSTs the export to a small HTTP service that calls the Claude API and
 *   responds with the generated pack, returned here as JSON and fed straight into the import preview.
 */
interface ContentTransport {
    /** Sends [file]. Returns a pack's JSON when the transport gets one back directly, or null otherwise. */
    suspend fun sendExport(file: ExportFile, prompt: String): String?
}
