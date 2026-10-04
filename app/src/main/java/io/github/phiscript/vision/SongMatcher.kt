package io.github.phiscript.vision

import java.text.Normalizer
import java.util.Locale

data class SongCandidate(val id: String, val title: String, val aliases: List<String> = emptyList())
data class Identity(val songId: String, val title: String, val difficulty: String)

object SongMatcher {
    private val difficultyLine = Regex(
        "^(?:(?:LV|LEVEL|DIFFICULTY|难度)[\\s:.：]*)?(EZ|HD|IN|AT)" +
            "(?:\\s*(?:LV[\\s.]*)?\\d{1,2}(?:\\.\\d+)?)?$", RegexOption.IGNORE_CASE)
    private val difficultyToken = Regex(
        "(?<![\\p{L}\\p{N}])(EZ|HD|IN|AT)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun match(titleLines: List<String>, difficultyLines: List<String>,
              songs: List<SongCandidate>): Identity? {
        val index = HashMap<String, MutableSet<String>>()
        val byId = songs.associateBy { it.id }
        for (song in songs) {
            val names = song.aliases + song.title + song.id.substringBefore('.')
            for (name in names) {
                val key = normalize(name)
                if (key.isNotEmpty()) index.getOrPut(key) { linkedSetOf() }.add(song.id)
            }
        }
        val hits = linkedSetOf<String>()
        for (i in titleLines.indices) for (length in 1..minOf(3, titleLines.size - i)) {
            val key = normalize(titleLines.subList(i, i + length).joinToString(""))
            index[key]?.let { hits.addAll(it) }
        }
        if (hits.size != 1) return null
        val difficulties = linkedSetOf<String>()
        for (raw in difficultyLines) {
            val line = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
            val exact = difficultyLine.matchEntire(line)
            if (exact != null) difficulties.add(exact.groupValues[1].uppercase(Locale.ROOT))
            else {
                val tokens = difficultyToken.findAll(line)
                    .map { it.groupValues[1].uppercase(Locale.ROOT) }.toSet()
                if (tokens.size > 1) difficulties.addAll(tokens)
            }
        }
        if (difficulties.size != 1) return null
        val song = byId.getValue(hits.single())
        return Identity(song.id, song.title, difficulties.single())
    }

    fun isPauseOrResult(lines: List<String>): Boolean {
        val labels = lines.map { normalize(it) }.toSet()
        val pause = setOf("继续", "继续游戏", "恢复游戏", "resume", "retry", "重试", "重新开始")
        if (labels.any { it in pause }) return true
        val combo = labels.any { it == "maxcombo" || it == "最大连击" }
        val score = labels.any {
            it == "accuracy" || it == "准确率" || it == "score" ||
                it == "rankingscore" || it == "newbest"
        }
        return combo && score
    }
}
