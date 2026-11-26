package dev.yutakobayashi.tansu

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.yutakobayashi.tansu.ui.home.HomeScreen
import dev.yutakobayashi.tansu.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Home)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // 画面ごとに ViewModel を持たせ、画面を閉じたら破棄する。
    // ないと ViewModel が Activity に残り、設定画面を開き直しても前回の状態が出る。
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Home> { HomeScreen(onOpenSettings = { backStack.add(Settings) }) }
        entry<Settings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
      },
  )
}
