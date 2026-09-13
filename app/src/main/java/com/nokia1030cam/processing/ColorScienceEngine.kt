package com.nokia1030cam.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ColorScienceEngine {

    private const val SATURATION = 0.92f
    private const val CONTRAST = 1.06f
    private const val BRIGHTNESS_OFFSET = 2f
    private const val WARMTH_R = 1.035f
    private const val WARMTH_B = 0.975f

    fun naturalColorMatrix(): ColorMatrix {
        val warmth = ColorMatrix(
            floatArrayOf(
                WARMTH_R, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, WARMTH_B, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val saturation = ColorMatrix().apply { setSaturation(SATURATION) }

        val contrastTranslate = (-0.5f * CONTRAST + 0.5f) * 255f + BRIGHTNESS_OFFSET
        val contrast = ColorMatrix(
            floatArrayOf(
                CONTRAST, 0f, 0f, 0f, contrastTranslate,
                0f, CONTRAST, 0f, 0f, contrastTranslate,
                0f, 0f, CONTRAST, 0f, contrastTranslate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val result = ColorMatrix(warmth)
        result.postConcat(saturation)
        result.postConcat(contrast)
        return result
    }

    fun previewColorFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(naturalColorMatrix())

    fun applyNaturalColorScience(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(naturalColorMatrix())
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
