package dev.yutakobayashi.tansu.ui.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yutakobayashi.tansu.R
import dev.yutakobayashi.tansu.appContainer
import dev.yutakobayashi.tansu.export.ExportFormat
import java.io.File

/** トップバーに置く書き出しの操作。形式を選ぶと保存済みの名刺をすべて書き出し、共有シートを開く。 */
@Composable
fun ExportAction(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val container = context.appContainer
  val viewModel: ExportViewModel = viewModel { ExportViewModel(container.cardExporter) }
  val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is ExportEvent.Share -> context.share(event.file, event.format)
        ExportEvent.NoCards -> context.toast(R.string.export_no_cards)
        ExportEvent.Failed -> context.toast(R.string.export_failed)
      }
    }
  }
  ExportAction(enabled = !isExporting, onExport = viewModel::export, modifier = modifier)
}

@Composable
internal fun ExportAction(enabled: Boolean, onExport: (ExportFormat) -> Unit, modifier: Modifier = Modifier) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    TextButton(
      onClick = { expanded = true },
      enabled = enabled,
      modifier = Modifier.testTag(ExportTestTags.ACTION),
    ) {
      Text(stringResource(R.string.export_action))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      FORMATS.forEach { (format, label) ->
        DropdownMenuItem(
          text = { Text(stringResource(label)) },
          onClick = {
            expanded = false
            onExport(format)
          },
          modifier = Modifier.testTag(ExportTestTags.format(format)),
        )
      }
    }
  }
}

object ExportTestTags {
  const val ACTION = "export_action"

  fun format(format: ExportFormat): String = "export_format_${format.extension}"
}

private val FORMATS = listOf(ExportFormat.VCard to R.string.export_vcard, ExportFormat.Csv to R.string.export_csv)

/** 書き出したファイルを `ACTION_SEND` で渡す。受け取り側には読み取りだけを一時的に許す。 */
private fun Context.share(file: File, format: ExportFormat) {
  val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
  val send =
    Intent(Intent.ACTION_SEND).apply {
      type = format.mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      clipData = ClipData.newRawUri(file.name, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  startActivity(Intent.createChooser(send, getString(R.string.export_chooser_title)))
}

private fun Context.toast(message: Int) {
  Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
