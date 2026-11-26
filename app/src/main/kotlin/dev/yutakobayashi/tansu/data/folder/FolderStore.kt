package dev.yutakobayashi.tansu.data.folder

import android.content.SharedPreferences
import androidx.core.content.edit

/** 選んだフォルダの tree URI を覚えておく。 */
interface FolderStore {
  fun read(): String?

  fun write(treeUri: String)
}

class SharedPreferencesFolderStore(private val preferences: SharedPreferences) : FolderStore {
  override fun read(): String? = preferences.getString(KEY_TREE_URI, null)

  override fun write(treeUri: String) {
    preferences.edit(commit = true) { putString(KEY_TREE_URI, treeUri) }
  }

  companion object {
    const val FILE_NAME = "folder"
    private const val KEY_TREE_URI = "tree_uri"
  }
}
