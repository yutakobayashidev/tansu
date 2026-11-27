package dev.yutakobayashi.tansu

import android.app.Application
import android.content.Context
import dev.yutakobayashi.tansu.data.analysis.AnalysisClient
import dev.yutakobayashi.tansu.data.analysis.AnalysisRunner
import dev.yutakobayashi.tansu.data.analysis.ChatCompletionsAnalysisClient
import dev.yutakobayashi.tansu.data.card.CardRepository
import dev.yutakobayashi.tansu.data.card.SourceImageFiles
import dev.yutakobayashi.tansu.data.card.TansuDatabase
import dev.yutakobayashi.tansu.data.folder.FolderRepository
import dev.yutakobayashi.tansu.data.folder.SafFolderAccess
import dev.yutakobayashi.tansu.data.folder.SharedPreferencesFolderStore
import dev.yutakobayashi.tansu.data.settings.ByoSettingsRepository
import dev.yutakobayashi.tansu.data.settings.ConnectionChecker
import dev.yutakobayashi.tansu.data.settings.KeystoreSecretCipher
import dev.yutakobayashi.tansu.data.settings.ModelsEndpointConnectionChecker
import dev.yutakobayashi.tansu.data.settings.SharedPreferencesByoSettingsStore
import java.io.File
import kotlinx.coroutines.Dispatchers

class TansuApplication : Application() {
  val container: AppContainer by lazy { AppContainer(this) }
}

/** アプリ全体で共有する依存。 */
class AppContainer(context: Context) {
  val byoSettingsRepository: ByoSettingsRepository by lazy {
    ByoSettingsRepository(
      store =
        SharedPreferencesByoSettingsStore(
          context.getSharedPreferences(SharedPreferencesByoSettingsStore.FILE_NAME, Context.MODE_PRIVATE)
        ),
      cipher = KeystoreSecretCipher(),
      ioDispatcher = Dispatchers.IO,
    )
  }

  val connectionChecker: ConnectionChecker by lazy {
    ModelsEndpointConnectionChecker(byoSettingsRepository, Dispatchers.IO)
  }

  /** プロセスごとに 1 つだけ開く。[TansuDatabase.build] を参照。 */
  private val database: TansuDatabase by lazy { TansuDatabase.build(context) }

  val cardRepository: CardRepository by lazy {
    CardRepository(database, SourceImageFiles(File(context.filesDir, SOURCE_IMAGE_DIRECTORY)), Dispatchers.IO)
  }

  /**
   * 解析に使うクライアント。debug ビルドの E2E では HTTP を使わない偽物に差し替える。
   *
   * [analysisRunner] は呼び出しのたびにこの値を読むので、差し替えた後に始めた解析から新しい値を使う。
   */
  @Volatile var analysisClient: AnalysisClient = ChatCompletionsAnalysisClient()

  val analysisRunner: AnalysisRunner by lazy {
    AnalysisRunner(
      repository = cardRepository,
      credentialsProvider = byoSettingsRepository,
      client = { credentials, image -> analysisClient.analyze(credentials, image) },
      ioDispatcher = Dispatchers.IO,
    )
  }

  val folderAccess: SafFolderAccess by lazy { SafFolderAccess(context.contentResolver) }

  val folderRepository: FolderRepository by lazy {
    FolderRepository(
      store =
        SharedPreferencesFolderStore(
          context.getSharedPreferences(SharedPreferencesFolderStore.FILE_NAME, Context.MODE_PRIVATE)
        ),
      access = folderAccess,
      database = database,
      cards = cardRepository,
      ioDispatcher = Dispatchers.IO,
    )
  }

  private companion object {
    const val SOURCE_IMAGE_DIRECTORY = "source-images"
  }
}

val Context.appContainer: AppContainer
  get() = (applicationContext as TansuApplication).container
