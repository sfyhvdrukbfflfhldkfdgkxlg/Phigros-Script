package io.github.phiscript.vision
import java.text.Normalizer
import java.util.Locale

data class PhiraCandidate(val id: String, val title: String, val level: String, val aliases: List<String> = emptyList())
/** Custom Phira levels must match along with a unique name. */
object PhiraMatcher {
    fun match(titleLines: List<String>, levelLines: List<String>, candidates: List<PhiraCandidate>): PhiraCandidate? {
        val titles = combinations(titleLines); val levels = combinations(levelLines)
        return candidates.filter { candidate ->
            val expectedLevel = normalizeLevel(candidate.level)
            if (expectedLevel.isEmpty() || candidate.title.isBlank()) false
            else (candidate.aliases + candidate.title).any { name ->
                val expectedTitle = SongMatcher.normalize(name)
                expectedTitle.isNotEmpty() && (
                    titles.any { SongMatcher.normalize(it) == expectedTitle } &&
                    levels.any { normalizeLevel(it) == expectedLevel } ||
                    (titles + levels).any { combined(it, name, candidate.level) })
            }
        }.singleOrNull()
    }
    private fun combinations(lines: List<String>): List<String> {
        val bounded = lines.filter { it.isNotBlank() && it.length <= 300 }.take(80)
        val result = ArrayList<String>()
        for (i in bounded.indices) for (count in 1..minOf(3, bounded.size - i))
            result.add(bounded.subList(i, i + count).joinToString(" "))
        return result
    }
    private fun combined(raw: String, name: String, level: String): Boolean {
        val canonical = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
        for (cut in canonical.indices.filter { canonical[it].isWhitespace() }) {
            val left = canonical.substring(0, cut).trim(); val right = canonical.substring(cut).trim()
            if ((SongMatcher.normalize(left) == SongMatcher.normalize(name) &&
                normalizeLevel(right) == normalizeLevel(level)) ||
                (normalizeLevel(left) == normalizeLevel(level) &&
                SongMatcher.normalize(right) == SongMatcher.normalize(name))) return true
        }; return false
    }
    internal fun normalizeLevel(raw: String): String {
        val text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace(Regex("^(?:level|difficulty|难度|lv)\\s*[:：.]?\\s*", RegexOption.IGNORE_CASE), "")
        return text.filter { it.isLetterOrDigit() || it == '.' || it == '+' || it == '-' }
    }
}
