package io.github.dyzydery.bahasakotak.io.pack

import java.io.File

/**
 * App-private folder that the laptop loop drops packs into over adb (`run-as … files/inbox/`). Home offers
 * the oldest one for import when the app comes to the foreground; the file is removed once handled.
 */
class PackInbox(private val dir: File) {
    fun next(): File? = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.minByOrNull { it.lastModified() }

    fun remove(file: File) {
        file.delete()
    }
}
