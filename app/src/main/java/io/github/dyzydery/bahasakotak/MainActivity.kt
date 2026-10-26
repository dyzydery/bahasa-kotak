package io.github.dyzydery.bahasakotak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.dyzydery.bahasakotak.ui.HomeScreen
import io.github.dyzydery.bahasakotak.ui.HomeViewModel
import io.github.dyzydery.bahasakotak.ui.theme.BahasaKotakTheme

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as BahasaKotakApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BahasaKotakTheme {
                HomeScreen(homeViewModel, onStart = {})
            }
        }
    }
}
