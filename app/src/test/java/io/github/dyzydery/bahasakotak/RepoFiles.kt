package io.github.dyzydery.bahasakotak

import java.io.File

/** Files at the repository root (schemas/, packs/), shared between the app and Claude. */
object RepoFiles {
    private val root = File(checkNotNull(System.getProperty("repoRoot")) { "repoRoot system property not set" })

    fun read(path: String): String = File(root, path).readText()

    val packSchema get() = read("schemas/pack.schema.json")
    val starterPack get() = read("packs/starter.json")
}
