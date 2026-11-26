package dev.yutakobayashi.tansu.data.folder

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * フォルダの画像の内容ハッシュを、更新日時とサイズが変わるまで覚えておく。
 *
 * 一覧を開くたびに全画像を読み直さないためのもので、取り込みや解析とは独立している。
 * 名刺画像を参照しないので、取り込み前の画像も載る。
 */
@Entity(tableName = "folder_image_hash")
internal data class FolderImageHashEntity(
  @PrimaryKey @ColumnInfo(name = "document_uri") val documentUri: String,
  @ColumnInfo(name = "last_modified") val lastModified: Long?,
  val size: Long?,
  @ColumnInfo(name = "content_hash") val contentHash: String,
)

@Dao
internal interface FolderImageHashDao {
  @Query("SELECT * FROM folder_image_hash")
  suspend fun all(): List<FolderImageHashEntity>

  @Upsert
  suspend fun upsert(entities: List<FolderImageHashEntity>)

  @Query("DELETE FROM folder_image_hash WHERE document_uri IN (:documentUris)")
  suspend fun delete(documentUris: List<String>)

  @Query("DELETE FROM folder_image_hash")
  suspend fun clear()
}
