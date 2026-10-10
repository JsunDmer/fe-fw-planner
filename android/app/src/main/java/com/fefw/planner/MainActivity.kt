package com.fefw.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fefw.planner.ui.PlannerApp
import com.fefw.planner.ui.theme.Bg
import com.fefw.planner.ui.theme.FefwTheme
import com.fefw.planner.viewmodel.PlannerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            FefwTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    val vm: PlannerViewModel = viewModel()
                    PlannerApp(vm)
                }
            }
        }
    }
}