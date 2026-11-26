package dev.yutakobayashi.tansu.testing

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoldenTest {
  @get:Rule val temp = TemporaryFolder()

  private val goldens: File by lazy { temp.newFolder("golden") }
  private val actuals: File by lazy { temp.newFolder("actual") }

  private fun golden(update: Boolean = false) = Golden(goldens, actuals, update)

  @Test
  fun identicalBytesPass() {
    File(goldens, "a.vcf").writeBytes("BEGIN:VCARD\r\n".toByteArray())

    golden().assertMatches("a.vcf", "BEGIN:VCARD\r\n")

    assertFalse(File(actuals, "a.vcf").exists())
  }

  @Test
  fun lineEndingDifferenceFailsWithTheOffsetAndKeepsTheActualOutput() {
    File(goldens, "a.vcf").writeBytes("BEGIN:VCARD\r\nFN:山田\r\n".toByteArray())

    val error = assertThrows(AssertionError::class.java) { golden().assertMatches("a.vcf", "BEGIN:VCARD\nFN:山田\n") }

    assertTrue(error.message, error.message!!.contains("differs at byte 11 (line 1)"))
    assertTrue(error.message, error.message!!.contains("BEGIN:VCARD[\\r]\\nFN:"))
    assertTrue(error.message, error.message!!.contains("BEGIN:VCARD[\\n]FN:\\xE5"))
    assertEquals("BEGIN:VCARD\nFN:山田\n", File(actuals, "a.vcf").readText())
  }

  @Test
  fun truncatedOutputFails() {
    File(goldens, "a.csv").writeText("uid,fn\r\n")

    val error = assertThrows(AssertionError::class.java) { golden().assertMatches("a.csv", "uid,fn") }

    assertTrue(error.message, error.message!!.contains("differs at byte 6"))
    assertTrue(error.message, error.message!!.contains("[EOF]"))
  }

  @Test
  fun missingGoldenFails() {
    val error = assertThrows(AssertionError::class.java) { golden().assertMatches("nested/new.vcf", "x") }

    assertTrue(error.message, error.message!!.contains("does not exist"))
    assertEquals("x", File(actuals, "nested/new.vcf").readText())
  }

  @Test
  fun updateModeWritesTheGolden() {
    golden(update = true).assertMatches("nested/new.vcf", byteArrayOf(0, 1, 2))

    assertArrayEquals(byteArrayOf(0, 1, 2), File(goldens, "nested/new.vcf").readBytes())
    golden().assertMatches("nested/new.vcf", byteArrayOf(0, 1, 2))
  }

  @Test
  fun namesOutsideTheDirectoryAreRejected() {
    for (name in listOf("../escape", "", "a/../../escape")) {
      assertThrows(name, IllegalArgumentException::class.java) { golden(update = true).assertMatches(name, "x") }
    }
  }

  @Test
  fun gradlePassesTheGoldenDirectory() {
    val directory = File(System.getProperty(Golden.DIRECTORY_PROPERTY)!!)
    assertTrue(directory.path, directory.isDirectory)
    assertTrue(directory.path, directory.invariantSeparatorsPath.endsWith("app/src/test/golden"))
  }
}
