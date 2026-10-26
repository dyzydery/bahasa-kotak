package io.github.dyzydery.bahasakotak.io.pack

import io.github.dyzydery.bahasakotak.data.ContentRepository

sealed interface ImportPreview {
    data class Invalid(val errors: List<String>) : ImportPreview
    data class Rejected(val reason: String) : ImportPreview
    data class Ready(val plan: ImportPlan.Apply) : ImportPreview
}

/** Entry point for every pack import, whatever transport delivered the file. */
class PackImportService(
    private val reader: PackReader,
    private val repository: ContentRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun preview(text: String): ImportPreview {
        val pack = when (val read = reader.read(PackText.extract(text))) {
            is PackReadResult.Invalid -> return ImportPreview.Invalid(read.errors)
            is PackReadResult.Valid -> read.pack
        }
        return when (val plan = ImportPlanner.plan(pack, repository.installedContent(pack.packId))) {
            is ImportPlan.Apply -> ImportPreview.Ready(plan)
            is ImportPlan.AlreadyInstalled ->
                ImportPreview.Rejected("Paczka „${pack.title}” w wersji ${plan.version} jest już zainstalowana.")
            is ImportPlan.Downgrade ->
                ImportPreview.Rejected(
                    "Zainstalowana jest nowsza wersja paczki „${pack.title}” (${plan.installed}) niż importowana (${plan.offered})."
                )
        }
    }

    suspend fun apply(plan: ImportPlan.Apply) = repository.apply(plan, clock())

    /** Imports the bundled starter pack on first launch, or when the app ships a newer version of it. */
    suspend fun importBundled(text: String) {
        val preview = preview(text)
        if (preview is ImportPreview.Ready) apply(preview.plan)
    }
}
