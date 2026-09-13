package com.nokia1030cam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nokia1030cam.camera.CameraViewModel
import com.nokia1030cam.ui.CameraScreen
import com.nokia1030cam.ui.theme.Nokia1030CamTheme
import com.nokia1030cam.ui.theme.ViewfinderBlack

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var permissionsGranted by remember { mutableStateOf(hasAllPermissions()) }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { results -> permissionsGranted = results.values.all { it } }

            Nokia1030CamTheme {
                if (permissionsGranted) {
                    CameraScreen(viewModel = cameraViewModel)
                } else {
                    PermissionRationaleScreen(onRequest = { launcher.launch(REQUIRED_PERMISSIONS) })
                }
            }
        }
    }

    private fun hasAllPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun PermissionRationaleScreen(onRequest: () -> Unit) {
    LaunchedEffect(Unit) { onRequest() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewfinderBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.permission_rationale),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
