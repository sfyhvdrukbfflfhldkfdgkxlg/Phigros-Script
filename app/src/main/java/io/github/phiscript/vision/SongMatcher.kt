package io.github.phiscript.vision
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class SongCandidate(val id: String, val title: String, val aliases: List<String> = emptyList())
data class Identity(val songId: String, val title: String, val difficulty: String)
data class NormalizedPoint(val x: Float, val y: Float)
data class PauseMenu(val resume: NormalizedPoint)
data class TextRegion(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val center: NormalizedPoint get() = NormalizedPoint((left + right) / 2f, (top + bottom) / 2f)
    internal val valid: Boolean get() = left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && left < right && top < bottom
}
object SongMatcher {
    private const val difficultyBody = "(?:(?:LV|LEVEL|DIFFICULTY|难度)[\\s:.：]*)?(EZ|HD|IN|AT)" +
        "(?:\\s*(?:LV[\\s.]*)?\\d{1,2}(?:\\.\\d+)?)?"
    private val difficultyLine = Regex("^$difficultyBody$", RegexOption.IGNORE_CASE)
    private val difficultyWithLevel = Regex(
        "(?<![\\p{L}\\p{N}])(EZ|HD|IN|AT)\\s*(?:LV[\\s.]*)?\\d{1,2}(?:\\.\\d+)?" +
            "(?![\\p{L}\\p{N}.])", RegexOption.IGNORE_CASE)
    private val difficultyToken = Regex("(?<![\\p{L}\\p{N}])(EZ|HD|IN|AT)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val titlePrefix = Regex("^($difficultyBody)\\s+", RegexOption.IGNORE_CASE)
    private val titleSuffix = Regex("\\s+($difficultyBody)$", RegexOption.IGNORE_CASE)
    private val resumeLabels = setOf("继续", "继续游戏", "恢复游戏", "resume", "continue")
    private val pauseLabels = setOf("retry", "restart", "重试", "重新开始", "暂停", "pause", "paused")
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    fun match(titleLines: List<String>, difficultyLines: List<String>, songs: List<SongCandidate>): Identity? {
        val index = HashMap<String, MutableSet<String>>()
        val byId = songs.associateBy { it.id }
        for (song in songs) for (name in song.aliases + song.title + song.id.substringBefore('.')) {
            val key = normalize(name)
            if (key.isNotEmpty()) index.getOrPut(key) { linkedSetOf() }.add(song.id)
        }
        val keys = linkedSetOf<String>()
        for (i in titleLines.indices) for (length in 1..minOf(3, titleLines.size - i)) {
            val raw = canonical(titleLines.subList(i, i + length).joinToString(" "))
            keys.add(normalize(raw))
            titleEdges(raw).forEach { keys.add(normalize(it.first)) }
        }
        val hits = linkedSetOf<String>()
        for (key in keys.filter { it.isNotEmpty() && it.length <= 200 })
            for ((name, ids) in index) if (matchesName(key, name)) hits.addAll(ids)
        if (hits.size != 1) return null
        val song = byId.getValue(hits.single())
        val difficulties = difficultyLabels(difficultyLines).toMutableSet()
        for (raw in difficultyLines) for ((title, difficulty) in titleEdges(canonical(raw))) {
            val key = normalize(title)
            val possible = index.filterKeys { matchesName(key, it) }.values.flatten().toSet()
            if (possible == setOf(song.id)) difficulties.add(difficulty)
        }
        if (difficulties.size != 1) return null
        return Identity(song.id, song.title, difficulties.single())
    }
    fun difficultyLabels(lines: List<String>): Set<String> {
        val found = linkedSetOf<String>()
        for (raw in lines) {
            val line = canonical(raw)
            val exact = difficultyLine.matchEntire(line)
            if (exact != null) found.add(exact.groupValues[1].uppercase(Locale.ROOT))
            else {
                difficultyWithLevel.findAll(line).forEach { found.add(it.groupValues[1].uppercase(Locale.ROOT)) }
                val tokens = difficultyToken.findAll(line).map { it.groupValues[1].uppercase(Locale.ROOT) }.toSet()
                if (tokens.size > 1) found.addAll(tokens)
            }
        }
        return found
    }
    private fun canonical(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
    private fun titleEdges(text: String): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>(2)
        titlePrefix.find(text)?.let {
            val rest = text.substring(it.range.last + 1).trim()
            if (rest.isNotEmpty()) result.add(rest to it.groupValues[2].uppercase(Locale.ROOT))
        }
        titleSuffix.find(text)?.let {
            val rest = text.substring(0, it.range.first).trim()
            if (rest.isNotEmpty()) result.add(rest to it.groupValues[2].uppercase(Locale.ROOT))
        }
        return result
    }
    private fun matchesName(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 6 || b.length < 6 || a.none { it.isLetter() } || b.none { it.isLetter() } ||
            abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
    fun pauseMenu(labels: List<TextRegion>): PauseMenu? {
        val valid = labels.filter { it.valid }
        if (isResult(valid.map { it.text }) || valid.none { normalize(it.text) in pauseLabels }) return null
        val buttons = ArrayList<TextRegion>()
        for (region in valid.filter { normalize(it.text) in resumeLabels }
            .sortedByDescending { (it.right - it.left) * (it.bottom - it.top) })
            if (buttons.none { sameButton(it, region) }) buttons.add(region)
        return buttons.singleOrNull()?.let { PauseMenu(it.center) }
    }
    private fun sameButton(a: TextRegion, b: TextRegion): Boolean {
        val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (width <= 0f || height <= 0f) return false
        val smaller = minOf((a.right - a.left) * (a.bottom - a.top), (b.right - b.left) * (b.bottom - b.top))
        return width * height >= smaller * 0.8f
    }
    fun isResult(lines: List<String>): Boolean {
        val labels = lines.map { normalize(it) }
        fun hasValue(vararg names: String) = labels.any { label ->
            names.any { name -> label == name || (label.startsWith(name) &&
                label.substring(name.length).let { it.isNotEmpty() && it.all(Char::isDigit) }) }
        }
        return hasValue("maxcombo", "最大连击") && hasValue("accuracy", "准确率", "score", "rankingscore", "newbest")
    }
    fun isPauseOrResult(lines: List<String>): Boolean =
        lines.any { normalize(it) in resumeLabels || normalize(it) in pauseLabels } || isResult(lines)
}
