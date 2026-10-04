package io.github.phiscript.vision

import android.graphics.Bitmap
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

data class OcrConfig(
    val titleRoi: RectF = RectF(0f, 0f, 1f, 1f),
    val difficultyRoi: RectF = RectF(0f, 0f, 1f, 1f)
)
data class OcrFrame(val identity: Identity?, val pauseOrResult: Boolean)

/** One background worker owns this recognizer. Keep the bitmap alive until return. */
class SongIdentifier(config: OcrConfig = OcrConfig()) : Closeable {
    private val titleRoi = validatedRoi(config.titleRoi)
    private val difficultyRoi = validatedRoi(config.difficultyRoi)
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    var lastDiagnostic: String = "等待歌曲信息"
        private set

    fun identify(bitmap: Bitmap, songs: List<SongCandidate>): Identity? =
        inspect(bitmap, songs).identity

    fun inspect(bitmap: Bitmap, songs: List<SongCandidate>): OcrFrame {
        val observation = read(bitmap)
        val identity = SongMatcher.match(
            observation.filter { inside(it, titleRoi) }.map { it.text },
            observation.filter { inside(it, difficultyRoi) }.map { it.text }, songs)
        val paused = SongMatcher.isPauseOrResult(observation.map { it.text })
        lastDiagnostic = when {
            paused -> "检测到暂停或结算界面"
            identity != null -> "已识别 " + identity.title + " · " + identity.difficulty
            observation.isEmpty() -> "画面没有可识别的文字"
            songs.isEmpty() -> "尚未导入可匹配的谱面"
            else -> "未唯一匹配曲名和难度；可调整文字识别区域或歌曲别名"
        }
        return OcrFrame(identity, paused)
    }

    fun detectPaused(bitmap: Bitmap): Boolean =
        SongMatcher.isPauseOrResult(read(bitmap).map { it.text })

    private data class Label(val text: String, val x: Float, val y: Float)
    private fun read(bitmap: Bitmap): List<Label> {
        check(Looper.myLooper() != Looper.getMainLooper()) { "OCR must run on a background worker" }
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0)
        val width = minOf(bitmap.width, 1280)
        val height = (bitmap.height.toDouble() * width / bitmap.width).roundToInt().coerceAtLeast(1)
        val scaled = if (width == bitmap.width) bitmap
            else Bitmap.createScaledBitmap(bitmap, width, height, true)
        try {
            val result = awaitRecognition(recognizer.process(InputImage.fromBitmap(scaled, 0)))
            return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val bounds = line.boundingBox ?: return@mapNotNull null
                Label(line.text, bounds.exactCenterX() / width, bounds.exactCenterY() / height)
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun awaitRecognition(task: Task<Text>): Text {
        var interrupted = false
        try {
            while (true) {
                try { return Tasks.await(task) }
                catch (_: InterruptedException) { interrupted = true }
            }
        } finally {
            // A timeout cannot release the bitmap while ML Kit may still read it.
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private fun inside(label: Label, roi: RectF): Boolean =
        label.x >= roi.left && label.x <= roi.right && label.y >= roi.top && label.y <= roi.bottom
    override fun close() = recognizer.close()

    companion object {
        internal fun validatedRoi(value: RectF): RectF {
            require(value.left.isFinite() && value.top.isFinite() &&
                value.right.isFinite() && value.bottom.isFinite() &&
                value.left >= 0f && value.top >= 0f && value.right <= 1f && value.bottom <= 1f &&
                value.left < value.right && value.top < value.bottom) {
                "识别区域必须是 0 到 1 之间的 left,top,right,bottom"
            }
            return RectF(value)
        }
    }
}
