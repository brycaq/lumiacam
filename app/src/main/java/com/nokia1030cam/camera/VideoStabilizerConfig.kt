package com.nokia1030cam.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.UseCase

object VideoStabilizerConfig {

    fun deviceSupportsOis(characteristics: CameraCharacteristics): Boolean {
        val modes = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        ) ?: return false
        return modes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
    }

    fun <T : UseCase> applyTo(
        builder: ExtendableBuilder<T>,
        oisAvailable: Boolean,
        enabled: Boolean = true
    ) {
        val extender = Camera2Interop.Extender(builder)

        if (!enabled) {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            )
            extender.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
            )
            return
        }

        val eisMode = if (Build.VERSION.SDK_INT >= 33) {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
        } else {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        }
        extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, eisMode)

        if (oisAvailable) {
            extender.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
            )
        }
    }
}
