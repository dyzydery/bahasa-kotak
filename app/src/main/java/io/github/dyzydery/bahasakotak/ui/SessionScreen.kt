package io.github.dyzydery.bahasakotak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dyzydery.bahasakotak.domain.AnswerOutcome
import io.github.dyzydery.bahasakotak.domain.Direction
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.ItemKind
import io.github.dyzydery.bahasakotak.domain.Register
import io.github.dyzydery.bahasakotak.domain.StudyItem
import io.github.dyzydery.bahasakotak.domain.Task

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Zakończ") }
                val running = state as? SessionUiState.Running
                LinearProgressIndicator(
                    progress = { running?.let { it.answered.toFloat() / it.planned.coerceAtLeast(1) } ?: 0f },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                SessionUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is SessionUiState.Finished -> FinishedView(s, onExit)
                is SessionUiState.Running -> when (val task = s.task) {
                    is Task.Note -> NoteView(task, viewModel::next)
                    is Task.Intro -> IntroView(task.item, viewModel::next)
                    is Task.Ask -> AskView(task, s.feedback, viewModel::submit, viewModel::next)
                }
            }
        }
    }
}

@Composable
private fun NoteView(task: Task.Note, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Gramatyka", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(task.note.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            simpleMarkdown(task.note.body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Dalej") }
    }
}

@Composable
private fun IntroView(item: StudyItem, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Nowe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        RegisterChip(item.register)
        Spacer(Modifier.height(16.dp))
        Text(item.idText, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(item.plText, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        item.notes?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Dalej") }
    }
}

@Composable
private fun AskView(task: Task.Ask, feedback: AnswerOutcome?, onSubmit: (String) -> Unit, onNext: () -> Unit) {
    // Keyed by the task so a re-queued retry of the same card starts with an empty field.
    var answer by rememberSaveable(task) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(task) { focus.requestFocus() }
    val submitOrNext = { if (feedback == null) onSubmit(answer) else onNext() }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (task.direction == Direction.PL_ID) "Przetłumacz na indonezyjski" else "Przetłumacz na polski",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        RegisterChip(task.item.register)
        Spacer(Modifier.height(12.dp))
        Text(task.item.prompt(task.direction), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = { if (feedback == null) answer = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            readOnly = feedback != null,
            singleLine = task.item.kind != ItemKind.SENTENCE,
            placeholder = { Text("Twoja odpowiedź") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submitOrNext() }),
        )
        Spacer(Modifier.height(16.dp))
        feedback?.let { FeedbackCard(it) }
        Spacer(Modifier.weight(1f))
        if (feedback == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSubmit("") }, modifier = Modifier.weight(1f)) { Text("Nie wiem") }
                Button(onClick = { onSubmit(answer) }, modifier = Modifier.weight(2f)) { Text("Sprawdź") }
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Dalej") }
        }
    }
}

@Composable
private fun FeedbackCard(outcome: AnswerOutcome) {
    val colors = MaterialTheme.colorScheme
    val (title, container) = when (outcome.check.grade) {
        Grade.CORRECT -> "Dobrze!" to colors.primaryContainer
        Grade.TYPO -> "Prawie dobrze – literówka" to colors.tertiaryContainer
        Grade.WRONG -> "Niestety nie" to colors.errorContainer
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (outcome.check.grade != Grade.CORRECT || outcome.check.expected != outcome.userAnswer.trim()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (outcome.check.grade == Grade.TYPO) "Poprawna pisownia: ${outcome.check.expected}"
                    else "Poprawna odpowiedź: ${outcome.check.expected}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            outcome.task.item.notes?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RegisterChip(register: Register) {
    val label = when (register) {
        Register.FORMAL -> "formalny"
        Register.COLLOQUIAL -> "potoczny"
        Register.NEUTRAL -> return
    }
    AssistChip(onClick = {}, label = { Text(label) })
}

@Composable
private fun FinishedView(state: SessionUiState.Finished, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.nothingToDo) {
            Text("Na dziś wszystko zrobione", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Brak zaległych powtórek i nowego materiału. Wróć jutro.", textAlign = TextAlign.Center)
        } else {
            Text("Koniec sesji", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text("Dobrze: ${state.correct}")
            Text("Prawie dobrze: ${state.typo}")
            Text("Źle: ${state.wrong}")
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onExit) { Text("Wróć") }
    }
}
