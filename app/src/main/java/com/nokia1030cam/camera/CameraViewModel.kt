package com.nokia1030cam.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
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
    val errorMessage: String? = null
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = CameraController(application)
    private val panoramaFrames = mutableListOf<Bitmap>()

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
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun selectMode(mode: CaptureMode) {
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
                    _uiState.value = _uiState.value.copy(isCapturing = false, lastCaptureUri = uri)
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
                    _uiState.value = _uiState.value.copy(isCapturing = false, lastCaptureUri = uris.lastOrNull())
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isCapturing = false, errorMessage = e.message)
                }
        }
    }

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
                        lastCaptureUri = uri
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
