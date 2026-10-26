package io.github.dyzydery.bahasakotak.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dyzydery.bahasakotak.AppContainer
import io.github.dyzydery.bahasakotak.data.ContentStats
import io.github.dyzydery.bahasakotak.data.PackEntity
import io.github.dyzydery.bahasakotak.data.SessionEntity
import io.github.dyzydery.bahasakotak.io.pack.ImportPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.dyzydery.bahasakotak.io.export.ExportBuilder
import io.github.dyzydery.bahasakotak.io.export.ExportFile
import io.github.dyzydery.bahasakotak.io.transport.ContentTransport
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Showing(val preview: ImportPreview) : ImportUiState
    data class Done(val message: String) : ImportUiState
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.contentRepository
    private val importService = container.packImportService

    val packs: StateFlow<List<PackEntity>> =
        repository.packs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats: StateFlow<ContentStats?> =
        repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val placementDone: StateFlow<Boolean?> =
        container.studyRepository.placementDone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState = _importState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            importService.importBundled(container.asset(AppContainer.STARTER_PACK_ASSET))
        }
    }

    fun onPackFilePicked(uri: Uri) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                try {
                    importService.preview(container.readUri(uri))
                } catch (e: Exception) {
                    ImportPreview.Invalid(listOf("Nie udało się odczytać pliku: ${e.message}"))
                }
            }
            _importState.value = ImportUiState.Showing(preview)
        }
    }

    fun confirmImport() {
        val preview = (_importState.value as? ImportUiState.Showing)?.preview as? ImportPreview.Ready ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { importService.apply(preview.plan) }
            _importState.value = ImportUiState.Done("Zaimportowano paczkę „${preview.plan.pack.title}”.")
        }
    }

    /** Skipping records an empty, finished placement session; basics are then treated as known. */
    fun skipPlacement() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            container.studyRepository.saveSession(
                SessionEntity(UUID.randomUUID().toString(), now, now, taskCount = 0, placement = true)
            )
        }
    }

    /** Builds the export and hands it to the transport; a transport that returns a pack goes to import preview. */
    fun shareExport() = sendExport(container.shareTransport, confirmation = null)

    fun copyExport() = sendExport(
        container.clipboardTransport,
        confirmation = "Skopiowano prompt i eksport. Wklej je w rozmowie z Claude.",
    )

    private fun sendExport(transport: ContentTransport, confirmation: String?) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { buildExport() }
            val prompt = withContext(Dispatchers.IO) { container.asset(AppContainer.ANALYSIS_PROMPT_ASSET) }
            val pack = transport.sendExport(file, prompt)
            _importState.value = when {
                pack != null -> ImportUiState.Showing(importService.preview(pack))
                confirmation != null -> ImportUiState.Done(confirmation)
                else -> ImportUiState.Idle
            }
        }
    }

    fun importFromClipboard() {
        val text = container.clipboardText()
        if (text == null) {
            _importState.value = ImportUiState.Done("Schowek jest pusty. Skopiuj odpowiedź Claude z paczką.")
            return
        }
        viewModelScope.launch {
            _importState.value = ImportUiState.Showing(withContext(Dispatchers.IO) { importService.preview(text) })
        }
    }

    fun saveExport(target: Uri) = runReporting("Zapisano eksport.") {
        val file = buildExport()
        checkNotNull(container.openWriter(target)).use { it.write(file.json) }
    }

    fun exportFileName(): String = ExportBuilder.fileName(Instant.now(), ZoneId.systemDefault())

    fun backup(target: Uri) = runReporting("Kopia zapasowa zapisana.") { container.backupManager.backup(target) }

    fun backupFileName(): String =
        "bahasa-kotak-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now()) + ".db"

    fun restore(source: Uri) {
        viewModelScope.launch {
            val error = container.backupManager.restore(source)
            if (error == null) container.backupManager.restartApp()
            else _importState.value = ImportUiState.Done(error)
        }
    }

    private suspend fun buildExport(): ExportFile {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val data = container.database.exportDao().exportData()
        return ExportBuilder.toFile(ExportBuilder.build(data, container.appVersion, now, zone), now, zone)
    }

    private fun runReporting(success: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { action() }
                success
            } catch (e: Exception) {
                "Nie udało się: ${e.message}"
            }
            _importState.value = ImportUiState.Done(message)
        }
    }

    fun dismissImport() {
        _importState.value = ImportUiState.Idle
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
