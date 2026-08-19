package com.embyfusion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.embyfusion.ui.FusionApp
import com.embyfusion.ui.FusionViewModel
import com.embyfusion.ui.FusionViewModelFactory
import com.embyfusion.ui.theme.FusionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as FusionApplication
            val vm: FusionViewModel = viewModel(factory = FusionViewModelFactory(app.repository))
            FusionTheme { FusionApp(vm) }
        }
    }
}

