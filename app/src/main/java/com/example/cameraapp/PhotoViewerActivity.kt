package com.example.cameraapp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

class PhotoViewerActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        val verified = intent.getBooleanExtra("verified", false)
        val stamp = intent.getStringExtra("stamp") ?: ""
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        val image = ZoomImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeFile(path))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        val badge = TextView(this).apply {
            text = if (verified) "✓ ORIGINAL VERIFIED
$stamp" else "⚠ MODIFIED / NOT ORIGINAL
$stamp"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(18, 16, 18, 16)
            gravity = Gravity.CENTER
            setBackgroundColor(if (verified) 0x990D5B18.toInt() else 0x99A00000.toInt())
        }
        root.addView(badge, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
    }
}

class ZoomImageView(context: android.content.Context) : ImageView(context) {
    private var scaleFactor = 1f
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            scaleFactor = (scaleFactor * d.scaleFactor).coerceIn(1f, 6f)
            scaleX = scaleFactor
            scaleY = scaleFactor
            if (scaleFactor == 1f) {
                translationX = 0f
                translationY = 0f
            }
            return true
        }
    })
    private var downX = 0f
    private var downY = 0f
    private var baseTranslationX = 0f
    private var baseTranslationY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                baseTranslationX = translationX
                baseTranslationY = translationY
            }
            MotionEvent.ACTION_MOVE -> if (scaleFactor > 1f && event.pointerCount == 1) {
                translationX = baseTranslationX + (event.x - downX)
                translationY = baseTranslationY + (event.y - downY)
            }
        }
        return true
    }
}
