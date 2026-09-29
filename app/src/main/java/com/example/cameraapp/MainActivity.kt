package com.example.cameraapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var timeText: TextView
    private lateinit var captureButton: Button
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val timeExecutor = Executors.newSingleThreadScheduledExecutor()
    private val timeAuthority = TimeAuthority()
    private lateinit var store: SecurityStore
    private val timeFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss 'IST'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    private var syncInProgress = false
    private var lastSyncOk = false
    private var zoom = 1f

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = findViewById(R.id.preview)
        timeText = findViewById(R.id.timeText)
        captureButton = findViewById(R.id.capture)
        store = SecurityStore(this)

        findViewById<Button>(R.id.flip).setOnClickListener {
            lens = if (lens == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
                   else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        findViewById<Button>(R.id.gallery).setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        captureButton.setOnClickListener { capture() }

        val detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val c = camera ?: return true
                val state = c.cameraInfo.zoomState.value ?: return true
                zoom = (zoom * d.scaleFactor).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                c.cameraControl.setZoomRatio(zoom)
                return true
            }
        })
        preview.setOnTouchListener { _, event -> detector.onTouchEvent(event); true }

        captureButton.isEnabled = false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        } else startCamera()

        updateClock()
        syncTimeAsync()
        timeExecutor.scheduleAtFixedRate({ syncTimeAsync() }, 2, 2, TimeUnit.MINUTES)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 10 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener({
                try {
                    val provider = future.get()
                    val selector = CameraSelector.Builder().requireLensFacing(lens).build()
                    val previewUseCase = Preview.Builder().build().also {
                        it.setSurfaceProvider(preview.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setJpegQuality(92)
                        .build()
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(this, selector, previewUseCase, capture)
                    imageCapture = capture
                    zoom = 1f
                } catch (_: Exception) {
                    runOnUiThread { timeText.text = "CAMERA ERROR" }
                }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    private fun syncTimeAsync() {
        if (syncInProgress) return
        syncInProgress = true
        timeExecutor.execute {
            lastSyncOk = timeAuthority.sync()
            syncInProgress = false
            runOnUiThread { updateClock() }
        }
    }

    private fun updateClock() {
        val snapshot = timeAuthority.snapshot()
        if (snapshot == null) {
            timeText.text = "INTERNET TIME: SYNCING…"
            captureButton.isEnabled = false
        } else {
            timeText.text = "VERIFIED • " + timeFormat.format(Date(snapshot.epochMs))
            captureButton.isEnabled = imageCapture != null && lastSyncOk
        }
        timeText.postDelayed({ updateClock() }, 1000)
    }

    private fun capture() {
        val capture = imageCapture ?: return
        val epoch = timeAuthority.snapshot()?.epochMs ?: return
        captureButton.isEnabled = false
        val temp = File.createTempFile("capture_", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(temp).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val bitmap = BitmapFactory.decodeFile(temp.absolutePath)
                        ?: throw IllegalStateException("Unable to decode captured image")
                    val timestamp = timeFormat.format(Date(epoch))
                    val canvas = Canvas(bitmap)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = max(28f, bitmap.width / 42f)
                        setShadowLayer(6f, 2f, 2f, Color.BLACK)
                    }
                    canvas.drawText(timestamp, 28f, bitmap.height - 36f, paint)

                    val sequence = store.nextSequence()
                    val finalFile = File(store.photoDir(), "CAM_%06d.jpg".format(sequence))
                    finalFile.outputStream().use { out ->
                        if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)) {
                            throw IllegalStateException("JPEG encode failed")
                        }
                    }
                    bitmap.recycle()
                    temp.delete()
                    store.createRecord(finalFile, epoch, timestamp)
                } catch (_: Exception) {
                    temp.delete()
                } finally {
                    runOnUiThread { updateClock() }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                temp.delete()
                runOnUiThread { updateClock() }
            }
        })
    }

    override fun onDestroy() {
        timeExecutor.shutdownNow()
        cameraExecutor.shutdownNow()
        super.onDestroy()
    }
}
