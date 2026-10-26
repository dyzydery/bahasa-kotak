package io.github.dyzydery.bahasakotak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dyzydery.bahasakotak.ui.HomeScreen
import io.github.dyzydery.bahasakotak.ui.HomeViewModel
import io.github.dyzydery.bahasakotak.ui.SessionScreen
import io.github.dyzydery.bahasakotak.ui.SessionViewModel
import io.github.dyzydery.bahasakotak.ui.stats.StatsScreen
import io.github.dyzydery.bahasakotak.ui.stats.StatsViewModel
import io.github.dyzydery.bahasakotak.ui.theme.BahasaKotakTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as BahasaKotakApp).container
    private val homeViewModel: HomeViewModel by viewModels { HomeViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BahasaKotakTheme {
                // 0 = home; n > 0 = the n-th session started in this activity (keys a fresh SessionViewModel).
                var session by rememberSaveable { mutableIntStateOf(0) }
                var lastSession by rememberSaveable { mutableIntStateOf(0) }
                var placement by rememberSaveable { mutableStateOf(false) }
                // 0 = closed; n > 0 = the n-th visit, so each visit recomputes the numbers.
                var statsVisit by rememberSaveable { mutableIntStateOf(0) }
                var lastStatsVisit by rememberSaveable { mutableIntStateOf(0) }
                if (statsVisit > 0) {
                    val vm: StatsViewModel = viewModel(key = "stats-$statsVisit", factory = StatsViewModel.factory(container))
                    StatsScreen(vm, onBack = { statsVisit = 0 })
                } else if (session == 0) {
                    HomeScreen(
                        homeViewModel,
                        onStart = { placement = false; lastSession++; session = lastSession },
                        onPlacement = { placement = true; lastSession++; session = lastSession },
                        onStats = { lastStatsVisit++; statsVisit = lastStatsVisit },
                    )
                } else {
                    val vm: SessionViewModel = viewModel(
                        key = "session-$session",
                        factory = SessionViewModel.factory(container, placement),
                    )
                    SessionScreen(vm, onExit = { session = 0 })
                }
            }
        }
    }
}
