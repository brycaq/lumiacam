package com.nokia1030cam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nokia1030cam.camera.ManualControls
import com.nokia1030cam.ui.theme.LumiaCyan
import com.nokia1030cam.ui.theme.TextSecondary
import kotlin.math.roundToLong

@Composable
fun ProControlsPanel(
    controls: ManualControls,
    onControlsChanged: (ManualControls) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProDialRow(
            label = "ISO",
            valueLabel = controls.isoSensitivity?.toString() ?: "AUTO",
            value = (controls.isoSensitivity ?: 100).toFloat(),
            range = 100f..3200f,
            onValueChange = { onControlsChanged(controls.copy(isoSensitivity = it.roundToInt())) }
        )
        ProDialRow(
            label = "SHUTTER",
            valueLabel = controls.shutterSpeedNanos?.let { formatShutterSpeed(it) } ?: "AUTO",
            value = (controls.shutterSpeedNanos ?: 8_333_333L).toFloat(),
            range = 1_000_000f..2_000_000_000f,
            onValueChange = { onControlsChanged(controls.copy(shutterSpeedNanos = it.roundToLong())) }
        )
        ProDialRow(
            label = "EV",
            valueLabel = "%+.1f".format(controls.exposureCompensationStops),
            value = controls.exposureCompensationStops,
            range = -3f..3f,
            onValueChange = { onControlsChanged(controls.copy(exposureCompensationStops = it)) }
        )
        ProDialRow(
            label = "WB",
            valueLabel = controls.whiteBalanceKelvin?.let { "${it}K" } ?: "AUTO",
            value = (controls.whiteBalanceKelvin ?: 5500).toFloat(),
            range = 2300f..6500f,
            onValueChange = { onControlsChanged(controls.copy(whiteBalanceKelvin = it.roundToInt())) }
        )
    }
}

@Composable
private fun ProDialRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = LumiaCyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = LumiaCyan,
                activeTrackColor = LumiaCyan,
                inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
    }
}

private fun Float.roundToInt(): Int = this.roundToLong().toInt()

private fun formatShutterSpeed(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    return if (seconds >= 1.0) {
        "%.1fs".format(seconds)
    } else {
        "1/${(1.0 / seconds).roundToLong()}"
    }
}
