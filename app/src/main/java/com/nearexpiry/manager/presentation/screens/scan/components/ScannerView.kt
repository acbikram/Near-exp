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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * How long the same barcode must be seen continuously before we fire the
 * callback. 1 second eliminates virtually all accidental misreads — a
 * partial/blurry decode gives a different value, which resets the timer.
 */
private const val CONFIRM_DURATION_MS = 1000L

@Composable
fun ScannerView(
    cameraController: LifecycleCameraController,
    onBarcodeScanned: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit,
    modifier: Modifier = Modifier
) {
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val executor       = remember { Executors.newSingleThreadExecutor() }

    // Thread-safe confirmation state — written from the analyser executor thread.
    val candidateValue    = remember { AtomicReference("") }
    val candidateFirstMs  = remember { AtomicLong(0L) }
    // Prevent firing multiple times for the same confirmed barcode.
    val lastFiredValue    = remember { AtomicReference("") }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                cameraController.setImageAnalysisTargetSize(
                    CameraController.OutputSize(Size(1280, 720))
                )

                cameraController.setImageAnalysisAnalyzer(executor) { imageProxy ->
                    processImageProxy(
                        scanner           = barcodeScanner,
                        imageProxy        = imageProxy,
                        candidateValue    = candidateValue,
                        candidateFirstMs  = candidateFirstMs,
                        lastFiredValue    = lastFiredValue,
                        onConfirmed       = onBarcodeScanned
                    )
                }

                val lifecycleOwner = findViewTreeLifecycleOwner()
                if (lifecycleOwner != null) {
                    cameraController.bindToLifecycle(lifecycleOwner)
                }
                this.controller = cameraController
            }
        },
        modifier = modifier.clipToBounds()
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    candidateValue: AtomicReference<String>,
    candidateFirstMs: AtomicLong,
    lastFiredValue: AtomicReference<String>,
    onConfirmed: (com.google.mlkit.vision.barcode.common.Barcode) -> Unit
) {
    val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val barcode = barcodes.firstOrNull() ?: run {
                // Nothing detected this frame — reset candidate so a new scan
                // starts fresh (avoids counting gaps toward the 1-second window).
                candidateValue.set("")
                candidateFirstMs.set(0L)
                return@addOnSuccessListener
            }

            val raw = barcode.rawValue ?: return@addOnSuccessListener
            val now = System.currentTimeMillis()

            if (raw == candidateValue.get()) {
                // Same barcode as last frame — check if we've held it long enough.
                val elapsed = now - candidateFirstMs.get()
                if (elapsed >= CONFIRM_DURATION_MS && raw != lastFiredValue.get()) {
                    // Confirmed — fire exactly once, then reset so the user has
                    // to move the camera away before the same barcode fires again.
                    lastFiredValue.set(raw)
                    candidateValue.set("")
                    candidateFirstMs.set(0L)
                    onConfirmed(barcode)
                }
                // else: still accumulating time — do nothing this frame
            } else {
                // Different barcode (or first frame) — start a new confirmation window.
                candidateValue.set(raw)
                candidateFirstMs.set(now)
                // Allow the new barcode to fire even if the old one was the same string
                // (e.g. user scans same item twice deliberately after putting it away).
                if (raw != lastFiredValue.get()) {
                    lastFiredValue.set("")
                }
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
