package com.nokia1030cam.processing

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NightModeStacker {

    suspend fun stack(frames: List<Bitmap>): Bitmap = withContext(Dispatchers.Default) {
        require(frames.isNotEmpty()) { "Need at least one frame to stack" }
        if (frames.size == 1) return@withContext frames.first()

        val w = frames.first().width
        val h = frames.first().height
        val pixelCount = w * h
        val accumulatorR = LongArray(pixelCount)
        val accumulatorG = LongArray(pixelCount)
        val accumulatorB = LongArray(pixelCount)
        val scratch = IntArray(pixelCount)

        for (frame in frames) {
            require(frame.width == w && frame.height == h) { "All frames must share dimensions" }
            frame.getPixels(scratch, 0, w, 0, 0, w, h)
            for (i in 0 until pixelCount) {
                val px = scratch[i]
                accumulatorR[i] += (px ushr 16) and 0xFF
                accumulatorG[i] += (px ushr 8) and 0xFF
                accumulatorB[i] += px and 0xFF
            }
        }

        val n = frames.size
        val result = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val r = (accumulatorR[i] / n).toInt()
            val g = (accumulatorG[i] / n).toInt()
            val b = (accumulatorB[i] / n).toInt()
            result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        output
    }
}
