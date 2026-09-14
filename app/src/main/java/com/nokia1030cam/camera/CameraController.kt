package com.nokia1030cam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nokia1030cam.processing.ColorScienceEngine
import com.nokia1030cam.processing.NightModeStacker
import com.nokia1030cam.processing.OversamplingProcessor
import com.nokia1030cam.processing.PanoramaStitcher
import com.nokia1030cam.processing.PortraitBokehProcessor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

private const val TAG = "CameraController"
private const val NIGHT_MODE_FRAME_COUNT = 6
private const val BURST_FRAME_COUNT = 8

class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var oisAvailable: Boolean = false
    private var currentManualControls: ManualControls = ManualControls.AUTO
    private var currentStabilizationEnabled: Boolean = true

    private val outputExecutor = ContextCompat.getMainExecutor(context)

    suspend fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        manualControls: ManualControls,
        stabilizationEnabled: Boolean
    ) {
        val provider = getOrCreateCameraProvider()
        provider.unbindAll()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val captureBuilder = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        applyManualControls(captureBuilder, manualControls)
        val newImageCapture = captureBuilder.build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        val videoBuilder = VideoCapture.Builder(recorder)
        oisAvailable = queryOisSupport(provider, cameraSelector)
        VideoStabilizerConfig.applyTo(videoBuilder, oisAvailable, stabilizationEnabled)
        val newVideoCapture = videoBuilder.build()

        camera = provider.bindToLifecycle(
            lifecycleOwner, cameraSelector, preview, newImageCapture, newVideoCapture
        )

        imageCapture = newImageCapture
        videoCapture = newVideoCapture
        cameraProvider = provider
        currentManualControls = manualControls
        currentStabilizationEnabled = stabilizationEnabled

        pushCombinedLiveOptions()
    }

    fun updateLiveControls(controls: ManualControls) {
        currentManualControls = controls
        pushCombinedLiveOptions()
        applyExposureCompensation(controls.exposureCompensationStops)
    }

    fun updateStabilization(enabled: Boolean) {
        currentStabilizationEnabled = enabled
        pushCombinedLiveOptions()
    }

    private fun pushCombinedLiveOptions() {
        val cameraControl = camera?.cameraControl ?: return
        val optionsBuilder = CaptureRequestOptions.Builder()
        val controls = currentManualControls

        var manualAeRequested = false
        controls.isoSensitivity?.let {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it)
            manualAeRequested = true
        }
        controls.shutterSpeedNanos?.let {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
            manualAeRequested = true
        }
        optionsBuilder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            if (manualAeRequested) CaptureRequest.CONTROL_AE_MODE_OFF else CaptureRequest.CONTROL_AE_MODE_ON
        )

        controls.whiteBalanceKelvin?.let { kelvin ->
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX
            )
            optionsBuilder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToRggbGains(kelvin))
        }
        controls.focusDistanceDiopters?.let {
            optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, it)
        }

        val eisMode = if (!currentStabilizationEnabled) {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        } else if (Build.VERSION.SDK_INT >= 33) {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
        } else {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        }
        optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, eisMode)
        if (oisAvailable) {
            optionsBuilder.setCaptureRequestOption(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (currentStabilizationEnabled) {
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                } else {
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                }
            )
        }

        Camera2CameraControl.from(cameraControl).setCaptureRequestOptions(optionsBuilder.build())
    }

    private fun applyExposureCompensation(stops: Float) {
        val cam = camera ?: return
        val exposureState = cam.cameraInfo.exposureState
        if (!exposureState.isExposureCompensationSupported) return
        val stepFraction = exposureState.exposureCompensationStep.toFloat()
        if (stepFraction <= 0f) return
        val index = (stops / stepFraction).roundToInt()
            .coerceIn(exposureState.exposureCompensationRange.lower, exposureState.exposureCompensationRange.upper)
        cam.cameraControl.setExposureCompensationIndex(index)
    }

    suspend fun capturePhoto(mode: CaptureMode): Uri {
        val capture = imageCapture ?: error("Camera not bound yet")

        val processedBitmap: Bitmap = when (mode) {
            CaptureMode.PHOTO, CaptureMode.PRO -> {
                val raw = captureSingleFrame(capture)
                val oversampled = OversamplingProcessor.oversample(raw)
                ColorScienceEngine.applyNaturalColorScience(oversampled)
            }

            CaptureMode.NIGHT -> {
                val frames = List(NIGHT_MODE_FRAME_COUNT) { captureSingleFrame(capture) }
                val stacked = NightModeStacker.stack(frames)
                val oversampled = OversamplingProcessor.oversample(stacked)
                ColorScienceEngine.applyNaturalColorScience(oversampled)
            }

            CaptureMode.PORTRAIT -> {
                val raw = captureSingleFrame(capture)
                val bokeh = PortraitBokehProcessor.apply(raw)
                ColorScienceEngine.applyNaturalColorScience(bokeh)
            }

            CaptureMode.BURST -> {
                val raw = captureSingleFrame(capture)
                ColorScienceEngine.applyNaturalColorScience(raw)
            }

            CaptureMode.PANORAMA -> error("Use capturePanoramaSweep() for panorama mode")
            CaptureMode.VIDEO -> error("Use startVideoRecording() for video mode")
        }

        return saveBitmapToMediaStore(processedBitmap)
    }

    suspend fun captureBurst(): List<Uri> {
        val capture = imageCapture ?: error("Camera not bound yet")
        val uris = mutableListOf<Uri>()
        repeat(BURST_FRAME_COUNT) {
            val raw = captureSingleFrame(capture)
            val processed = ColorScienceEngine.applyNaturalColorScience(raw)
            uris += saveBitmapToMediaStore(processed)
        }
        return uris
    }

    suspend fun capturePanoramaFrame(): Bitmap {
        val capture = imageCapture ?: error("Camera not bound yet")
        return captureSingleFrame(capture)
    }

    suspend fun finishPanorama(frames: List<Bitmap>): Uri {
        val stitched = PanoramaStitcher.stitch(frames)
        val colorGraded = ColorScienceEngine.applyNaturalColorScience(stitched)
        return saveBitmapToMediaStore(colorGraded)
    }

    fun startVideoRecording(onEvent: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: error("Camera not bound yet")
        val name = "1030CAM_${timestamp()}.mp4"

        val pendingRecording = if (Build.VERSION.SDK_INT >= 29) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            capture.output.prepareRecording(context, outputOptions)
        } else {
            val file = File(context.getExternalFilesDir(null), name)
            val outputOptions = FileOutputOptions.Builder(file).build()
            capture.output.prepareRecording(context, outputOptions)
        }

        activeRecording = pendingRecording
            .apply {
                if (ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(outputExecutor, onEvent)
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isOisAvailable(): Boolean = oisAvailable

    private suspend fun getOrCreateCameraProvider(): ProcessCameraProvider =
        cameraProvider ?: suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }, outputExecutor)
        }

    private fun applyManualControls(builder: ImageCapture.Builder, controls: ManualControls) {
        if (controls == ManualControls.AUTO) return
        val extender = Camera2Interop.Extender(builder)

        controls.isoSensitivity?.let {
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it)
        }
        controls.shutterSpeedNanos?.let {
            extender.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
        }
        if (controls.isoSensitivity != null || controls.shutterSpeedNanos != null) {
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        }
        controls.whiteBalanceKelvin?.let { kelvin ->
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            extender.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            extender.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToRggbGains(kelvin))
        }
        controls.focusDistanceDiopters?.let {
            extender.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            extender.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, it)
        }
    }

    private fun kelvinToRggbGains(kelvin: Int): android.hardware.camera2.params.RggbChannelVector {
        val temp = kelvin.coerceIn(1000, 40000) / 100.0
        val red: Double
        val blue: Double
        if (temp <= 66) {
            red = 255.0
        } else {
            red = 329.698727446 * Math.pow(temp - 60, -0.1332047592)
        }
        blue = if (temp >= 66) {
            255.0
        } else if (temp <= 19) {
            0.0
        } else {
            138.5177312231 * Math.log(temp - 10) - 305.0447927307
        }
        val rGain = (red.coerceIn(0.0, 255.0) / 255.0 * 2.0).toFloat().coerceAtLeast(0.5f)
        val bGain = (blue.coerceIn(0.0, 255.0) / 255.0 * 2.0).toFloat().coerceAtLeast(0.5f)
        return android.hardware.camera2.params.RggbChannelVector(rGain, 1f, 1f, bGain)
    }

    private fun queryOisSupport(provider: ProcessCameraProvider, selector: CameraSelector): Boolean {
        return try {
            val cameraInfo = selector.filter(provider.availableCameraInfos).firstOrNull() ?: return false
            val characteristics = Camera2CameraInfo.extractCameraCharacteristics(cameraInfo)
            VideoStabilizerConfig.deviceSupportsOis(characteristics)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not query OIS support", t)
            false
        }
    }

    private suspend fun captureSingleFrame(capture: ImageCapture): Bitmap =
        suspendCancellableCoroutine { cont ->
            capture.takePicture(
                outputExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
                        cont.resume(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    private fun imageProxyToBitmap(image: androidx.camera.core.ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        return if (image.imageInfo.rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri {
        val name = "1030CAM_${timestamp()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/1030Cam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        if (Build.VERSION.SDK_INT >= 29) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        return uri
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
}
