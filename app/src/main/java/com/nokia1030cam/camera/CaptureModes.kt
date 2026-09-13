package com.nokia1030cam.camera

enum class CaptureMode(val label: String) {
    PHOTO("Photo"),
    PRO("Pro"),
    NIGHT("Night"),
    PORTRAIT("Portrait"),
    PANORAMA("Panorama"),
    BURST("Burst"),
    VIDEO("Video")
}

data class ManualControls(
    val isoSensitivity: Int? = null,
    val shutterSpeedNanos: Long? = null,
    val exposureCompensationStops: Float = 0f,
    val whiteBalanceKelvin: Int? = null,
    val focusDistanceDiopters: Float? = null
) {
    companion object {
        val AUTO = ManualControls()
    }
}
