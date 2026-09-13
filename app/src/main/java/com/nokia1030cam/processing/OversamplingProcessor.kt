package com.nokia1030cam.processing

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

object OversamplingProcessor {

    const val DEFAULT_OVERSAMPLING_RATIO = 2.86f

    suspend fun oversample(
        source: Bitmap,
        outputRatio: Float = DEFAULT_OVERSAMPLING_RATIO
    ): Bitmap = withContext(Dispatchers.Default) {
        val targetWidth = max(1, (source.width / outputRatio).roundToInt())
        val targetHeight = max(1, (source.height / outputRatio).roundToInt())

        val blurRadius = max(1, (outputRatio / 2f).roundToInt())
        val blurred = boxBlurApproximateGaussian(source, blurRadius, passes = 3)

        Bitmap.createScaledBitmap(blurred, targetWidth, targetHeight, true)
    }

    suspend fun blurOnly(source: Bitmap, radius: Int, passes: Int = 3): Bitmap =
        withContext(Dispatchers.Default) {
            boxBlurApproximateGaussian(source, max(1, radius), passes)
        }

    private fun boxBlurApproximateGaussian(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        repeat(passes) {
            pixels = boxBlurHorizontal(pixels, w, h, radius)
            pixels = boxBlurVertical(pixels, w, h, radius)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlurHorizontal(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val rowStart = y * w
            for (x in 0 until w) {
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var count = 0
                for (dx in -radius..radius) {
                    val sx = x + dx
                    if (sx in 0 until w) {
                        val px = src[rowStart + sx]
                        a += (px ushr 24) and 0xFF
                        r += (px ushr 16) and 0xFF
                        g += (px ushr 8) and 0xFF
                        b += px and 0xFF
                        count++
                    }
                }
                dst[rowStart + x] = packArgb(a / count, r / count, g / count, b / count)
            }
        }
        return dst
    }

    private fun boxBlurVertical(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val dst = IntArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var count = 0
                for (dy in -radius..radius) {
                    val sy = y + dy
                    if (sy in 0 until h) {
                        val px = src[sy * w + x]
                        a += (px ushr 24) and 0xFF
                        r += (px ushr 16) and 0xFF
                        g += (px ushr 8) and 0xFF
                        b += px and 0xFF
                        count++
                    }
                }
                dst[y * w + x] = packArgb(a / count, r / count, g / count, b / count)
            }
        }
        return dst
    }

    private fun packArgb(a: Long, r: Long, g: Long, b: Long): Int =
        ((a.toInt() and 0xFF) shl 24) or
            ((r.toInt() and 0xFF) shl 16) or
            ((g.toInt() and 0xFF) shl 8) or
            (b.toInt() and 0xFF)
}
