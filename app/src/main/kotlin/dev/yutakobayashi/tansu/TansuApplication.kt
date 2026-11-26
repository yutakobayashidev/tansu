package dev.yutakobayashi.tansu

import android.app.Application
import android.content.Context
import dev.yutakobayashi.tansu.data.settings.ByoSettingsRepository
import dev.yutakobayashi.tansu.data.settings.ConnectionChecker
import dev.yutakobayashi.tansu.data.settings.KeystoreSecretCipher
import dev.yutakobayashi.tansu.data.settings.ModelsEndpointConnectionChecker
import dev.yutakobayashi.tansu.data.settings.SharedPreferencesByoSettingsStore
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
}

val Context.appContainer: AppContainer
  get() = (applicationContext as TansuApplication).container
