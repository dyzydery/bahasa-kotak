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
import io.github.dyzydery.bahasakotak.domain.Leitner
import io.github.dyzydery.bahasakotak.domain.PlannerInput
import io.github.dyzydery.bahasakotak.domain.SessionPlanner
import io.github.dyzydery.bahasakotak.domain.StatsCalculator
import io.github.dyzydery.bahasakotak.io.export.ExportBuilder
import io.github.dyzydery.bahasakotak.io.export.ExportFile
import io.github.dyzydery.bahasakotak.io.transport.ContentTransport
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** What Home shows about today and overall progress. */
data class TodaySummary(
    val due: Int,
    val newAvailable: Int,
    val known: Int,
    val mastered: Int,
    val introduced: Int,
    val total: Int,
)

sealed interface ImportUiState {
    data object Idle : ImportUiState
    /** [inboxFile] is set when the pack came from the inbox; it is removed once the dialog is closed. */
    data class Showing(val preview: ImportPreview, val inboxFile: File? = null) : ImportUiState
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

    private val _today = MutableStateFlow<TodaySummary?>(null)
    val today = _today.asStateFlow()

    /** Home came to the foreground: refresh counts and offer any pack the laptop loop dropped in the inbox. */
    fun onResume() {
        refreshToday()
        checkInbox()
    }

    private fun checkInbox() {
        if (_importState.value != ImportUiState.Idle) return
        viewModelScope.launch {
            container.bundledImport.await()
            val file = withContext(Dispatchers.IO) { container.packInbox.next() } ?: return@launch
            val preview = withContext(Dispatchers.IO) {
                runCatching { importService.preview(file.readText()) }
                    .getOrElse { ImportPreview.Invalid(listOf("Nie udało się odczytać ${file.name}: ${it.message}")) }
            }
            _importState.value = ImportUiState.Showing(preview, inboxFile = file)
        }
    }

    /** Called whenever Home is shown, so the counts reflect the session just finished. */
    fun refreshToday() {
        viewModelScope.launch {
            container.bundledImport.await()
            val snapshot = container.studyRepository.snapshot()
            val now = System.currentTimeMillis()
            val cards = snapshot.cards.values.filter { it.key.itemId in snapshot.items }
            val input = PlannerInput(now, snapshot.cards, snapshot.items, snapshot.lessons, SessionPlanner.DEFAULT_TASK_LIMIT)
            val learnable = snapshot.items.values.filter { !it.placementOnly }
            val introduced = cards.mapTo(HashSet()) { it.key.itemId }
            _today.value = TodaySummary(
                due = cards.count { it.dueAt <= now },
                newAvailable = minOf(SessionPlanner.MAX_NEW_ITEMS, SessionPlanner.pickNewItems(input).size),
                known = cards.count { it.box >= StatsCalculator.KNOWN_BOX },
                mastered = cards.count { it.box == Leitner.MASTERED },
                introduced = learnable.count { it.id in introduced },
                total = learnable.size,
            )
        }
    }

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState = _importState.asStateFlow()

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
        val showing = _importState.value as? ImportUiState.Showing ?: return
        val preview = showing.preview as? ImportPreview.Ready ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                importService.apply(preview.plan)
                showing.inboxFile?.let(container.packInbox::remove)
            }
            container.refreshLatestExportAsync()
            refreshToday()
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

    private suspend fun buildExport(): ExportFile = container.exportService.build()

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
        // A declined or invalid inbox pack is dropped; the laptop keeps its copy in postepy/.
        (_importState.value as? ImportUiState.Showing)?.inboxFile?.let(container.packInbox::remove)
        _importState.value = ImportUiState.Idle
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
