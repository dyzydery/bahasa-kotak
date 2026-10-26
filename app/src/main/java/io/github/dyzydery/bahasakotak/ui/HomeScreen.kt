package io.github.dyzydery.bahasakotak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dyzydery.bahasakotak.R
import io.github.dyzydery.bahasakotak.data.ContentStats
import io.github.dyzydery.bahasakotak.data.PackEntity
import io.github.dyzydery.bahasakotak.io.pack.ImportPreview

@Composable
fun HomeScreen(viewModel: HomeViewModel, onStart: () -> Unit, onPlacement: () -> Unit) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val placementDone by viewModel.placementDone.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val pickPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onPackFilePicked(uri)
    }
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.saveExport(uri)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.backup(uri)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri)
    }
    var confirmRestore by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.home_greeting), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart) { Text(stringResource(R.string.home_start)) }
            if (placementDone == false) {
                Spacer(Modifier.height(24.dp))
                PlacementOffer(onPlacement, viewModel::skipPlacement)
            }
            Spacer(Modifier.height(32.dp))
            ContentSummary(packs, stats)
            Spacer(Modifier.height(24.dp))
            ToolsSection(title = "Pętla z Claude") {
                OutlinedButton(onClick = viewModel::shareExport, Modifier.fillMaxWidth()) { Text("Wyślij eksport do Claude") }
                OutlinedButton(onClick = { saveExport.launch(viewModel.exportFileName()) }, Modifier.fillMaxWidth()) {
                    Text("Zapisz eksport do pliku")
                }
                // MIUI's file manager often reports .json as application/octet-stream, so accept anything
                // and let the schema validation reject what isn't a pack.
                OutlinedButton(onClick = { pickPack.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_import_pack))
                }
            }
            Spacer(Modifier.height(16.dp))
            ToolsSection(title = "Kopia zapasowa") {
                OutlinedButton(onClick = { saveBackup.launch(viewModel.backupFileName()) }, Modifier.fillMaxWidth()) {
                    Text("Utwórz kopię zapasową")
                }
                OutlinedButton(onClick = { confirmRestore = true }, Modifier.fillMaxWidth()) { Text("Przywróć z kopii") }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Przywrócić z kopii?") },
            text = { Text("Wszystkie obecne dane i postępy zostaną zastąpione zawartością kopii. Aplikacja uruchomi się ponownie.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; pickBackup.launch(arrayOf("*/*")) }) { Text("Wybierz plik") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Anuluj") } },
        )
    }

    when (val state = importState) {
        ImportUiState.Idle -> Unit
        is ImportUiState.Done -> InfoDialog(state.message, viewModel::dismissImport)
        is ImportUiState.Showing -> ImportPreviewDialog(state.preview, viewModel::confirmImport, viewModel::dismissImport)
    }
}

@Composable
private fun ToolsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun PlacementOffer(onStart: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Test poziomujący", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Około 20 krótkich pytań. To, co już znasz, pominie początkowe przegródki.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSkip) { Text("Pomiń") }
                Button(onClick = onStart) { Text("Rozpocznij test") }
            }
        }
    }
}

@Composable
private fun ContentSummary(packs: List<PackEntity>, stats: ContentStats?) {
    if (packs.isEmpty() || stats == null) return
    Text(
        text = "Paczki: " + packs.joinToString { "${it.title} (v${it.version})" } + "\n" +
            "${stats.topics} tematów · ${stats.lessons} lekcji · " +
            "${stats.items - stats.sentences} słów i zwrotów · ${stats.sentences} zdań",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (preview) {
        is ImportPreview.Ready -> {
            val plan = preview.plan
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(if (plan.isUpgrade) "Aktualizacja paczki" else "Nowa paczka") },
                text = {
                    Text(
                        "„${plan.pack.title}”, wersja ${plan.pack.packVersion}\n\n" +
                            "Nowe elementy: ${plan.newItemIds.size}\n" +
                            "Zaktualizowane: ${plan.updatedItemIds.size}\n" +
                            "Wycofane: ${plan.retiredItemIds.size}\n\n" +
                            "Postępy w nauce zostaną zachowane."
                    )
                },
                confirmButton = { TextButton(onClick = onConfirm) { Text("Importuj") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
            )
        }
        is ImportPreview.Rejected -> InfoDialog(preview.reason, onDismiss)
        is ImportPreview.Invalid -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Niepoprawna paczka") },
            text = {
                Text(
                    preview.errors.joinToString("\n") { "• $it" },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun InfoDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
