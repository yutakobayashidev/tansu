package dev.yutakobayashi.tansu.domain

import java.util.UUID

/** 名刺 1 件を一意に識別する値。vCard 4.0 の `UID` として書き出す。 */
@JvmInline
value class Uid(val value: UUID) {
  override fun toString(): String = value.toString()

  companion object {
    fun random(): Uid = Uid(UUID.randomUUID())
  }
}
