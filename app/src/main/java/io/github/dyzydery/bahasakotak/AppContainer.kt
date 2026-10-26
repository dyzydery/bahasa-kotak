package io.github.dyzydery.bahasakotak

import android.content.Context
import android.net.Uri
import io.github.dyzydery.bahasakotak.data.BahasaDatabase
import io.github.dyzydery.bahasakotak.data.ContentRepository
import io.github.dyzydery.bahasakotak.io.pack.PackImportService
import io.github.dyzydery.bahasakotak.io.pack.PackReader

/** Manual dependency wiring; one instance per process, owned by [BahasaKotakApp]. */
class AppContainer(private val context: Context) {
    val database by lazy { BahasaDatabase.open(context) }
    val contentRepository by lazy { ContentRepository(database) }
    val packImportService by lazy {
        PackImportService(PackReader(asset(PACK_SCHEMA_ASSET)), contentRepository)
    }

    fun asset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    fun readUri(uri: Uri): String =
        checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $uri" }
            .bufferedReader().use { it.readText() }

    companion object {
        const val PACK_SCHEMA_ASSET = "pack.schema.json"
        const val STARTER_PACK_ASSET = "starter.json"
    }
}
