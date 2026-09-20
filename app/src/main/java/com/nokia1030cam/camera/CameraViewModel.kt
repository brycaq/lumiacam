package com.nokia1030cam.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CameraUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val manualControls: ManualControls = ManualControls.AUTO,
    val stabilizationEnabled: Boolean = true,
    val isRecording: Boolean = false,
    val isCapturing: Boolean = false,
    val isPanoramaSweeping: Boolean = false,
    val panoramaFrameCount: Int = 0,
    val oisAvailable: Boolean = false,
    val lastCaptureUri: Uri? = null,
    val lastCaptureEventId: Long = 0L,
    val errorMessage: String? = null,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val flashMode: FlashMode = FlashMode.OFF,
    val torchEnabled: Boolean = false,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.MP8,
    // Fraction (0f..1f) of the viewfinder where the user last tapped to focus; null when no
    // focus reticle should be shown. Cleared automatically a moment after it's set.
    val focusPointPercent: Pair<Float, Float>? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = CameraController(application)
    private val panoramaFrames = mutableListOf<Bitmap>()
    private var focusIndicatorJob: kotlinx.coroutines.Job? = null
    private var captureEventCounter = 0L

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            runCatching {
                controller.bindToLifecycle(
                    lifecycleOwner,
                    previewView,
                    _uiState.value.manualControls,
                    _uiState.value.stabilizationEnabled
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(oisAvailable = controller.isOisAvailable())
                controller.observeZoomState(lifecycleOwner) { current, min, max ->
                    _uiState.value = _uiState.value.copy(
                        zoomRatio = current,
                        minZoomRatio = min,
                        maxZoomRatio = max
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun selectMode(mode: CaptureMode) {
        // Leaving video mode should drop any torch we left on.
        if (_uiState.value.mode == CaptureMode.VIDEO && mode != CaptureMode.VIDEO && _uiState.value.torchEnabled) {
            controller.setTorchEnabled(false)
            _uiState.value = _uiState.value.copy(torchEnabled = false)
        }
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun updateManualControls(controls: ManualControls) {
        _uiState.value = _uiState.value.copy(manualControls = controls)
        controller.updateLiveControls(controls)
    }

    fun toggleStabilization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(stabilizationEnabled = enabled)
        controller.updateStabilization(enabled)
    }

    /** Multiplies zoom by a pinch-gesture scale delta; the authoritative new value arrives via observeZoomState. */
    fun onPinchZoom(scaleFactor: Float) {
        controller.onPinchZoom(scaleFactor)
    }

    fun setZoomRatio(ratio: Float) {
        controller.setZoomRatio(ratio)
    }

    /** Focuses at a tapped point and shows a brief reticle there. */
    fun onTapToFocus(previewView: PreviewView, xPx: Float, yPx: Float, xPercent: Float, yPercent: Float) {
        controller.tapToFocus(previewView, xPx, yPx)
        _uiState.value = _uiState.value.copy(focusPointPercent = xPercent to yPercent)
        focusIndicatorJob?.cancel()
        focusIndicatorJob = viewModelScope.launch {
            delay(900)
            _uiState.value = _uiState.value.copy(focusPointPercent = null)
        }
    }

    /** In VIDEO mode this toggles the torch; in photo modes it cycles OFF → AUTO → ON. */
    fun onFlashButtonPressed() {
        if (_uiState.value.mode == CaptureMode.VIDEO) {
            val newTorchState = !_uiState.value.torchEnabled
            controller.setTorchEnabled(newTorchState)
            _uiState.value = _uiState.value.copy(torchEnabled = newTorchState)
            return
        }
        val next = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        controller.setFlashMode(next)
        _uiState.value = _uiState.value.copy(flashMode = next)
    }

    /** Cycles through the available output-resolution presets. */
    fun cycleResolutionPreset() {
        val presets = ResolutionPreset.entries
        val next = presets[(presets.indexOf(_uiState.value.resolutionPreset) + 1) % presets.size]
        controller.setResolutionPreset(next)
        _uiState.value = _uiState.value.copy(resolutionPreset = next)
    }

    fun onShutterPressed(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val mode = _uiState.value.mode
        when (mode) {
            CaptureMode.VIDEO -> toggleVideoRecording()
            CaptureMode.PANORAMA -> handlePanoramaShutter(lifecycleOwner, previewView)
            CaptureMode.BURST -> captureBurst()
            else -> captureSingle(mode)
        }
    }

    private fun captureSingle(mode: CaptureMode) {
        if (_uiState.value.isCapturing) return
        _uiState.value = _uiState.value.copy(isCapturing = true)
        viewModelScope.launch {
            runCatching { controller.capturePhoto(mode) }
                .onSuccess { uri ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        lastCaptureUri = uri,
                        lastCaptureEventId = ++captureEventCounter
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isCapturing = false, errorMessage = e.message)
                }
        }
    }

    private fun captureBurst() {
        if (_uiState.value.isCapturing) return
        _uiState.value = _uiState.value.copy(isCapturing = true)
        viewModelScope.launch {
            runCatching { controller.captureBurst() }
                .onSuccess { uris ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        lastCaptureUri = uris.lastOrNull(),
                        lastCaptureEventId = ++captureEventCounter
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isCapturing = false, errorMessage = e.message)
                }
        }
    }

    /** Each shutter press while sweeping grabs one frame; a long-press-free simple flow. */
    private fun handlePanoramaShutter(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val state = _uiState.value
        if (!state.isPanoramaSweeping) {
            panoramaFrames.clear()
            _uiState.value = state.copy(isPanoramaSweeping = true, panoramaFrameCount = 0)
            return
        }

        viewModelScope.launch {
            runCatching { controller.capturePanoramaFrame() }
                .onSuccess { frame ->
                    panoramaFrames += frame
                    _uiState.value = _uiState.value.copy(panoramaFrameCount = panoramaFrames.size)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
        }
    }

    fun finishPanorama() {
        if (panoramaFrames.isEmpty()) {
            _uiState.value = _uiState.value.copy(isPanoramaSweeping = false, panoramaFrameCount = 0)
            return
        }
        _uiState.value = _uiState.value.copy(isCapturing = true)
        viewModelScope.launch {
            val framesSnapshot = panoramaFrames.toList()
            panoramaFrames.clear()
            runCatching { controller.finishPanorama(framesSnapshot) }
                .onSuccess { uri ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isPanoramaSweeping = false,
                        panoramaFrameCount = 0,
                        lastCaptureUri = uri,
                        lastCaptureEventId = ++captureEventCounter
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        isPanoramaSweeping = false,
                        panoramaFrameCount = 0,
                        errorMessage = e.message
                    )
                }
        }
    }

    private fun toggleVideoRecording() {
        if (_uiState.value.isRecording) {
            controller.stopVideoRecording()
            _uiState.value = _uiState.value.copy(isRecording = false)
            return
        }

        controller.startVideoRecording { event ->
            when (event) {
                is VideoRecordEvent.Start -> _uiState.value = _uiState.value.copy(isRecording = true)
                is VideoRecordEvent.Finalize -> {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false,
                        lastCaptureUri = if (!event.hasError()) event.outputResults.outputUri else null,
                        lastCaptureEventId = if (!event.hasError()) ++captureEventCounter else _uiState.value.lastCaptureEventId,
                        errorMessage = if (event.hasError()) "Recording error: ${event.error}" else null
                    )
                }
                else -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
