package dev.yutakobayashi.tansu

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.ui.detail.ImageDetailScreen
import dev.yutakobayashi.tansu.ui.detail.SavedImagesScreen
import dev.yutakobayashi.tansu.ui.home.HomeScreen
import dev.yutakobayashi.tansu.ui.review.ReviewScreen
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
        entry<Home> {
          HomeScreen(
            onOpenSettings = { backStack.add(Settings) },
            onOpenImage = { backStack.add(Review(it.uri, it.displayName)) },
            onOpenSavedImages = { backStack.add(SavedImages) },
          )
        }
        entry<Review> { key ->
          ReviewScreen(
            documentUri = key.documentUri,
            displayName = key.displayName,
            onBack = { backStack.removeLastOrNull() },
            onOpenSettings = { backStack.add(Settings) },
            // レビュー画面を画像詳細に置き換え、戻るとフォルダの一覧に戻るようにする。
            onOpenDetail = { hash ->
              if (backStack.lastOrNull() == key) {
                backStack.removeLastOrNull()
                backStack.add(ImageDetail(hash.value))
              }
            },
          )
        }
        entry<SavedImages> {
          SavedImagesScreen(
            onBack = { backStack.removeLastOrNull() },
            onOpenImage = { backStack.add(ImageDetail(it.value)) },
          )
        }
        entry<ImageDetail> { key ->
          ImageDetailScreen(sourceImage = ContentHash(key.contentHash), onBack = { backStack.removeLastOrNull() })
        }
        entry<Settings> { SettingsScreen(onBack = { backStack.removeLastOrNull() }) }
      },
  )
}
