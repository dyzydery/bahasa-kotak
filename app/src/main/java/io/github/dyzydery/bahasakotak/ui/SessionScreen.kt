package io.github.dyzydery.bahasakotak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dyzydery.bahasakotak.domain.AnswerOutcome
import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.ExerciseType
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Leitner
import io.github.dyzydery.bahasakotak.domain.Register
import io.github.dyzydery.bahasakotak.domain.StudyItem
import io.github.dyzydery.bahasakotak.domain.Task
import io.github.dyzydery.bahasakotak.ui.theme.heroGradient

@Composable
fun SessionScreen(viewModel: SessionViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onExit)

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val running = state as? SessionUiState.Running
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Zakończ") }
                LinearProgressIndicator(
                    progress = { running?.let { it.answered.toFloat() / it.total.coerceAtLeast(1) } ?: 0f },
                    modifier = Modifier.weight(1f).height(10.dp),
                    strokeCap = StrokeCap.Round,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    running?.let { "${it.answered}/${it.total}" } ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                SessionUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is SessionUiState.Finished -> FinishedView(s, onExit)
                is SessionUiState.Running -> when (val task = s.task) {
                    is Task.Note -> NoteView(task, viewModel::next)
                    is Task.Intro -> IntroView(task.item, viewModel::next)
                    is Task.Ask ->
                        if (task.exerciseType == ExerciseType.BLOCKS) BlocksView(task, s.box, s.feedback, viewModel::submit, viewModel::next)
                        else AskView(task, s.box, s.feedback, viewModel::submit, viewModel::next)
                }
            }
        }
    }
}

@Composable
private fun NoteView(task: Task.Note, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Tag("Gramatyka", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.height(12.dp))
        Text(task.note.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        // The scrollable area takes the free space; the card inside it only as tall as the note.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(simpleMarkdown(task.note.body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryAction("Rozumiem", onNext)
    }
}

