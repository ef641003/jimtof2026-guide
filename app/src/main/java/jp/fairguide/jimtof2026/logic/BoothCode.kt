package jp.fairguide.jimtof2026.logic

data class BoothLocation(
    val code: String,
    val hallId: String,
    val hallName: String,
    val building: String,
    val floor: String,
) {
    val label: String get() = "$hallName（$building $floor）"
}

/**
 * 小間番号（例：E1024、W3104、S4029、WA015）からホールを割り出す。
 * 公式の出展者検索の表記にもとづく：先頭の英字が展示棟、続く1桁がホール番号。
 */
object BoothCode {

    /** 東→西→南の順。並べ替えに使う */
    val HALL_ORDER: List<String> = listOf("E1", "E2", "E3", "E7", "E8", "WA", "W1", "W2", "W3", "W4", "S1", "S2", "S3", "S4")

    private val EAST_OPEN = setOf(1, 2, 3, 7, 8) // 東4〜6ホールは改修工事で休館
    private val ATRIUM = Regex("^WA(\\d{0,3})$")

    /** 例：E1024、W3104、E1009-2（パビリオン内の小間は「-番号」がつく） */
    private val HALL = Regex("^([EWS])(\\d)(\\d{0,3})(-\\d{1,2})?$")

    /** 全角英数字・小文字・空白を吸収して「E1024」「E1009-2」の形にそろえる */
    fun normalize(input: String): String {
        val sb = StringBuilder()
        for (raw in input.trim()) {
            val ch = when (raw) {
                in '０'..'９' -> '0' + (raw - '０')
                in 'Ａ'..'Ｚ' -> 'A' + (raw - 'Ａ')
                in 'ａ'..'ｚ' -> 'A' + (raw - 'ａ')
                '－', 'ー', '−', '‐', '―' -> '-'
                else -> raw.uppercaseChar()
            }
            if (ch in '0'..'9' || ch in 'A'..'Z') sb.append(ch)
            if (ch == '-' && sb.isNotEmpty() && sb.last() != '-') sb.append('-')
        }
        return sb.toString().trimEnd('-')
    }

    fun locate(input: String): BoothLocation? {
        val code = normalize(input)
        if (code.isEmpty()) return null
        if (ATRIUM.matches(code)) {
            return BoothLocation(code, "WA", "西アトリウム", "西展示棟", "1F")
        }
        val match = HALL.matchEntire(code) ?: return null
        val number = match.groupValues[2].toInt()
        return when (match.groupValues[1]) {
            "E" -> if (number in EAST_OPEN) {
                BoothLocation(code, "E$number", "東${number}ホール", "東展示棟", "1F")
            } else {
                null
            }
            "W" -> if (number in 1..4) {
                BoothLocation(code, "W$number", "西${number}ホール", "西展示棟", if (number <= 2) "1F" else "4F")
            } else {
                null
            }
            "S" -> if (number in 1..4) {
                BoothLocation(code, "S$number", "南${number}ホール", "南展示棟", if (number <= 2) "1F" else "4F")
            } else {
                null
            }
            else -> null
        }
    }

    /** 並べ替え用の順位。小間番号がない・読めないものは最後 */
    fun hallRank(input: String): Int {
        val id = locate(input)?.hallId ?: return HALL_ORDER.size
        val index = HALL_ORDER.indexOf(id)
        return if (index >= 0) index else HALL_ORDER.size
    }
}
