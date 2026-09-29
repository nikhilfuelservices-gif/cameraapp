package com.example.cameraapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.concurrent.Executors

class GalleryActivity : ComponentActivity() {
    private lateinit var store: SecurityStore
    private lateinit var grid: GridLayout
    private lateinit var countText: TextView
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = SecurityStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0D10.toInt())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 12)
            setBackgroundColor(0xFF12161B.toInt())
        }

        val title = TextView(this).apply {
            text = "Photo Gallery"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
        }
        countText = TextView(this).apply {
            text = "Checking local photos…"
            setTextColor(0xFF9AA4AF.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 0)
        }
        header.addView(title)
        header.addView(countText)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        grid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(10, 10, 10, 20)
            useDefaultMargins = false
        }
        scroll.addView(grid, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        loadGallery()
    }

    private fun loadGallery() {
        countText.text = "Checking local photos…"
        executor.execute {
            val items = try { store.listGalleryItems() } catch (_: Exception) { emptyList() }
            runOnUiThread { render(items) }
        }
    }

    private fun render(items: List<GalleryItem>) {
        grid.removeAllViews()
        val authentic = items.count { it.verifiedPhoto.authentic }
        countText.text = items.size.toString() + " photo(s)  •  " + authentic + " authenticated"

        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No captured photos yet"
                setTextColor(0xFFB5BEC8.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(20, 80, 20, 80)
            }
            grid.addView(empty, GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(0, 2)
                width = -1
                height = -2
            })
            return
        }

        items.forEach { item ->
            val entry = item.verifiedPhoto
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(5, 5, 5, 12)
            }

            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF20252B.toInt())
                if (entry.file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    setImageBitmap(BitmapFactory.decodeFile(entry.file.absolutePath, opts))
                }
                setOnClickListener {
                    startActivity(Intent(this@GalleryActivity, PhotoViewerActivity::class.java).apply {
                        putExtra("path", entry.file.absolutePath)
                        putExtra("verified", entry.authentic)
                        putExtra("stamp", if (item.isOrphan) "UNVERIFIED LOCAL PHOTO" else entry.record.timestampText)
                    })
                }
            }

            val status = TextView(this).apply {
                text = when {
                    entry.authentic -> "✓  AUTHENTIC"
                    item.isOrphan -> "⚠  UNVERIFIED LOCAL"
                    else -> "⚠  MODIFIED"
                }
                setTextColor(if (entry.authentic) 0xFF69E58A.toInt() else 0xFFFF9A8C.toInt())
                textSize = 12f
                setPadding(8, 8, 8, 2)
            }

            val stamp = TextView(this).apply {
                text = if (item.isOrphan) "Record missing" else entry.record.timestampText
                setTextColor(0xFF8E98A3.toInt())
                textSize = 11f
                setPadding(8, 0, 8, 4)
            }

            card.addView(image, LinearLayout.LayoutParams(-1, 230))
            card.addView(status)
            card.addView(stamp)

            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = -2
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) loadGallery()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}