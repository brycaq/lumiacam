package com.nokia1030cam.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PanoramaStitcher {

    suspend fun stitch(frames: List<Bitmap>, overlapFraction: Float = 0.15f): Bitmap =
        withContext(Dispatchers.Default) {
            require(frames.isNotEmpty()) { "Need at least one frame to stitch" }
            if (frames.size == 1) return@withContext frames.first()

            val frameHeight = frames.first().height
            val keepWidthPerFrame = (frames.first().width * (1f - overlapFraction)).toInt()
            val totalWidth = keepWidthPerFrame * (frames.size - 1) + frames.last().width

            val output = Bitmap.createBitmap(totalWidth, frameHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            var xOffset = 0
            frames.forEachIndexed { index, frame ->
                canvas.drawBitmap(frame, xOffset.toFloat(), 0f, null)
                xOffset += if (index < frames.lastIndex) keepWidthPerFrame else frame.width
            }

            output
        }
}
