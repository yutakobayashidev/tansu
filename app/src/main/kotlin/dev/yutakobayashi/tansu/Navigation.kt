package dev.yutakobayashi.tansu

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
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
    entryProvider =
      entryProvider {
        entry<Home> { HomeScreen(onOpenSettings = { backStack.add(Settings) }) }
        entry<Settings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
      },
  )
}
