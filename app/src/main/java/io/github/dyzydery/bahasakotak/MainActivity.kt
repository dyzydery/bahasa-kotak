package io.github.dyzydery.bahasakotak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dyzydery.bahasakotak.ui.HomeScreen
import io.github.dyzydery.bahasakotak.ui.theme.BahasaKotakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BahasaKotakTheme {
                HomeScreen(onStart = {})
            }
        }
    }
}
