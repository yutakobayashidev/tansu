package dev.yutakobayashi.tansu.domain

/**
 * レビューで項目や候補に付ける注意の段階。後に並ぶほど重い。
 */
enum class Attention {
  /** 注意なし。 */
  None,

  /** 黄。形式不正の `EMAIL` がある。確定は妨げない。 */
  Warning,

  /** 赤。`FN` が空で、確定できない。 */
  Blocking,
}

/**
 * レビューで候補 1 件に示す注意。
 *
 * @property malformedEmails 形式不正の `EMAIL` の位置（[CardDetails.email] の添字）。
 * @property sharesFn 同じ解析の別の候補と `FN` が一致する。候補は統合せず、警告だけを出す（ADR 0003）。
 */
data class CandidateReview(
  val candidate: Candidate,
  val malformedEmails: Set<Int>,
  val sharesFn: Boolean,
) {
  val attention: Attention
    get() =
      when {
        !candidate.isConfirmable -> Attention.Blocking
        malformedEmails.isNotEmpty() -> Attention.Warning
        else -> Attention.None
      }

  companion object {
    /** 同じ解析で得た [candidates] それぞれの注意を、順序を保って求める。 */
    fun of(candidates: List<Candidate>): List<CandidateReview> {
      val fnCounts = candidates.mapNotNull { it.fn?.value }.groupingBy { it }.eachCount()
      return candidates.map { candidate ->
        CandidateReview(
          candidate = candidate,
          malformedEmails =
            candidate.details.email.withIndex().filterNot { EmailFormat.isValid(it.value.value) }.mapTo(HashSet()) {
              it.index
            },
          sharesFn = candidate.fn?.let { (fnCounts[it.value] ?: 0) > 1 } ?: false,
        )
      }
    }
  }
}

/**
 * 読み取り結果 1 件分のレビュー。どの候補を確定するかを持つ。
 *
 * 既定では確定できる候補をすべて保存対象にし、利用者は外したい候補だけを外す。
 * `FN` が空の候補は保存対象に入れられない（AC4）。
 *
 * @property selected 保存対象の候補の位置（[candidates] の添字）。
 */
@ConsistentCopyVisibility
data class ReadResultReview
private constructor(
  val candidates: List<CandidateReview>,
  val selected: Set<Int>,
) {
  /** 候補が 0 件。失敗ではなく該当なし。 */
  val isNoCardsFound: Boolean get() = candidates.isEmpty()

  fun isSelected(index: Int): Boolean = index in selected

  /** [index] の候補を保存対象に入れる、または外す。確定できない候補は入れられない。 */
  fun select(index: Int, include: Boolean): ReadResultReview {
    require(index in candidates.indices) { "No candidate at $index" }
    if (include && !candidates[index].candidate.isConfirmable) return this
    return copy(selected = if (include) selected + index else selected - index)
  }

  /** 保存対象の候補を名刺として確定する。並びは候補の順。 */
  fun confirm(newUid: () -> Uid = Uid::random): List<Card> =
    selected.sorted().map { index ->
      when (val result = candidates[index].candidate.confirm(newUid())) {
        is ConfirmationResult.Confirmed -> result.card
        ConfirmationResult.MissingFn -> error("Selected candidate $index cannot be confirmed")
      }
    }

  companion object {
    fun of(candidates: List<Candidate>): ReadResultReview {
      val reviews = CandidateReview.of(candidates)
      return ReadResultReview(
        candidates = reviews,
        selected = reviews.indices.filterTo(HashSet()) { reviews[it].candidate.isConfirmable },
      )
    }
  }
}

/**
 * `EMAIL` の形式の検査。レビューで黄色にするかどうかだけに使い、値は直さない。
 *
 * `local@domain` の形で、両側が空でなく、空白や 2 個目の `@` を含まず、ドメインに `.` で区切られた
 * 空でないラベルが 2 つ以上あるものを正しい形式とみなす。RFC 5322 の全体は検査しない。
 */
object EmailFormat {
  private val PATTERN = Regex("""^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$""")

  fun isValid(value: String): Boolean = PATTERN.matches(value)
}
