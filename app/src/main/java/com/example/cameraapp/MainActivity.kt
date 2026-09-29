package com.example.cameraapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var stamp: TextView
    private lateinit var imageCapture: ImageCapture
    private var lens = CameraSelector.LENS_FACING_BACK
    private var trustedBase = 0L
    private var monoBase = 0L
    private val executor = Executors.newSingleThreadExecutor()
    private val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm:ss 'IST'", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.preview); stamp = findViewById(R.id.timeText)
        findViewById<Button>(R.id.flip).setOnClickListener { lens = if(lens==CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; startCamera() }
        findViewById<Button>(R.id.capture).setOnClickListener { capture() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),7) else startCamera()
        syncTime()
        preview.setOnTouchListener { _, _ -> false }
    }

    private fun syncTime() {
        executor.execute {
            var best = 0L
            listOf("https://www.google.com","https://www.cloudflare.com","https://www.microsoft.com").forEach { u ->
                try {
                    val c=URL(u).openConnection() as HttpURLConnection; c.connectTimeout=2500;c.readTimeout=2500; c.requestMethod="HEAD"; c.connect()
                    val t=c.date; if(t>best) best=t; c.disconnect()
                } catch(_:Exception){}
            }
            if(best>0){ trustedBase=best; monoBase=SystemClock.elapsedRealtime() }
            runOnUiThread { updateClock() }
        }
    }

    private fun trustedNow(): Long = if(trustedBase>0) trustedBase + (SystemClock.elapsedRealtime()-monoBase) else 0L

    private fun updateClock() {
        val t=trustedNow()
        stamp.text=if(t>0) fmt.format(Date(t)) else "INTERNET TIME SYNCING..."
        stamp.postDelayed({updateClock()},1000)
    }

    private fun startCamera() {
        val f=ProcessCameraProvider.getInstance(this)
        f.addListener({
            val p=f.get()
            val selector=CameraSelector.Builder().requireLensFacing(lens).build()
            imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            p.unbindAll(); p.bindToLifecycle(this,selector,Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)},imageCapture)
        },ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        if(trustedNow()<=0) return
        val dir=File(filesDir,"captures").apply{mkdirs()}
        val name="CAM_"+SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date(trustedNow()))+".jpg"
        val out=File(dir,name)
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),object:ImageCapture.OnImageCapturedCallback(){
            override fun onCaptureSuccess(image:ImageProxy){
                val bmp=image.toBitmap()
                val canvas=Canvas(bmp); val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=max(24f,bmp.width/45f);setShadowLayer(5f,2f,2f,Color.BLACK)}
                canvas.drawText(fmt.format(Date(trustedNow())),30f,bmp.height-35f,p)
                FileOutputStream(out).use{bmp.compress(Bitmap.CompressFormat.JPEG,95,it)}
                image.close()
            }
        })
    }
}
