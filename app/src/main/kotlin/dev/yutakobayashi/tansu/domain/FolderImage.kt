package dev.yutakobayashi.tansu.domain

/**
 * フォルダにある名刺画像 1 枚。まだ取り込んでいない画像も含む。
 *
 * @property uri SAF の document URI。
 * @property lastModified 更新日時（エポックミリ秒）。provider が返さなければ `null`。
 * @property progress 内容ハッシュを求めるまでは `null`。
 */
data class FolderImage(
  val uri: String,
  val displayName: String,
  val lastModified: Long?,
  val size: Long?,
  val progress: ImageProgress? = null,
) {
  companion object {
    /** 一覧の並び順。更新日時の新しい順で、同時刻なら名前順。更新日時の無い画像は末尾に置く。 */
    val NEWEST_FIRST: Comparator<FolderImage> =
      compareByDescending<FolderImage, Long?>(nullsFirst()) { it.lastModified }.thenBy { it.displayName }
  }
}
