package com.nearexpiry.manager.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nearexpiry.manager.R
import com.nearexpiry.manager.presentation.screens.scan.components.ScannerView

/**
 * A narrow pairing-only QR camera surface. It is composed only after the user
 * explicitly chooses Pair with Price Tag PC, so it cannot trigger a camera
 * permission prompt elsewhere in the app.
 */
@Composable
fun PriceTagPairingQrScannerDialog(
    onPayloadScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val controller = remember { LifecycleCameraController(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf(false) }
    var bindAttempt by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        cameraError = false
        if (granted) bindAttempt++
    }

    LaunchedEffect(Unit) {
        controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.price_tag_scan_qr_title)) },
        text = {
            when {
                !hasCameraPermission -> Text(stringResource(R.string.price_tag_camera_permission))
                cameraError -> Text(stringResource(R.string.price_tag_camera_preview_error))
                else -> {
                    Box(Modifier.height(320.dp)) {
                        ScannerView(
                            cameraController = controller,
                            onBarcodeScanned = { barcode ->
                                barcode.rawValue?.let(onPayloadScanned)
                            },
                            bindingKey = bindAttempt,
                            onCameraError = { cameraError = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                !hasCameraPermission -> {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.price_tag_allow_camera))
                    }
                }
                cameraError -> {
                    Button(onClick = {
                        cameraError = false
                        bindAttempt++
                    }) { Text(stringResource(R.string.retry)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
