package dev.yutakobayashi.tansu.export

import dev.yutakobayashi.tansu.data.analysis.ReadCard
import dev.yutakobayashi.tansu.domain.Card
import dev.yutakobayashi.tansu.domain.CardDetails
import dev.yutakobayashi.tansu.domain.ContentHash
import dev.yutakobayashi.tansu.domain.PropertyValue
import dev.yutakobayashi.tansu.domain.Uid
import java.util.UUID

/** 書き出しのテストで使う固定値。UID と内容ハッシュを固定し、ゴールデンを再現できるようにする。 */
internal object ExportFixtures {
  fun uid(index: Int): Uid = Uid(UUID.fromString("00000000-0000-4000-8000-%012d".format(index)))

  fun hash(char: Char): ContentHash = ContentHash(char.toString().repeat(64))

  fun card(index: Int, read: ReadCard): Card =
    Card.of(
      uid = uid(index),
      fn = requireNotNull(PropertyValue.of(read.fn)),
      details =
        CardDetails.fromRaw(
          n = read.n,
          org = read.org,
          title = read.title,
          tel = read.tel,
          email = read.email,
          adr = read.adr,
          url = read.url,
          note = read.note,
        ),
    )

  /** `FN` だけを持つ名刺。 */
  fun fnOnly(index: Int, fn: String = "見本 花子"): Card = Card.of(uid(index), PropertyValue.of(fn)!!)

  /** 75 オクテットの折り返しが UTF-8 の文字の途中に来る、長い `NOTE` を持つ名刺。 */
  fun longNote(index: Int): Card =
    Card.of(
      uid = uid(index),
      fn = PropertyValue.of("Long Note")!!,
      details =
        CardDetails.fromRaw(
          note = "a" + "名刺の裏面に記載された長いメモ。".repeat(8),
          url = "https://example.com/path?a=1,b=2;c=3",
        ),
    )
}
