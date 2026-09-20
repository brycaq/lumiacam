package com.nokia1030cam.ui

import android.graphics.RenderEffect
import android.os.Build
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nokia1030cam.camera.CameraUiState
import com.nokia1030cam.camera.CameraViewModel
import com.nokia1030cam.camera.CaptureMode
import com.nokia1030cam.camera.FlashMode
import com.nokia1030cam.processing.ColorScienceEngine
import com.nokia1030cam.ui.theme.ChromeDark
import com.nokia1030cam.ui.theme.LumiaCyan
import com.nokia1030cam.ui.theme.TextPrimary
import com.nokia1030cam.ui.theme.TextSecondary
import com.nokia1030cam.ui.theme.ViewfinderBlack

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var previewSizePx by remember { mutableStateOf(IntSize.Zero) }

    // Fires a short "Saved" confirmation whenever a new capture completes. Gated on the event
    // counter (not the URI) so it never fires on initial composition, only on real captures.
    LaunchedEffect(uiState.lastCaptureEventId) {
        if (uiState.lastCaptureEventId > 0) {
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewfinderBlack)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewSizePx = it }
                .naturalColorPreviewEffect()
                // Pinch to zoom. detectTransformGestures reports an incremental scale factor per
                // callback tick, which we feed straight into the running zoom ratio.
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) viewModel.onPinchZoom(zoom)
                    }
                }
                // Tap to focus. Runs as a second, independent gesture detector on the same
                // surface — a genuine two-finger pinch can occasionally still register a trailing
                // single-finger "tap" as the first finger lifts; harmless (just refocuses on the
                // last pinch point) but worth knowing about rather than presenting as flawless.
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        previewViewRef?.let { pv ->
                            viewModel.onTapToFocus(
                                pv,
                                offset.x,
                                offset.y,
                                xPercent = if (previewSizePx.width > 0) offset.x / previewSizePx.width else 0.5f,
                                yPercent = if (previewSizePx.height > 0) offset.y / previewSizePx.height else 0.5f
                            )
                        }
                    }
                },
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    previewViewRef = this
                    viewModel.bindCamera(lifecycleOwner, this)
                }
            }
        )

        uiState.focusPointPercent?.let { (fx, fy) ->
            FocusReticle(fx, fy, previewSizePx)
        }

        // Top bar: stabilization badge, flash, resolution
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StabilizationBadge(uiState)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uiState.maxZoomRatio > uiState.minZoomRatio) {
                    ZoomBadge(uiState.zoomRatio)
                }
                ResolutionBadge(
                    label = uiState.resolutionPreset.label,
                    onClick = viewModel::cycleResolutionPreset
                )
                CircleIconButton(
                    icon = flashIconFor(uiState),
                    active = uiState.flashMode != FlashMode.OFF || uiState.torchEnabled,
                    onClick = viewModel::onFlashButtonPressed,
                    contentDescription = "Flash"
                )
            }
        }

        if (uiState.isPanoramaSweeping) {
            PanoramaProgressOverlay(
                frameCount = uiState.panoramaFrameCount,
                onDone = { viewModel.finishPanorama() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
            )
        }

        if (uiState.isRecording) {
            RecordingBadge(modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }

        // Bottom control cluster
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ChromeDark.copy(alpha = 0.85f))
                .padding(bottom = 20.dp)
        ) {
            if (uiState.mode == CaptureMode.PRO) {
                ProControlsPanel(
                    controls = uiState.manualControls,
                    onControlsChanged = viewModel::updateManualControls,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            CircularModeSelector(
                selectedMode = uiState.mode,
                onModeSelected = viewModel::selectMode,
                modifier = Modifier.padding(top = 12.dp)
            )

            ShutterRow(
                uiState = uiState,
                onShutter = {
                    previewViewRef?.let { viewModel.onShutterPressed(lifecycleOwner, it) }
                },
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

/**
 * Applies [ColorScienceEngine]'s color matrix to the live viewfinder itself (API 31+, where
 * [RenderEffect] color filters are available) so what you frame already looks like the final
 * "natural color" capture, not just the saved file.
 */
private fun Modifier.naturalColorPreviewEffect(): Modifier = graphicsLayer {
    if (Build.VERSION.SDK_INT >= 31) {
        val colorFilter = android.graphics.ColorMatrixColorFilter(ColorScienceEngine.naturalColorMatrix())
        renderEffect = RenderEffect.createColorFilterEffect(colorFilter).asComposeRenderEffect()
    }
}

@Composable
private fun FocusReticle(xPercent: Float, yPercent: Float, containerSizePx: IntSize) {
    val sizeDp = 56.dp
    Box(
        modifier = Modifier
            .size(sizeDp)
            .graphicsLayer {
                translationX = xPercent * containerSizePx.width - size.width / 2
                translationY = yPercent * containerSizePx.height - size.height / 2
            }
            .border(2.dp, LumiaCyan, CircleShape)
    )
}

@Composable
private fun ZoomBadge(zoomRatio: Float) {
    Text(
        text = "%.1fx".format(zoomRatio),
        color = LumiaCyan,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ChromeDark.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun ResolutionBadge(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TextPrimary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ChromeDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun flashIconFor(uiState: CameraUiState) = when {
    uiState.mode == CaptureMode.VIDEO -> if (uiState.torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff
    uiState.flashMode == FlashMode.OFF -> Icons.Filled.FlashOff
    uiState.flashMode == FlashMode.AUTO -> Icons.Filled.FlashAuto
    else -> Icons.Filled.FlashOn
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ChromeDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) LumiaCyan else TextSecondary,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun StabilizationBadge(uiState: CameraUiState) {
    val label = when {
        uiState.oisAvailable && uiState.stabilizationEnabled -> "OIS+EIS"
        uiState.stabilizationEnabled -> "EIS"
        else -> "STAB OFF"
    }
    Text(
        text = label,
        color = if (uiState.stabilizationEnabled) LumiaCyan else TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ChromeDark.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun RecordingBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ChromeDark.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = Color.Red, modifier = Modifier.size(12.dp))
        Text("REC", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PanoramaProgressOverlay(frameCount: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(ChromeDark.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sweep: $frameCount frames", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
        Text(
            "Done",
            color = LumiaCyan,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable(onClick = onDone)
        )
    }
}

/** Maps each capture mode to a representative icon for the circular mode carousel. */
private fun iconFor(mode: CaptureMode) = when (mode) {
    CaptureMode.PHOTO -> Icons.Filled.PhotoCamera
    CaptureMode.PRO -> Icons.Filled.Tune
    CaptureMode.NIGHT -> Icons.Filled.NightsStay
    CaptureMode.PORTRAIT -> Icons.Filled.Person
    CaptureMode.PANORAMA -> Icons.Filled.Panorama
    CaptureMode.BURST -> Icons.Filled.BurstMode
    CaptureMode.VIDEO -> Icons.Filled.Videocam
}

/**
 * A horizontal carousel of circular icon "pucks" — closer to the Lumia Pro Cam's circular chrome
 * aesthetic than a plain text row. The selected mode's puck fills solid cyan; others stay outlined.
 * (A full rotary drag-dial, where you spin a ring to change modes, is a further step beyond this —
 * this keeps the circular visual language without the much larger gesture-engineering lift.)
 */
@Composable
private fun CircularModeSelector(
    selectedMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(CaptureMode.entries.toList()) { mode ->
            val isSelected = mode == selectedMode
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) LumiaCyan else ChromeDark)
                        .border(1.dp, if (isSelected) LumiaCyan else TextSecondary.copy(alpha = 0.5f), CircleShape)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        iconFor(mode),
                        contentDescription = mode.label,
                        tint = if (isSelected) ViewfinderBlack else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = mode.label,
                    color = if (isSelected) LumiaCyan else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ShutterRow(
    uiState: CameraUiState,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ShutterButton(uiState = uiState, onClick = onShutter)
    }
}

@Composable
private fun RowScope.ShutterButton(uiState: CameraUiState, onClick: () -> Unit) {
    val isVideo = uiState.mode == CaptureMode.VIDEO
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
            .background(if (uiState.isRecording) Color.Red else Color.Transparent, CircleShape)
            .clickable(enabled = !uiState.isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isVideo && uiState.isRecording) Icons.Filled.Stop else Icons.Filled.CameraAlt,
            contentDescription = "Shutter",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}
