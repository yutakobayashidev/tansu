package dev.yutakobayashi.tansu.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** AC13: 設定未完了なら解析ボタンが無効で、設定へ誘導する。 */
class HomeScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int) = composeTestRule.activity.getString(id)

  @Test
  fun incompleteSettings_disablesAnalysisAndGuidesToSettings() {
    var analyzeClicks = 0
    var settingsOpened = 0
    composeTestRule.setContent {
      TansuTheme {
        HomeScreen(
          isSettingsComplete = false,
          onOpenSettings = { settingsOpened++ },
          onAnalyze = { analyzeClicks++ },
        )
      }
    }

    composeTestRule.onNodeWithTag(HomeTestTags.ANALYZE_BUTTON).assertIsNotEnabled().performClick()
    assertEquals(0, analyzeClicks)

    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.settings_required_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.settings_required_action)).performClick()
    assertEquals(1, settingsOpened)
  }

  @Test
  fun completeSettings_enablesAnalysisAndHidesNotice() {
    var analyzeClicks = 0
    composeTestRule.setContent {
      TansuTheme { HomeScreen(isSettingsComplete = true, onOpenSettings = {}, onAnalyze = { analyzeClicks++ }) }
    }

    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(HomeTestTags.ANALYZE_BUTTON).assertIsEnabled().performClick()
    assertEquals(1, analyzeClicks)
  }

  @Test
  fun completingSettings_enablesAnalysis() {
    var complete by mutableStateOf(false)
    composeTestRule.setContent {
      TansuTheme { HomeScreen(isSettingsComplete = complete, onOpenSettings = {}, onAnalyze = {}) }
    }
    composeTestRule.onNodeWithTag(HomeTestTags.ANALYZE_BUTTON).assertIsNotEnabled()

    complete = true

    composeTestRule.onNodeWithTag(HomeTestTags.ANALYZE_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertDoesNotExist()
  }
}
