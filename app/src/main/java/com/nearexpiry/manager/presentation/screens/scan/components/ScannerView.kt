package com.nearexpiry.manager.presentation.screens.scan.components

import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun ScannerView(
    cameraController: LifecycleCameraController,
    onBarcodeScanned: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit,
    modifier: Modifier = Modifier
) {
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val executor       = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // FILL_CENTER: zoom/crop the preview so it completely fills
                // the allocated box with NO black bars (letterboxing/pillarboxing).
                // The camera feed is cropped at the edges — exactly like a "cover" fit.
                // clipToBounds on the AndroidView prevents any overflow.
                scaleType = PreviewView.ScaleType.FILL_CENTER

                // COMPATIBLE (TextureView) respects view bounds strictly.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                // Use a landscape analysis resolution so barcodes on products
                // (wider than tall) are captured correctly in portrait mode.
                cameraController.setImageAnalysisTargetSize(
                    CameraController.OutputSize(Size(1280, 720))
                )

                cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
                    processImageProxy(barcodeScanner, imageProxy, onBarcodeScanned)
                }

                val lifecycleOwner = findViewTreeLifecycleOwner()
                if (lifecycleOwner != null) {
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
                this.controller = cameraController
            }
        },
        // clipToBounds clips the AndroidView to its measured Compose size
        // so the camera preview never bleeds outside the glowing scan-frame.
        modifier = modifier.clipToBounds()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.let { onResult(it) }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
