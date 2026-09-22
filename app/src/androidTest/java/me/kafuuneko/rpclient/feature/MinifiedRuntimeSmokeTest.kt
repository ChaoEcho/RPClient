package me.kafuuneko.rpclient.feature

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.feature.main.MainViewModel
import me.kafuuneko.rpclient.feature.main.presentation.MainPage
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.theme.AppThemeManager
import me.kafuuneko.rpclient.libs.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** 对真实应用类运行，防止只验证测试 APK 自己的反射而漏掉 R8 合并或移除观察者。 */
@RunWith(AndroidJUnit4::class)
class MinifiedRuntimeSmokeTest {
    @Test
    fun realMainViewModelDispatchesDistinctIntentsAfterShrinking() = runBlocking<Unit> {
        val original = AppThemeMode.fromPersistedValue(AppModel.themeMode)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var viewModel: MainViewModel
                scenario.onActivity { viewModel = ViewModelProvider(it)[MainViewModel::class.java] }
                withTimeout(30_000) { viewModel.uiStateFlow.filterIsInstance<MainUiState.Normal>().first() }
                viewModel.emit(MainUiIntent.SelectPage(MainPage.Settings))
                withTimeout(10_000) {
                    viewModel.uiStateFlow.filterIsInstance<MainUiState.Normal>().first { it.selectedPage == MainPage.Settings }
                }
                viewModel.emit(MainUiIntent.Back)
                withTimeout(10_000) {
                    viewModel.uiStateFlow.filterIsInstance<MainUiState.Normal>().first { it.selectedPage == MainPage.Home }
                }
                for (mode in listOf(AppThemeMode.Light, AppThemeMode.Dark)) {
                    viewModel.emit(MainUiIntent.SelectThemeMode(mode))
                    withTimeout(10_000) {
                        viewModel.uiStateFlow.filterIsInstance<MainUiState.Normal>().first {
                            it.settingsState.appearanceState.themeMode == mode
                        }
                    }
                    assertEquals(mode.persistedValue, AppModel.themeMode)
                }
            }
        } finally {
            GlobalContext.get().get<AppThemeManager>().setThemeMode(original)
        }
    }
}