@Composable
private fun IntroView(item: StudyItem, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("Nowe", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            RegisterTag(item.register)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(heroGradient))
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column {
                Text(item.idText, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(item.plText, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
                if (item.acceptedId.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Też: " + item.acceptedId.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
        item.notes?.let {
            Spacer(Modifier.height(16.dp))
            Hint(it)
        }
        Spacer(Modifier.weight(1f))
        PrimaryAction("Dalej", onNext)
    }
}

@Composable
private fun QuestionHeader(task: Task.Ask, box: Int?, instruction: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tag(
            if (task.direction == Direction.PL_ID) "PL → ID" else "ID → PL",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        box?.let { BoxTag(it) }
        RegisterTag(task.item.register)
        if (task.retry) Tag("Jeszcze raz", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
    }
    Spacer(Modifier.height(16.dp))
    Text(instruction, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            task.item.prompt(task.direction),
            style = if (task.item.kind == ItemKind.SENTENCE) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )
    }
}

@Composable
private fun AskView(task: Task.Ask, box: Int?, feedback: AnswerOutcome?, onSubmit: (String) -> Unit, onNext: () -> Unit) {
    // Keyed by the task so a re-queued retry of the same card starts with an empty field.
    var answer by rememberSaveable(task) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(task) { focus.requestFocus() }
    val canSubmit = answer.isNotBlank()
    val submitOrNext = {
        if (feedback != null) onNext() else if (canSubmit) onSubmit(answer)
    }

    Column(Modifier.fillMaxSize()) {
        QuestionHeader(task, box, if (task.direction == Direction.PL_ID) "Napisz po indonezyjsku" else "Napisz po polsku")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { if (feedback == null) answer = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            readOnly = feedback != null,
            singleLine = task.item.kind != ItemKind.SENTENCE,
            textStyle = MaterialTheme.typography.titleLarge,
            shape = MaterialTheme.shapes.medium,
            placeholder = { Text("Twoja odpowiedź", style = MaterialTheme.typography.titleLarge) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitOrNext() }),
        )
        Spacer(Modifier.height(16.dp))
        FeedbackArea(feedback)
        Spacer(Modifier.weight(1f))
        AnswerActions(feedback, canSubmit = canSubmit, onSubmit = { onSubmit(answer) }, onSkip = { onSubmit("") }, onNext = onNext)
    }
}

/** Tap tiles in the pool to build the answer; tap a placed tile to put it back. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlocksView(task: Task.Ask, box: Int?, feedback: AnswerOutcome?, onSubmit: (String) -> Unit, onNext: () -> Unit) {
    // Indices into task.tiles, so repeated words stay distinct.
    val placed = remember(task) { mutableStateListOf<Int>() }
    val answer = placed.joinToString(" ") { task.tiles[it] }

    Column(Modifier.fillMaxSize()) {
        QuestionHeader(task, box, if (task.direction == Direction.PL_ID) "Ułóż po indonezyjsku" else "Ułóż po polsku")
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (placed.isEmpty()) {
                Text(
                    "Stuknij słowa poniżej",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                placed.forEach { index ->
                    WordTile(task.tiles[index], placed = true, enabled = feedback == null) { placed.remove(index) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            task.tiles.indices.filter { it !in placed }.forEach { index ->
                WordTile(task.tiles[index], placed = false, enabled = feedback == null) { placed.add(index) }
            }
        }
        Spacer(Modifier.height(16.dp))
        FeedbackArea(feedback)
        Spacer(Modifier.weight(1f))
        AnswerActions(feedback, canSubmit = placed.isNotEmpty(), onSubmit = { onSubmit(answer) }, onSkip = { onSubmit("") }, onNext = onNext)
    }
}

@Composable
private fun WordTile(text: String, placed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = if (placed) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (placed) colors.onPrimaryContainer else colors.onSurface,
        border = if (placed) null else BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = if (placed) 0.dp else 1.dp,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun AnswerActions(
    feedback: AnswerOutcome?,
    canSubmit: Boolean,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    if (feedback == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip, modifier = Modifier.weight(1f).height(56.dp)) { Text("Nie wiem") }
            Button(onClick = onSubmit, enabled = canSubmit, modifier = Modifier.weight(2f).height(56.dp)) {
                Text("Sprawdź", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        PrimaryAction("Dalej", onNext)
    }
}

@Composable
private fun FeedbackArea(feedback: AnswerOutcome?) {
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + slideInVertically { it / 3 },
    ) {
        // Keep the last outcome on screen while the card animates.
        feedback?.let { FeedbackCard(it) }
    }
}

@Composable
private fun FeedbackCard(outcome: AnswerOutcome) {
    val colors = MaterialTheme.colorScheme
    data class Look(val title: String, val icon: ImageVector, val container: Color, val content: Color)
    val look = when (outcome.check.grade) {
        Grade.CORRECT -> Look("Dobrze!", Icons.Filled.CheckCircle, colors.primaryContainer, colors.onPrimaryContainer)
        Grade.TYPO -> Look("Prawie dobrze – literówka", Icons.Filled.Info, colors.secondaryContainer, colors.onSecondaryContainer)
        Grade.WRONG -> Look("Niestety nie", Icons.Filled.Warning, colors.errorContainer, colors.onErrorContainer)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = look.container, contentColor = look.content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(look.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(look.title, style = MaterialTheme.typography.titleMedium)
                if (outcome.check.grade != Grade.CORRECT || outcome.check.expected != outcome.userAnswer.trim()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (outcome.check.grade == Grade.TYPO) "Poprawna pisownia:" else "Poprawna odpowiedź:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(outcome.check.expected, style = MaterialTheme.typography.titleLarge)
                }
                outcome.task.item.notes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun BoxTag(box: Int) {
    val text = if (box == Leitner.MASTERED) "Opanowane" else "Kotak $box"
    Tag(text, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RegisterTag(register: Register) {
    val colors = MaterialTheme.colorScheme
    when (register) {
        Register.FORMAL -> Tag("formalny", colors.primaryContainer, colors.onPrimaryContainer)
        Register.OFFICE -> Tag("Jakarta · biuro", colors.tertiaryContainer, colors.onTertiaryContainer)
        Register.COLLOQUIAL -> Tag("potoczny (gaul)", colors.secondaryContainer, colors.onSecondaryContainer)
        Register.NEUTRAL -> Unit
    }
}

@Composable
private fun Hint(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FinishedView(state: SessionUiState.Finished, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(heroGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        if (state.nothingToDo) {
            Text("Na dziś wszystko zrobione", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Brak zaległych powtórek i nowego materiału. Sampai besok – do jutra!",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (state.placement) "Test zakończony" else "Bagus! Koniec sesji",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            val colors = MaterialTheme.colorScheme
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultTile(state.correct, "dobrze", colors.primaryContainer, colors.onPrimaryContainer, Modifier.weight(1f))
                ResultTile(state.typo, "literówki", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f))
                ResultTile(state.wrong, "źle", colors.errorContainer, colors.onErrorContainer, Modifier.weight(1f))
            }
            if (state.placement) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Znane słowa trafiły od razu do wyższych przegródek. Sprawdzisz je w zwykłych powtórkach.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        PrimaryAction("Wróć", onExit)
    }
}

@Composable
private fun ResultTile(value: Int, label: String, container: Color, content: Color, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
