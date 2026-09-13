package com.nokia1030cam.processing

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PortraitBokehProcessor {

    suspend fun apply(
        source: Bitmap,
        subjectRectPercent: RectF = RectF(0.28f, 0.15f, 0.72f, 0.85f),
        blurStrength: Float = 18f
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height

        val blurredBackground = softwareBlur(source, blurStrength)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(blurredBackground, 0f, 0f, null)

        val subjectRect = RectF(
            subjectRectPercent.left * w,
            subjectRectPercent.top * h,
            subjectRectPercent.right * w,
            subjectRectPercent.bottom * h
        )

        val featherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(w * 0.02f, BlurMaskFilter.Blur.NORMAL)
        }
        val clipPath = Path().apply { addOval(subjectRect, Path.Direction.CW) }

        val layerId = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
        canvas.drawPath(clipPath, featherPaint.apply { color = -0x1 })
        canvas.drawBitmap(
            source,
            0f, 0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            }
        )
        canvas.restoreToCount(layerId)

        output
    }

    private suspend fun softwareBlur(source: Bitmap, radius: Float): Bitmap {
        val proxyScale = 0.35f
        val proxy = Bitmap.createScaledBitmap(
            source,
            (source.width * proxyScale).toInt().coerceAtLeast(1),
            (source.height * proxyScale).toInt().coerceAtLeast(1),
            true
        )
        val blurredProxy = OversamplingProcessor.blurOnly(proxy, radius.toInt())
        return Bitmap.createScaledBitmap(blurredProxy, source.width, source.height, true)
    }
}
