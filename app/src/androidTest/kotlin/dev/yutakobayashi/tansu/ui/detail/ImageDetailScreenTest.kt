package dev.yutakobayashi.tansu.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.Uid
import dev.yutakobayashi.tansu.ui.theme.TansuTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImageDetailScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun string(id: Int, vararg args: Any) = composeTestRule.activity.getString(id, *args)

  private val deleted = mutableListOf<Uid>()
  private var shares = 0

  private fun show(state: ImageDetailUiState) {
    composeTestRule.setContent {
      TansuTheme {
        ImageDetailScreen(state = state, onBack = {}, onShare = { shares++ }, onDelete = { deleted += it })
      }
    }
  }

  @Test
  fun listsEverySavedCardOfTheImage() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA, SATO)))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.CARD_COUNT).assertTextContains(string(R.string.detail_cards, 2))
    composeTestRule.onNodeWithTag(ImageDetailTestTags.card(0)).assertIsDisplayed()
    composeTestRule.onNodeWithText("山田 太郎").assertIsDisplayed()
    composeTestRule.onNodeWithText("株式会社サンプル商事").assertIsDisplayed()
    composeTestRule.onNodeWithText("佐藤 花子").assertIsDisplayed()
  }

  @Test
  fun deletingAsksFirstAndSaysTheOriginalStays() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA, SATO)))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.delete(1)).performClick()

    composeTestRule.onNodeWithText(string(R.string.detail_delete_title, "佐藤 花子")).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.detail_delete_body)).assertIsDisplayed()
    assertEquals(emptyList<Uid>(), deleted)
    composeTestRule.onNodeWithTag(ImageDetailTestTags.DELETE_CONFIRM).performClick()
    assertEquals(listOf(SATO.uid), deleted)
  }

  @Test
  fun deletingTheLastCardWarnsThatTheCopyIsDeletedToo() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA)))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.delete(0)).performClick()

    composeTestRule.onNodeWithText(string(R.string.detail_delete_body_last)).assertIsDisplayed()
  }

  @Test
  fun cancellingKeepsTheCard() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA)))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.delete(0)).performClick()
    composeTestRule.onNodeWithTag(ImageDetailTestTags.DELETE_CANCEL).performClick()

    composeTestRule.onNodeWithTag(ImageDetailTestTags.DELETE_DIALOG).assertDoesNotExist()
    assertEquals(emptyList<Uid>(), deleted)
  }

  @Test
  fun deletingInProgressDisablesFurtherDeletesAndSharing() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA, SATO), isDeleting = true))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.delete(0)).assertIsNotEnabled()
    composeTestRule.onNodeWithTag(ImageDetailTestTags.SHARE).assertIsNotEnabled()
  }

  @Test
  fun shareAndFullScreenAreReachable() {
    show(ImageDetailUiState.Loaded(FILE, listOf(YAMADA)))

    composeTestRule.onNodeWithTag(ImageDetailTestTags.SHARE).performClick()
    composeTestRule.onNodeWithTag(ImageDetailTestTags.IMAGE).performClick()

    assertEquals(1, shares)
    composeTestRule.onNodeWithTag(ImageDetailTestTags.FULL_SCREEN).assertIsDisplayed()
  }

  private companion object {
    val FILE = File("unused")
    val YAMADA =
      Card.of(Uid(UUID(0, 1)), PropertyValue.of("山田 太郎")!!, CardDetails.fromRaw(org = "株式会社サンプル商事"))
    val SATO = Card.of(Uid(UUID(0, 2)), PropertyValue.of("佐藤 花子")!!)
  }
}
