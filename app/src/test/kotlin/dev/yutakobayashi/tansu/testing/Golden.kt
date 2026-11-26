package dev.yutakobayashi.tansu.testing

import java.io.File
import org.junit.Assert.fail

/**
 * 出力をゴールデンファイルとバイト単位で比べる。
 *
 * ゴールデンは `app/src/test/golden/` に置く。更新するときは Gradle に `-PupdateGoldens` を付けて実行する。
 * 更新モードでは比較せずに書き込み、テストを通す。
 *
 * ```
 * android-dev gradle testDebugUnitTest -PupdateGoldens
 * ```
 *
 * 不一致のときは実際の出力を [actualDirectory] に書き出し、最初に違うバイトの位置と前後を
 * 改行コードが見える形で示す。
 */
class Golden(
  private val directory: File,
  private val actualDirectory: File,
  private val update: Boolean = false,
) {
  fun assertMatches(name: String, actual: String) = assertMatches(name, actual.toByteArray(Charsets.UTF_8))

  fun assertMatches(name: String, actual: ByteArray) {
    val expectedFile = resolve(directory, name)
    if (update) {
      expectedFile.parentFile!!.mkdirs()
      expectedFile.writeBytes(actual)
      return
    }
    if (!expectedFile.isFile) {
      val written = writeActual(name, actual)
      fail("Golden $name does not exist at $expectedFile. Actual output: $written. Run with -PupdateGoldens to create it.")
    }
    val expected = expectedFile.readBytes()
    if (expected.contentEquals(actual)) {
      resolve(actualDirectory, name).delete()
      return
    }
    val written = writeActual(name, actual)
    fail(describeMismatch(name, expected, actual) + "\nActual output: $written. Run with -PupdateGoldens to accept it.")
  }

  private fun writeActual(name: String, actual: ByteArray): File =
    resolve(actualDirectory, name).apply {
      parentFile!!.mkdirs()
      writeBytes(actual)
    }

  companion object {
    const val DIRECTORY_PROPERTY = "tansu.golden.dir"
    const val ACTUAL_DIRECTORY_PROPERTY = "tansu.golden.actualDir"
    const val UPDATE_PROPERTY = "tansu.golden.update"

    /** Gradle が渡すシステムプロパティから作る既定のインスタンス。 */
    val default: Golden by lazy {
      fun property(key: String) =
        checkNotNull(System.getProperty(key)) { "System property $key is not set. Run tests through Gradle." }
      Golden(
        directory = File(property(DIRECTORY_PROPERTY)),
        actualDirectory = File(property(ACTUAL_DIRECTORY_PROPERTY)),
        update = System.getProperty(UPDATE_PROPERTY).toBoolean(),
      )
    }

    fun assertMatches(name: String, actual: String) = default.assertMatches(name, actual)

    fun assertMatches(name: String, actual: ByteArray) = default.assertMatches(name, actual)

    private fun resolve(root: File, name: String): File {
      val file = File(root, name).canonicalFile
      require(name.isNotBlank() && file.startsWith(root.canonicalFile) && file != root.canonicalFile) {
        "Golden name must be a relative path inside ${root.path}: $name"
      }
      return file
    }

    internal fun describeMismatch(name: String, expected: ByteArray, actual: ByteArray): String {
      val offset = (0 until minOf(expected.size, actual.size)).firstOrNull { expected[it] != actual[it] }
        ?: minOf(expected.size, actual.size)
      val line = actual.take(offset).count { it == '\n'.code.toByte() } + 1
      return buildString {
        append("Golden $name differs at byte $offset (line $line). ")
        append("Expected ${expected.size} bytes, actual ${actual.size} bytes.\n")
        append("  expected: ").append(excerpt(expected, offset)).append('\n')
        append("  actual:   ").append(excerpt(actual, offset))
      }
    }

    /** [offset] の前後を、制御文字と非 ASCII をエスケープして示す。 */
    private fun excerpt(bytes: ByteArray, offset: Int): String {
      val start = maxOf(0, offset - 24)
      val end = minOf(bytes.size, offset + 24)
      val body = (start until end).joinToString("") { index ->
        val text = escape(bytes[index])
        if (index == offset) "[$text]" else text
      }
      val marker = if (offset >= bytes.size) "[EOF]" else ""
      return (if (start > 0) "…" else "") + body + marker + (if (end < bytes.size) "…" else "")
    }

    private fun escape(byte: Byte): String {
      val value = byte.toInt() and 0xFF
      return when {
        value == '\r'.code -> "\\r"
        value == '\n'.code -> "\\n"
        value == '\t'.code -> "\\t"
        value == '\\'.code -> "\\\\"
        value in 0x20..0x7E -> value.toChar().toString()
        else -> "\\x%02X".format(value)
      }
    }
  }
}
