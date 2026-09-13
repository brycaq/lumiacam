package com.nokia1030cam.ui

import android.graphics.RenderEffect
import android.os.Build
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nokia1030cam.camera.CameraUiState
import com.nokia1030cam.camera.CameraViewModel
import com.nokia1030cam.camera.CaptureMode
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewfinderBlack)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .naturalColorPreviewEffect(),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StabilizationBadge(uiState)
            IconButton(onClick = { viewModel.toggleStabilization(!uiState.stabilizationEnabled) }) {
                Icon(
                    Icons.Filled.Videocam,
                    contentDescription = "Toggle stabilization",
                    tint = if (uiState.stabilizationEnabled) LumiaCyan else TextSecondary
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

            ModeSelector(
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

private fun Modifier.naturalColorPreviewEffect(): Modifier = graphicsLayer {
    if (Build.VERSION.SDK_INT >= 31) {
        val colorFilter = android.graphics.ColorMatrixColorFilter(ColorScienceEngine.naturalColorMatrix())
        renderEffect = RenderEffect.createColorFilterEffect(colorFilter).asComposeRenderEffect()
    }
}

@Composable
private fun StabilizationBadge(uiState: CameraUiState) {
    val label = when {
        uiState.oisAvailable && uiState.stabilizationEnabled -> "OIS+EIS"
        uiState.stabilizationEnabled -> "EIS"
        else -> "STABILIZATION OFF"
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

@Composable
private fun ModeSelector(
    selectedMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        items(CaptureMode.entries.toList()) { mode ->
            val isSelected = mode == selectedMode
            Text(
                text = mode.label.uppercase(),
                color = if (isSelected) LumiaCyan else TextSecondary,
                style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { onModeSelected(mode) }
            )
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
