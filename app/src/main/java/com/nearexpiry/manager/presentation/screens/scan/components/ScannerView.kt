package com.nearexpiry.manager.presentation.screens.scan.components

import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * How long the same barcode must be seen continuously before we fire the
 * callback. 500 ms filters out single-frame misreads while still feeling
 * near-instant to the user.
 */
private const val CONFIRM_DURATION_MS = 500L

/**
 * Reusable CameraX and ML Kit surface. The lifecycle owner is passed directly
 * from Compose instead of being looked up while the [PreviewView] is being
 * created. This is essential inside dialogs: the dialog window can attach the
 * view after its factory runs, making findViewTreeLifecycleOwner() null and
 * leaving a permanently black preview.
 */
@Composable
fun ScannerView(
    cameraController: LifecycleCameraController,
    onBarcodeScanned: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    bindingKey: Int = 0,
    onCameraError: (Throwable) -> Unit = {}
) {
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val latestOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val latestOnCameraError by rememberUpdatedState(onCameraError)

    // Thread-safe confirmation state — written from the analyser executor thread.
    val candidateValue = remember { AtomicReference("") }
    val candidateFirstMs = remember { AtomicLong(0L) }
    // Prevent firing multiple times for the same confirmed barcode.
    val lastFiredValue = remember { AtomicReference("") }

    DisposableEffect(cameraController, barcodeScanner, executor) {
        cameraController.setImageAnalysisTargetSize(
            CameraController.OutputSize(Size(1280, 720))
        )
        cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
            processImageProxy(
                scanner = barcodeScanner,
                imageProxy = imageProxy,
                candidateValue = candidateValue,
                candidateFirstMs = candidateFirstMs,
                lastFiredValue = lastFiredValue,
                onConfirmed = { barcode -> latestOnBarcodeScanned(barcode) }
            )
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
            executor.shutdown()
        }
    }

    // Bind after composition, including when ScannerView is hosted in an
    // AlertDialog. Binding through a supplied owner avoids an attachment race.
    LaunchedEffect(cameraController, lifecycleOwner, bindingKey) {
        try {
            cameraController.unbind()
            cameraController.bindToLifecycle(lifecycleOwner)
        } catch (error: Throwable) {
            latestOnCameraError(error)
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                controller = cameraController
            }
        },
        update = { preview -> preview.controller = cameraController },
        modifier = modifier.clipToBounds()
    )
}

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    candidateValue: AtomicReference<String>,
    candidateFirstMs: AtomicLong,
    lastFiredValue: AtomicReference<String>,
    onConfirmed: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val barcode = barcodes.firstOrNull() ?: run {
                // Nothing detected this frame — reset candidate so a new scan
                // starts fresh (avoids counting gaps toward the confirmation window).
                candidateValue.set("")
                candidateFirstMs.set(0L)
                return@addOnSuccessListener
            }

            val raw = barcode.rawValue ?: return@addOnSuccessListener
            val now = System.currentTimeMillis()

            if (raw == candidateValue.get()) {
                val elapsed = now - candidateFirstMs.get()
                if (elapsed >= CONFIRM_DURATION_MS && raw != lastFiredValue.get()) {
                    lastFiredValue.set(raw)
                    candidateValue.set("")
                    candidateFirstMs.set(0L)
                    onConfirmed(barcode)
                }
            } else {
                candidateValue.set(raw)
                candidateFirstMs.set(now)
                if (raw != lastFiredValue.get()) {
                    lastFiredValue.set("")
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
