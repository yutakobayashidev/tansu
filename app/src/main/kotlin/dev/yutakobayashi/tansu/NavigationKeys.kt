package dev.yutakobayashi.tansu

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Settings : NavKey

/** フォルダの画像 1 枚のレビュー画面。[documentUri] は SAF の document URI。 */
@Serializable data class Review(val documentUri: String, val displayName: String) : NavKey

/** 名刺を保存した名刺画像の一覧。 */
@Serializable data object SavedImages : NavKey

/** 名刺画像 1 枚の画像詳細。[contentHash] は名刺画像の内容ハッシュ。 */
@Serializable data class ImageDetail(val contentHash: String) : NavKey
