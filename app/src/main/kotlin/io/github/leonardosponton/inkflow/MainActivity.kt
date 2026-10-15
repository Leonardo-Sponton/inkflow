package io.github.leonardosponton.inkflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import io.github.leonardosponton.inkflow.core.designsystem.InkflowTheme
import io.github.leonardosponton.inkflow.core.model.ThemeMode
import io.github.leonardosponton.inkflow.core.model.UserSettings
import io.github.leonardosponton.inkflow.domain.project.SettingsRepository
import io.github.leonardosponton.inkflow.feature.editor.EditorScreen
import io.github.leonardosponton.inkflow.feature.editor.EditorViewModel
import io.github.leonardosponton.inkflow.feature.home.HomeScreen
import io.github.leonardosponton.inkflow.feature.settings.SettingsScreen
import io.github.leonardosponton.inkflow.navigation.EditorRoute
import io.github.leonardosponton.inkflow.navigation.HomeRoute
import io.github.leonardosponton.inkflow.navigation.SettingsRoute
import io.github.leonardosponton.inkflow.samples.SampleSeeder
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var seeder: SampleSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        seedIfRequested()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(UserSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            InkflowTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) { AppNavigation() }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        seedIfRequested()
    }

    /** Debug/benchmark builds only: `--es seed all|flowchart|uml|db|shapes|stress`. */
    private fun seedIfRequested() {
        val kind = intent?.getStringExtra(EXTRA_SEED) ?: return
        if (!BuildConfig.DEBUG && BuildConfig.BUILD_TYPE != "benchmark") return
        intent.removeExtra(EXTRA_SEED)
        lifecycleScope.launch { seeder.seed(kind) }
    }

    private companion object {
        const val EXTRA_SEED = "seed"
    }
}

@androidx.compose.runtime.Composable
private fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onOpenProject = { backStack.add(EditorRoute(it.value)) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                )
            }
            entry<EditorRoute> { route ->
                val vm = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(creationCallback = { it.create(route.projectId) })
                EditorScreen(viewModel = vm, onBack = { backStack.removeLastOrNull() })
            }
            entry<SettingsRoute> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
