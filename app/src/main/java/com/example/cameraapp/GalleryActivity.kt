package com.example.cameraapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class GalleryActivity : ComponentActivity() {
    private lateinit var store: SecurityStore
    private lateinit var grid: GridLayout

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = SecurityStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        val header = TextView(this).apply {
            text = "AUTHENTICATED GALLERY"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 22, 24, 18)
        }
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = true }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        grid.removeAllViews()
        val entries = store.listVerified()
        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No captured photos"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
            }
            grid.addView(empty, GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(0, 3)
                width = -1
                height = -1
            })
            return
        }
        entries.forEach { entry ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (entry.file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    setImageBitmap(BitmapFactory.decodeFile(entry.file.absolutePath, opts))
                }
                setBackgroundColor(if (entry.authentic) 0xFF123D16.toInt() else 0xFF551010.toInt())
                setOnClickListener {
                    startActivity(Intent(this@GalleryActivity, PhotoViewerActivity::class.java).apply {
                        putExtra("path", entry.file.absolutePath)
                        putExtra("verified", entry.authentic)
                        putExtra("stamp", entry.record.timestampText)
                    })
                }
            }
            box.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
            val label = TextView(this).apply {
                text = if (entry.authentic) "✓ AUTHENTIC" else "⚠ MODIFIED"
                setTextColor(if (entry.authentic) 0xFF9DFFAA.toInt() else 0xFFFF8A8A.toInt())
                gravity = Gravity.CENTER
                textSize = 11f
                setPadding(2, 8, 2, 8)
            }
            box.addView(label, LinearLayout.LayoutParams(-1, -2))
            grid.addView(box, GridLayout.LayoutParams().apply {
                width = 0
                height = 320
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 12)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) render()
    }
}
