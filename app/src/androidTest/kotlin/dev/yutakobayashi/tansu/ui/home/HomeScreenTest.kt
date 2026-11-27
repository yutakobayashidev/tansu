package dev.yutakobayashi.tansu.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.domain.FolderImage
import dev.yutakobayashi.tansu.domain.ImageProgress
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * AC13: 設定未完了なら設定へ誘導する。解析ボタン自体はレビュー画面にあり、ReviewScreenTest で確かめる。
 */
class HomeScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int) = composeTestRule.activity.getString(id)

  @Test
  fun incompleteSettingsGuidesToSettings() {
    var settingsOpened = 0
    composeTestRule.setContent {
      TansuTheme { HomeScreen(isSettingsComplete = false, onOpenSettings = { settingsOpened++ }) }
    }

    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.settings_required_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.settings_required_action)).performClick()
    assertEquals(1, settingsOpened)
  }

  @Test
  fun completingSettingsHidesNotice() {
    var complete by mutableStateOf(false)
    composeTestRule.setContent { TansuTheme { HomeScreen(isSettingsComplete = complete, onOpenSettings = {}) } }
    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertIsDisplayed()

    complete = true

    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertDoesNotExist()
  }

  @Test
  fun tappingAnImageOpensIt() {
    val image = FolderImage("content://tree/document/card-a.jpg", "card-a.jpg", 1, 1, ImageProgress.Unprocessed)
    val opened = mutableListOf<FolderImage>()
    composeTestRule.setContent {
      TansuTheme {
        HomeScreen(
          isSettingsComplete = true,
          onOpenSettings = {},
          onOpenImage = { opened += it },
          folder = FolderUiState.Loaded("名刺", listOf(image)),
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("card-a.jpg", substring = true).performClick()

    assertEquals(listOf(image), opened)
  }
}
