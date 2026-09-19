package com.nokia1030cam.camera

/**
 * Shooting modes. PHOTO / PRO / VIDEO are fully implemented against Camera2/CameraX.
 * PANORAMA, PORTRAIT and NIGHT are wired end-to-end but use reference-quality
 * (not production-optimized) algorithms — see each processor's KDoc for the
 * recommended production replacement (CameraX Extensions, ML Kit, etc).
 */
enum class CaptureMode(val label: String) {
    PHOTO("Photo"),
    PRO("Pro"),
    NIGHT("Night"),
    PORTRAIT("Portrait"),
    PANORAMA("Panorama"),
    BURST("Burst"),
    VIDEO("Video")
}

/**
 * Output resolution the final photo is delivered at. `targetMegapixels = null` (MAX) skips
 * oversampling entirely and delivers the sensor's native captured resolution. Any other preset
 * triggers [com.nokia1030cam.processing.OversamplingProcessor] to combine the captured pixels
 * down to that target — the smaller the target, the more noise-reduction "oversampling" benefit,
 * exactly like the tradeoff the real Lumia 1020 exposed to Nokia's own ISP, just made visible to
 * the user here instead of fixed at ~5MP.
 */
enum class ResolutionPreset(val label: String, val targetMegapixels: Double?) {
    MAX("MAX", null),
    MP12("12MP", 12.0),
    MP8("8MP", 8.0),
    MP5("5MP", 5.0)
}

enum class FlashMode { OFF, AUTO, ON }

/**
 * Mirrors the manual dials Nokia's Pro Cam app popularized on the Lumia 1020:
 * ISO, shutter speed, exposure compensation, white balance (Kelvin) and focus distance.
 * `null` on any field means "leave that control on auto" for that capture.
 */
data class ManualControls(
    val isoSensitivity: Int? = null,        // e.g. 100..3200
    val shutterSpeedNanos: Long? = null,     // e.g. 1/4000s .. 4s, in nanoseconds
    val exposureCompensationStops: Float = 0f, // -3.0f .. +3.0f
    val whiteBalanceKelvin: Int? = null,     // e.g. 2300..6500
    val focusDistanceDiopters: Float? = null // 0f = infinity, higher = closer
) {
    companion object {
        val AUTO = ManualControls()
    }
}
