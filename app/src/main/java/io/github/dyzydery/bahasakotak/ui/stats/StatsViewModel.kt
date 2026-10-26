package io.github.dyzydery.bahasakotak.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dyzydery.bahasakotak.AppContainer
import io.github.dyzydery.bahasakotak.domain.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class StatsViewModel(container: AppContainer) : ViewModel() {
    private val _stats = MutableStateFlow<Stats?>(null)
    val stats = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            _stats.value = container.statsRepository.stats(Instant.now(), ZoneId.systemDefault())
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(container) }
        }
    }
}
