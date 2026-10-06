package io.github.phiscript.capture
import io.github.phiscript.engine.Viewport

internal object PhiraViewport {
    fun bounds(screenWidth: Int, screenHeight: Int, aspectRatio: Double, forceAspectRatio: Boolean): Viewport {
        require(screenWidth > 0 && screenHeight > 0 && aspectRatio.isFinite() && aspectRatio > 0)
        val screenAspect = screenWidth.toDouble() / screenHeight
        val aspect = if (forceAspectRatio) aspectRatio else minOf(aspectRatio, screenAspect)
        val width: Double; val height: Double
        if (aspect <= screenAspect) { height = screenHeight.toDouble(); width = height * aspect }
        else { width = screenWidth.toDouble(); height = width / aspect }
        return Viewport(((screenWidth - width) / 2).toFloat(), ((screenHeight - height) / 2).toFloat(),
            width.toFloat(), height.toFloat())
    }
}
