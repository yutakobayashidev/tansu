package dev.yutakobayashi.tansu.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun setContent(state: SettingsUiState) {
    composeTestRule.setContent { TansuTheme { SettingsScreen(state, {}, {}, {}, {}, {}, {}) } }
  }

  @Test
  fun emptySettings_disableSaveAndConnectionCheck() {
    setContent(SettingsUiState())

    composeTestRule.onNodeWithTag(SettingsTestTags.SAVE).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(SettingsTestTags.CHECK_CONNECTION).assertIsNotEnabled()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.settings_check_requires_saved))
      .assertExists()
  }

  @Test
  fun savedSettings_enableConnectionCheckAndShowResult() {
    setContent(
      SettingsUiState(
        baseUrl = "https://api.example.com/v1",
        model = "model-a",
        hasSavedApiKey = true,
        isSavedSettingsComplete = true,
        connectionCheck =
          ConnectionCheckState.Done(dev.yutakobayashi.tansu.data.settings.ConnectionCheckResult.Unauthorized(401)),
      )
    )

    composeTestRule.onNodeWithTag(SettingsTestTags.SAVE).assertIsEnabled()
    composeTestRule.onNodeWithTag(SettingsTestTags.CHECK_CONNECTION).assertIsEnabled()
    composeTestRule
      .onNodeWithText(composeTestRule.activity.getString(R.string.settings_check_unauthorized, 401))
      .assertExists()
  }
}
