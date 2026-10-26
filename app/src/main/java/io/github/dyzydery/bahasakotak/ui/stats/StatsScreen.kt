package io.github.dyzydery.bahasakotak.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.HardItem
import io.github.dyzydery.bahasakotak.domain.Stats
import io.github.dyzydery.bahasakotak.domain.TopicProgress
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć") }
                Text("Statystyki", style = MaterialTheme.typography.titleLarge)
            }
            when (val s = stats) {
                null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                else -> StatsContent(s)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: Stats) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Tiles(stats)

        Section("Aktywność – ostatnie 30 dni") {
            val total = stats.activity.sumOf { it.value }
            BarChart(
                values = stats.activity.map { it.value },
                labelFor = { i -> stats.activity[i].let { "${date(it.day)}: ${plural(it.value, "odpowiedź", "odpowiedzi", "odpowiedzi")}" } },
                summary = "${plural(total, "odpowiedź", "odpowiedzi", "odpowiedzi")} · nauka w ${stats.activeDays} z 30 dni",
                axisLabels = timeAxis(stats.activity.map { it.day }),
            )
        }

        Section("Znane karty w czasie") {
            LineChart(
                values = stats.knownOverTime.map { it.value },
                labelFor = { i -> stats.knownOverTime[i].let { "${date(it.day)}: ${plural(it.value, "karta", "karty", "kart")} w przegródce ≥ 3" } },
                summary = "Karty w przegródce 3 lub wyżej. Stuknij w wykres, żeby zobaczyć dzień.",
                axisLabels = timeAxis(stats.knownOverTime.map { it.day }),
            )
        }

        Section("Przegródki") {
            BarChart(
                values = stats.boxes,
                labelFor = { i -> "${BOX_NAMES[i]}: ${plural(stats.boxes[i], "karta", "karty", "kart")}" },
                summary = "${plural(stats.boxes.sum(), "karta", "karty", "kart")} w nauce",
                axisLabels = BOX_LABELS,
                height = 110.dp,
            )
        }

        Section("Tematy") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stats.topics.forEach { TopicRow(it) }
            }
        }

        Section("Najtrudniejsze") {
            if (stats.hardest.isEmpty()) {
                Muted("Za mało danych. Słowo pojawi się tu po co najmniej 3 próbach z błędem.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.hardest.forEach { HardRow(it) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Tiles(stats: Stats) {
    val accuracy = stats.recentAccuracy?.let { "${(it * 100).roundToInt()}%" } ?: "–"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Opanowane", stats.mastered.toString(), "kart", Modifier.weight(1f))
            Tile("Znane", stats.known.toString(), "kart w przegródce ≥ 3", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Poprawność", accuracy, "pierwszych odpowiedzi, 7 dni", Modifier.weight(1f))
            Tile("Wprowadzone", "${stats.introducedItems}/${stats.totalItems}", "słów i zdań", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, caption: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun TopicRow(progress: TopicProgress) {
    Column {
        Row {
            Text(progress.topic.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${progress.known}/${progress.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        ProportionBar(progress.known.toFloat() / progress.total.coerceAtLeast(1))
    }
}

@Composable
private fun HardRow(hard: HardItem) {
    val (from, to) = if (hard.direction == Direction.PL_ID) hard.item.plText to hard.item.idText else hard.item.idText to hard.item.plText
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(from, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "→ $to",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${hard.wrong + hard.typos}/${hard.attempts} błędów",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Muted(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("pl"))
private fun date(day: LocalDate) = DATE.format(day)

private fun timeAxis(days: List<LocalDate>) =
    if (days.isEmpty()) emptyList() else listOf(date(days.first()), date(days[days.size / 2]), date(days.last()))

private val BOX_NAMES = listOf("Przegródka 1", "Przegródka 2", "Przegródka 3", "Przegródka 4", "Przegródka 5", "Opanowane")
private val BOX_LABELS = listOf("1", "2", "3", "4", "5", "opan.")

/** Polish plural forms: 1 karta, 2–4 karty, 5+ kart (with 12–14 taking the "many" form). */
fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
