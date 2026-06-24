package com.pasoseguro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.pasoseguro.app.data.PreferencesRepository
import com.pasoseguro.app.data.UserPreferences
import com.pasoseguro.app.navigation.NavGraph
import com.pasoseguro.app.ui.LocalUserPreferences
import com.pasoseguro.app.ui.theme.PasoSeguroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = PreferencesRepository.getInstance(this)

        enableEdgeToEdge()
        setContent {
            val prefs by repository.userPreferences.collectAsState(
                initial = UserPreferences.Default,
            )

            CompositionLocalProvider(LocalUserPreferences provides prefs) {
                PasoSeguroTheme(
                    highContrast = prefs.highContrast,
                    largeFont    = prefs.largeFont,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color    = MaterialTheme.colorScheme.background,
                    ) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            repository    = repository,
                        )
                    }
                }
            }
        }
    }
}
