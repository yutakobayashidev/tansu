package dev.yutakobayashi.tansu

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Settings : NavKey

/** フォルダの画像 1 枚のレビュー画面。[documentUri] は SAF の document URI。 */
@Serializable data class Review(val documentUri: String, val displayName: String) : NavKey
