package dev.yutakobayashi.tansu.data.folder

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class InMemoryFolderStore : FolderStore {
  private var treeUri: String? = null

  override fun read(): String? = treeUri

  override fun write(treeUri: String) {
    this.treeUri = treeUri
  }
}

data class FakeDocument(val document: FolderDocument, val bytes: ByteArray?) {
  companion object {
    fun of(tree: String, name: String, lastModified: Long?, bytes: ByteArray?) =
      FakeDocument(FolderDocument("$tree/document/$name", name, lastModified, bytes?.size?.toLong()), bytes)
  }
}

class FakeFolderAccess : FolderAccess {
  val persisted = mutableSetOf<String>()
  val documents = mutableListOf<FakeDocument>()
  val openCounts = mutableMapOf<String, Int>()
  var folderExists = true

  override fun persist(treeUri: String) {
    persisted += treeUri
  }

  override fun release(treeUri: String) {
    persisted -= treeUri
  }

  override fun hasPersistedPermission(treeUri: String): Boolean = treeUri in persisted

  override fun folderName(treeUri: String): String =
    if (folderExists) "名刺" else throw FolderUnavailableException()

  override fun listImages(treeUri: String): List<FolderDocument> =
    if (folderExists) documents.map { it.document } else throw FolderUnavailableException()

  override fun open(documentUri: String): InputStream {
    val document = documents.first { it.document.uri == documentUri }
    openCounts.merge(document.document.displayName, 1, Int::plus)
    return ByteArrayInputStream(document.bytes ?: throw IOException("unreadable"))
  }
}

