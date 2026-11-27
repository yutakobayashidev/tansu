package dev.yutakobayashi.tansu.ui.review

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.data.analysis.AnalysisFailure
import dev.yutakobayashi.tansu.domain.Attention
import dev.yutakobayashi.tansu.domain.Candidate
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.ReadResultReview
import dev.yutakobayashi.tansu.ui.home.HomeTestTags
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReviewScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

  private var state by mutableStateOf<ReviewUiState>(ReviewUiState.Loading)
  private var analyzeClicks = 0
  private var saveClicks = 0
  private var settingsOpened = 0

  private fun show(initial: ReviewUiState, isSettingsComplete: Boolean = true) {
    state = initial
    composeTestRule.setContent {
      TansuTheme {
        ReviewScreen(
          title = "card.jpg",
          state = state,
          isSettingsComplete = isSettingsComplete,
          onBack = {},
          onOpenSettings = { settingsOpened++ },
          onAnalyze = { analyzeClicks++ },
          onSelect = { index, include ->
            val current = state as ReviewUiState.Reviewing
            state = current.copy(review = current.review.select(index, include))
          },
          onSave = { saveClicks++ },
        )
      }
    }
  }

  private fun candidate(fn: String?, vararg email: String) =
    Candidate.fromRaw(HASH, fn, CardDetails.fromRaw(org = "株式会社サンプル商事", email = email.toList()))

  @Test
  fun malformedEmailIsYellowMissingFnIsRedAndYellowCanStillBeSaved() {
    // AC20
    show(ReviewUiState.Reviewing(ReadResultReview.of(listOf(candidate(null), candidate("鈴木 一郎", "ichiro@@example")))))

    composeTestRule.onNodeWithTag(ReviewTestTags.candidate(0, Attention.Blocking)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.blockingNotice(0)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.property(0, "FN")).assertTextContains(string(R.string.review_empty_value))
    // 赤の候補は保存対象に入れられない。
    composeTestRule.onNodeWithTag(ReviewTestTags.toggle(0)).assertIsOff().assertIsNotEnabled()

    composeTestRule.onNodeWithTag(ReviewTestTags.PAGER).performTouchInput { swipeLeft() }
    composeTestRule.onNodeWithTag(ReviewTestTags.candidate(1, Attention.Warning)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.warningNotice(1)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.property(1, "EMAIL", 0)).assertTextContains("ichiro@@example")
    composeTestRule.onNodeWithTag(ReviewTestTags.toggle(1)).assertIsOn().assertIsEnabled()

    composeTestRule.onNodeWithTag(ReviewTestTags.SAVE_BUTTON)
      .assertTextContains(string(R.string.review_save, 1))
      .assertIsEnabled()
      .performClick()
    assertEquals(1, saveClicks)
  }

  @Test
  fun everyCandidateIsOnByDefaultAndTheSecondCanBeTurnedOff() {
    // AC15・AC16
    show(ReviewUiState.Reviewing(ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe"), candidate("佐藤 花子")))))
    composeTestRule.onNodeWithTag(ReviewTestTags.SAVE_BUTTON).assertTextContains(string(R.string.review_save, 3))
    composeTestRule.onNodeWithText(string(R.string.review_page, 1, 3)).assertIsDisplayed()

    composeTestRule.onNodeWithTag(ReviewTestTags.PAGER).performTouchInput { swipeLeft() }
    composeTestRule.onNodeWithText(string(R.string.review_page, 2, 3)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.toggle(1)).assertIsOn().performClick().assertIsOff()

    assertEquals(setOf(0, 2), (state as ReviewUiState.Reviewing).review.selected)
    composeTestRule.onNodeWithTag(ReviewTestTags.SAVE_BUTTON).assertTextContains(string(R.string.review_save, 2))
    // 候補を切り替えても画像は同じ位置に残る。
    composeTestRule.onNodeWithTag(ReviewTestTags.IMAGE).assertIsDisplayed()
  }

  @Test
  fun noCardsFoundShowsReasonAndIsNotAFailure() {
    // AC18
    show(ReviewUiState.Reviewing(ReadResultReview.of(emptyList())))

    composeTestRule.onNodeWithTag(ReviewTestTags.NO_CARDS_FOUND).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.review_no_cards_title)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.review_no_cards_body)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.FAILURE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ReviewTestTags.SAVE_BUTTON).performClick()
    assertEquals(1, saveClicks)
  }

  @Test
  fun candidatesSharingFnAreWarnedAndBothStayOn() {
    // AC27
    show(ReviewUiState.Reviewing(ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("山田 太郎")))))

    composeTestRule.onNodeWithTag(ReviewTestTags.sameFnNotice(0)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.toggle(0)).assertIsOn()
    composeTestRule.onNodeWithTag(ReviewTestTags.PAGER).performTouchInput { swipeLeft() }
    composeTestRule.onNodeWithTag(ReviewTestTags.sameFnNotice(1)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(ReviewTestTags.toggle(1)).assertIsOn()
    // 統合しないので 2 件とも保存対象のまま。
    composeTestRule.onNodeWithTag(ReviewTestTags.SAVE_BUTTON).assertTextContains(string(R.string.review_save, 2))
  }

  @Test
  fun uniqueFnHasNoSameNameWarning() {
    show(ReviewUiState.Reviewing(ReadResultReview.of(listOf(candidate("山田 太郎"), candidate("Jane Doe")))))

    composeTestRule.onNodeWithTag(ReviewTestTags.sameFnNotice(0)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ReviewTestTags.candidate(0, Attention.None)).assertIsDisplayed()
  }

  @Test
  fun incompleteSettingsDisableAnalysisAndGuideToSettings() {
    // AC13
    show(ReviewUiState.NotAnalyzed(), isSettingsComplete = false)

    composeTestRule.onNodeWithTag(ReviewTestTags.ANALYZE_BUTTON).assertIsNotEnabled().performClick()
    assertEquals(0, analyzeClicks)
    composeTestRule.onNodeWithTag(HomeTestTags.SETTINGS_REQUIRED_NOTICE).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.settings_required_action)).performClick()
    assertEquals(1, settingsOpened)
  }

  @Test
  fun analyzeButtonStartsAnalysisOnlyWhenTapped() {
    // AC2 の UI 側。解析は明示的なタップでだけ始まる。
    show(ReviewUiState.NotAnalyzed())
    composeTestRule.waitForIdle()
    assertEquals(0, analyzeClicks)

    composeTestRule.onNodeWithTag(ReviewTestTags.ANALYZE_BUTTON).assertIsEnabled().performClick()
    assertEquals(1, analyzeClicks)
  }

  @Test
  fun failureShowsReasonAndRetry() {
    show(ReviewUiState.NotAnalyzed(Failure.Analysis(AnalysisFailure.Authentication)))

    composeTestRule.onNodeWithTag(ReviewTestTags.FAILURE).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.review_failed_authentication)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.review_retry)).performClick()
    assertEquals(1, analyzeClicks)
  }

  private companion object {
    val HASH = ContentHash("a".repeat(64))
  }
}
