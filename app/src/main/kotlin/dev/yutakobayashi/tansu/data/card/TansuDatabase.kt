package dev.yutakobayashi.tansu.data.card

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [SourceImageEntity::class, AnalysisEntity::class, CandidateEntity::class, CardEntity::class],
  version = 1,
)
@TypeConverters(Converters::class)
abstract class TansuDatabase : RoomDatabase() {
  internal abstract fun cardDao(): CardDao

  companion object {
    const val NAME = "tansu.db"

    /**
     * データベースを開く。プロセスごとに 1 つだけ作ること。
     *
     * 開いた時点で `解析中` の解析は、前のプロセスが解析を終えずに落ちたものなので `失敗` にする。
     * 同じプロセスで 2 つ目を開くと、進行中の解析まで `失敗` になる。
     */
    fun build(context: Context, name: String = NAME): TansuDatabase =
      Room.databaseBuilder(context.applicationContext, TansuDatabase::class.java, name)
        .addCallback(FailInterruptedAnalyses)
        .build()
  }
}

/** 前のプロセスで `解析中` のまま残った解析を `失敗` に倒す。 */
internal object FailInterruptedAnalyses : RoomDatabase.Callback() {
  override fun onOpen(db: SupportSQLiteDatabase) {
    db.execSQL(
      "UPDATE analysis SET status = '${StoredAnalysisStatus.FAILED.name}' " +
        "WHERE status = '${StoredAnalysisStatus.ANALYZING.name}'",
    )
  }
}
