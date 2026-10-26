package io.github.dyzydery.bahasakotak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dyzydery.bahasakotak.data.ContentStats
import io.github.dyzydery.bahasakotak.data.PackEntity
import io.github.dyzydery.bahasakotak.io.pack.ImportPreview
import io.github.dyzydery.bahasakotak.ui.stats.ProportionBar
import io.github.dyzydery.bahasakotak.ui.stats.plural
import io.github.dyzydery.bahasakotak.ui.theme.heroGradient
import java.time.LocalTime

@Composable
fun HomeScreen(viewModel: HomeViewModel, onStart: () -> Unit, onPlacement: () -> Unit, onStats: () -> Unit) {
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val placementDone by viewModel.placementDone.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(today, onStart)
            if (placementDone == false) PlacementOffer(onPlacement, viewModel::skipPlacement)
            today?.let { ProgressCard(it, onStats) }

            SectionTitle("Pętla z Claude")
            ActionList {
                ActionRow(Icons.AutoMirrored.Filled.Send, "Kopiuj dla Claude", "Prompt i eksport trafią do schowka", viewModel::copyExport)
                ActionDivider()
                ActionRow(Icons.Filled.Add, "Importuj ze schowka", "Wklej całą odpowiedź z paczką", viewModel::importFromClipboard)
                ActionDivider()
                ActionRow(Icons.Filled.Share, "Udostępnij eksport", "Systemowe udostępnianie pliku", viewModel::shareExport)
                ActionDivider()
                ActionRow(Icons.AutoMirrored.Filled.ExitToApp, "Zapisz eksport do pliku", "Np. żeby przenieść na laptopa") {
                    saveExport.launch(viewModel.exportFileName())
                }
                ActionDivider()
                // MIUI's file manager often reports .json as application/octet-stream, so accept anything
                // and let the schema validation reject what isn't a pack.
                ActionRow(Icons.Filled.AddCircle, "Importuj paczkę z pliku", "Plik .json od Claude") {
                    pickPack.launch(arrayOf("*/*"))
                }
            }

            SectionTitle("Kopia zapasowa")
            ActionList {
                ActionRow(Icons.Filled.Lock, "Utwórz kopię zapasową", "Cała baza z postępami do pliku") {
                    saveBackup.launch(viewModel.backupFileName())
                }
                ActionDivider()
                ActionRow(Icons.Filled.Refresh, "Przywróć z kopii", "Zastępuje obecne dane") { confirmRestore = true }
            }

            ContentSummary(packs, stats)
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

/** Indonesian greeting for the time of day, with its Polish meaning. */
private fun greeting(now: LocalTime = LocalTime.now()): Pair<String, String> = when (now.hour) {
    in 4..10 -> "Selamat pagi!" to "dzień dobry (rano)"
    in 11..14 -> "Selamat siang!" to "dzień dobry (w południe)"
    in 15..18 -> "Selamat sore!" to "dzień dobry (po południu)"
    else -> "Selamat malam!" to "dobry wieczór"
}

@Composable
private fun Hero(today: TodaySummary?, onStart: () -> Unit) {
    val (hello, meaning) = remember { greeting() }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(heroGradient))
            .padding(24.dp)
    ) {
        Column {
            Text(hello, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(today?.let { plural(it.due, "powtórka", "powtórki", "powtórek") } ?: "…")
                Pill(today?.let { plural(it.newAvailable, "nowe słowo", "nowe słowa", "nowych słów") } ?: "…")
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zacznij", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun ProgressCard(today: TodaySummary, onStats: () -> Unit) {
    Card(
        onClick = onStats,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Twoje postępy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Statystyki", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Figure(today.mastered.toString(), "opanowane", Modifier.weight(1f))
                Figure(today.known.toString(), "znane", Modifier.weight(1f))
                Figure("${today.introduced}/${today.total}", "wprowadzone", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            ProportionBar(today.introduced.toFloat() / today.total.coerceAtLeast(1))
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun ActionList(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column { content() }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionDivider() = HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun PlacementOffer(onStart: () -> Unit, onSkip: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text("Test poziomujący", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Około 20 krótkich pytań. To, co już znasz, pominie początkowe przegródki.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
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
        text = packs.joinToString(" · ") { "${it.title} v${it.version}" } + "\n" +
            "${stats.topics} tematów · ${stats.lessons} lekcji · " +
            "${stats.items - stats.sentences} słów i zwrotów · ${stats.sentences} zdań",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "„${plan.pack.title}”, wersja ${plan.pack.packVersion}\n\n" +
                                "Nowe elementy: ${plan.newItemIds.size}\n" +
                                "Zaktualizowane: ${plan.updatedItemIds.size}\n" +
                                "Wycofane: ${plan.retiredItemIds.size}\n\n" +
                                "Postępy w nauce zostaną zachowane."
                        )
                        plan.pack.feedback?.let { feedback ->
                            Spacer(Modifier.height(16.dp))
                            Text("Uwagi od Claude", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(simpleMarkdown(feedback), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
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
