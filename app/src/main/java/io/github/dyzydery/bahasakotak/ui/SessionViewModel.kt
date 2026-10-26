package io.github.dyzydery.bahasakotak.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dyzydery.bahasakotak.AppContainer
import io.github.dyzydery.bahasakotak.data.SessionEntity
import io.github.dyzydery.bahasakotak.domain.AnswerOutcome
import io.github.dyzydery.bahasakotak.domain.Grade
import io.github.dyzydery.bahasakotak.domain.PlacementEngine
import io.github.dyzydery.bahasakotak.domain.PlacementPlanner
import io.github.dyzydery.bahasakotak.domain.PlannerInput
import io.github.dyzydery.bahasakotak.domain.SessionEngine
import io.github.dyzydery.bahasakotak.domain.SessionPlanner
import io.github.dyzydery.bahasakotak.domain.SessionRunner
import io.github.dyzydery.bahasakotak.domain.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

sealed interface SessionUiState {
    data object Loading : SessionUiState
    data class Running(
        val task: Task,
        val feedback: AnswerOutcome?,
        val answered: Int,
        val planned: Int,
    ) : SessionUiState

    data class Finished(val correct: Int, val typo: Int, val wrong: Int, val placement: Boolean) : SessionUiState {
        val nothingToDo get() = correct + typo + wrong == 0
    }
}

class SessionViewModel(container: AppContainer, private val placement: Boolean) : ViewModel() {
    private val repository = container.studyRepository
    private val zone = ZoneId.systemDefault()
    private val sessionId = UUID.randomUUID().toString()
    private val startedAt = Instant.now()
    private lateinit var engine: SessionRunner
    private var questionShownAt = 0L

    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = repository.snapshot()
            engine = if (placement) {
                val tasks = PlacementPlanner.plan(snapshot.lessons, snapshot.cards)
                val introduced = snapshot.cards.keys.mapTo(HashSet()) { it.itemId }
                PlacementEngine(tasks, snapshot.lessons, introduced, zone)
            } else {
                val since = startedAt.minus(Duration.ofDays(7)).toEpochMilli()
                val tasks = SessionPlanner.plan(
                    PlannerInput(
                        nowMillis = startedAt.toEpochMilli(),
                        cards = snapshot.cards,
                        items = snapshot.items,
                        lessons = snapshot.lessons,
                        taskLimit = SessionPlanner.taskLimit(repository.durationsSince(since)),
                    )
                )
                SessionEngine(tasks, snapshot.cards, zone)
            }
            if (engine.plannedQuestions > 0) repository.saveSession(sessionEntity(endedAt = null))
            showCurrent()
        }
    }

    fun submit(answer: String) {
        val running = _state.value as? SessionUiState.Running ?: return
        if (running.task !is Task.Ask || running.feedback != null) return
        val now = Instant.now()
        val outcome = engine.submit(answer, now, SystemClock.elapsedRealtime() - questionShownAt)
        _state.value = running.copy(feedback = outcome, answered = engine.answeredQuestions)
        viewModelScope.launch {
            if (placement) repository.logAnswer(outcome, sessionId) else repository.saveAnswer(outcome, sessionId)
        }
    }

    fun next() {
        if (_state.value !is SessionUiState.Running) return
        engine.advance()
        viewModelScope.launch { showCurrent() }
    }

    private suspend fun showCurrent() {
        when (val task = engine.current) {
            null -> finish()
            is Task.Note -> repository.markGrammarNoteSeen(task.lessonId)
            is Task.Intro -> repository.saveCards(engine.introduce(task.item, Instant.now()))
            is Task.Ask -> questionShownAt = SystemClock.elapsedRealtime()
        }
        engine.current?.let { task ->
            _state.value = SessionUiState.Running(task, null, engine.answeredQuestions, engine.plannedQuestions)
        }
    }

    private suspend fun finish() {
        val g = engine.grades
        val now = Instant.now()
        (engine as? PlacementEngine)?.let { repository.saveCards(it.resultCards(now)) }
        // A placement test is always recorded, even when empty, so Home stops offering it.
        if (engine.plannedQuestions > 0 || placement) repository.saveSession(sessionEntity(endedAt = now))
        _state.value = SessionUiState.Finished(g[Grade.CORRECT] ?: 0, g[Grade.TYPO] ?: 0, g[Grade.WRONG] ?: 0, placement)
    }

    private fun sessionEntity(endedAt: Instant?) = SessionEntity(
        id = sessionId,
        startedAt = startedAt.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        taskCount = if (::engine.isInitialized) engine.answeredQuestions else 0,
        placement = placement,
    )

    companion object {
        fun factory(container: AppContainer, placement: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(container, placement) }
        }
    }
}
