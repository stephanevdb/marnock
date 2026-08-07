package com.marnock.app.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    val scheme = MaterialTheme.colorScheme
    val frameColor = scheme.primary
    val scrimColor = scheme.scrim

    val pulse = rememberInfiniteTransition(label = "scanPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.inverseSurface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                        if (handled.get()) {
                            image.close()
                            return@setAnalyzer
                        }
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val source = PlanarYUVLuminanceSource(
                            bytes, image.width, image.height,
                            0, 0, image.width, image.height, false
                        )
                        try {
                            val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
                            if (handled.compareAndSet(false, true)) {
                                onResult(result.text)
                            }
                        } catch (_: Exception) {
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = size.minDimension * 0.68f
            val left = (size.width - frame) / 2f
            val top = size.height * 0.28f
            val right = left + frame
            val bottom = top + frame

            drawRect(scrimColor, size = GeomSize(size.width, top))
            drawRect(scrimColor, topLeft = Offset(0f, bottom), size = GeomSize(size.width, size.height - bottom))
            drawRect(scrimColor, topLeft = Offset(0f, top), size = GeomSize(left, frame))
            drawRect(scrimColor, topLeft = Offset(right, top), size = GeomSize(size.width - right, frame))

            drawRoundRect(
                color = frameColor.copy(alpha = pulseAlpha),
                topLeft = Offset(left, top),
                size = GeomSize(frame, frame),
                cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Marnock",
                style = MaterialTheme.typography.titleLarge,
                color = scheme.inverseOnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Align the Mac pairing QR in the frame",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.inverseOnSurface.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }
}
