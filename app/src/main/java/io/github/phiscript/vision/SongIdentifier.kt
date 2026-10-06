package io.github.phiscript.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.Closeable
import kotlin.math.roundToInt

data class OcrConfig(val titleRoi: RectF = RectF(0f, 0f, 1f, 1f), val difficultyRoi: RectF = RectF(0f, 0f, 1f, 1f))
data class OcrFrame(val identity: Identity?, val pauseOrResult: Boolean, val pauseMenu: PauseMenu? = null,
                    val resultScreen: Boolean = false, val diagnostic: String = "")
data class OcrLabels(val titleTexts: List<String>, val difficultyTexts: List<String>,
    val regions: List<TextRegion>, val pauseOrResult: Boolean, val resultScreen: Boolean,
    val pauseMenu: PauseMenu?)
class SongIdentifier(config: OcrConfig = OcrConfig()) : Closeable {
    private val titleRoi = validatedRoi(config.titleRoi)
    private val difficultyRoi = validatedRoi(config.difficultyRoi)
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    var lastDiagnostic: String = "等待歌曲信息"
        private set
    fun identify(bitmap: Bitmap, songs: List<SongCandidate>): Identity? = inspect(bitmap, songs).identity
    fun inspect(bitmap: Bitmap, songs: List<SongCandidate>): OcrFrame {
        val observation = read(bitmap)
        val titleTexts = textsWithin(observation, titleRoi)
        val difficultyTexts = textsWithin(observation, difficultyRoi)
        val identity = SongMatcher.match(titleTexts, difficultyTexts, songs)
        val regions = observation.flatMap { listOf(it.region) + it.elements }
        val texts = regions.map { it.text }
        val result = SongMatcher.isResult(texts)
        val menu = SongMatcher.pauseMenu(regions)
        val paused = SongMatcher.isPauseOrResult(texts)
        val seenTitles = titleTexts.filter { it.isNotBlank() }.take(4)
            .joinToString(" / ") { it.replace(Regex("\\s+"), " ").take(36) }.take(120)
        val seenLevels = SongMatcher.difficultyLabels(difficultyTexts).joinToString("/").ifEmpty { "未确定" }
        val preview = "文字：" + seenTitles.ifEmpty { "无" } + "；难度：" + seenLevels
        lastDiagnostic = when {
            result -> "检测到结算界面"
            identity != null -> (if (menu != null) "已确认暂停；" else "") + "已识别 " + identity.title + " · " + identity.difficulty
            observation.isEmpty() -> "画面没有可识别的文字"
            songs.isEmpty() -> "尚未导入可匹配的谱面"
            menu != null -> "已确认暂停，等待唯一曲名和难度；" + preview
            paused -> "检测到菜单文字，等待暂停菜单确认；" + preview
            else -> "未唯一匹配曲名和难度；" + preview
        }
        return OcrFrame(identity, paused, menu, result, lastDiagnostic)
    }
    fun readLabels(bitmap: Bitmap): OcrLabels {
        val observation = read(bitmap)
        val regions = observation.flatMap { listOf(it.region) + it.elements }
        val texts = regions.map { it.text }
        return OcrLabels(textsWithin(observation, titleRoi), textsWithin(observation, difficultyRoi),
            regions, SongMatcher.isPauseOrResult(texts), SongMatcher.isResult(texts),
            SongMatcher.pauseMenu(regions))
    }
    fun detectPaused(bitmap: Bitmap): Boolean {
        val texts = read(bitmap).flatMap { listOf(it.region.text) + it.elements.map { e -> e.text } }
        return SongMatcher.isPauseOrResult(texts)
    }
    private data class Line(val region: TextRegion, val elements: List<TextRegion>)
    private fun textsWithin(lines: List<Line>, roi: RectF): List<String> = lines.mapNotNull { line ->
        if (line.elements.isEmpty()) line.region.text.takeIf { inside(line.region, roi) }
        else {
            val selected = line.elements.filter { inside(it, roi) }
            when {
                selected.isEmpty() -> null
                selected.size == line.elements.size && inside(line.region, roi) -> line.region.text
                else -> selected.joinToString(" ") { it.text }
            }
        }
    }
    private fun read(bitmap: Bitmap): List<Line> {
        check(Looper.myLooper() != Looper.getMainLooper()) { "OCR must run on a background worker" }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        val scale = minOf(1.0, 1920.0 / maxOf(bitmap.width, bitmap.height))
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (width == bitmap.width && height == bitmap.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val result = awaitRecognition(recognizer.process(InputImage.fromBitmap(scaled, 0)))
            return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val lineRegion = region(line.text, line.boundingBox, width, height) ?: return@mapNotNull null
                val elements = line.elements.mapNotNull { region(it.text, it.boundingBox, width, height) }
                Line(lineRegion, elements)
            }
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }
    private fun region(text: String, bounds: Rect?, width: Int, height: Int): TextRegion? {
        if (bounds == null || text.isBlank()) return null
        return TextRegion(text, (bounds.left.toFloat() / width).coerceIn(0f, 1f),
            (bounds.top.toFloat() / height).coerceIn(0f, 1f),
            (bounds.right.toFloat() / width).coerceIn(0f, 1f),
            (bounds.bottom.toFloat() / height).coerceIn(0f, 1f)).takeIf { it.valid }
    }
    private fun awaitRecognition(task: Task<Text>): Text {
        var interrupted = false
        try {
            while (true) {
                try { return Tasks.await(task) } catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
    private fun inside(label: TextRegion, roi: RectF): Boolean = label.center.x >= roi.left &&
        label.center.x <= roi.right && label.center.y >= roi.top && label.center.y <= roi.bottom
    override fun close() = recognizer.close()
    companion object {
        internal fun validatedRoi(value: RectF): RectF {
            require(value.left.isFinite() && value.top.isFinite() && value.right.isFinite() && value.bottom.isFinite() &&
                value.left >= 0f && value.top >= 0f && value.right <= 1f && value.bottom <= 1f &&
                value.left < value.right && value.top < value.bottom) {
                "识别区域必须是 0 到 1 之间的 left,top,right,bottom"
            }
            return RectF(value)
        }
    }
}
