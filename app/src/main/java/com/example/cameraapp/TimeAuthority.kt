package com.example.cameraapp

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class TimeAuthority {
    data class Snapshot(val epochMs: Long, val ageMs: Long)
    private data class Sample(val offsetMs: Long)

    @Volatile private var trustedOffsetMs: Long? = null
    @Volatile private var syncedAtMono: Long = 0L

    fun sync(): Boolean {
        val sources = listOf(
            "https://www.google.com/generate_204",
            "https://www.cloudflare.com/",
            "https://www.microsoft.com/",
            "https://www.apple.com/library/test/success.html"
        )
        val samples = mutableListOf<Sample>()
        for (source in sources) {
            try {
                val start = SystemClock.elapsedRealtime()
                val c = (URL(source).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    instanceFollowRedirects = true
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("User-Agent", "CameraApp/1.1")
                    useCaches = false
                }
                c.connect()
                val header = c.getHeaderField("Date")
                c.disconnect()
                val serverEpoch = header?.let { parseHttpDate(it) } ?: continue
                if (serverEpoch <= 0L) continue
                val end = SystemClock.elapsedRealtime()
                val midpoint = start + ((end - start) / 2L)
                samples += Sample(serverEpoch - midpoint)
            } catch (_: Exception) { }
        }
        if (samples.size < 2) return false
        val sorted = samples.map { it.offsetMs }.sorted()
        val median = sorted[sorted.size / 2]
        val agreeing = sorted.filter { abs(it - median) <= 10_000L }
        if (agreeing.size < 2) return false
        trustedOffsetMs = agreeing[agreeing.size / 2]
        syncedAtMono = SystemClock.elapsedRealtime()
        return true
    }

    fun snapshot(maxAgeMs: Long = TimeUnit.MINUTES.toMillis(10)): Snapshot? {
        val offset = trustedOffsetMs ?: return null
        val nowMono = SystemClock.elapsedRealtime()
        val age = nowMono - syncedAtMono
        if (age < 0 || age > maxAgeMs) return null
        return Snapshot(nowMono + offset, age)
    }
}
