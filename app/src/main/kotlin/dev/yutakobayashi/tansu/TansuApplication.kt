package dev.yutakobayashi.tansu

import android.app.Application
import android.content.Context
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
