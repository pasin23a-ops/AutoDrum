package com.example.autodrum

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private var screenW = 0
    private var screenH = 0
    private var density = 0

    private val laneCoords = Array(Prefs.LANE_COUNT) { Pair(0f, 0f) }
    private val baseline = arrayOfNulls<IntArray>(Prefs.LANE_COUNT)
    private val lastTapTime = LongArray(Prefs.LANE_COUNT)
    private var framesSeen = 0
    private val calibrationFrames = 10
    private val colorDiffThreshold = 70.0
    private val cooldownMs = 130L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("AutoDrumCapture").apply { start() }
        handler = Handler(handlerThread.looper)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        for (i in 0 until Prefs.LANE_COUNT) {
            laneCoords[i] = Prefs.getLane(prefs, i) ?: Pair(-1f, -1f)
        }

        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        density = metrics.densityDpi

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AutoDrumCapture", screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) {
                try {
                    processFrame(image)
                } catch (e: Exception) {
                } finally {
                    image.close()
                }
            }
        }, handler)

        return START_STICKY
    }

    private fun processFrame(image: android.media.Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        framesSeen++

        for (i in 0 until Prefs.LANE_COUNT) {
            val lx = laneCoords[i].first
            val ly = laneCoords[i].second
            if (lx < 0 || ly < 0) continue
            val x = lx.toInt().coerceIn(0, screenW - 1)
            val y = ly.toInt().coerceIn(0, screenH - 1)
            val offset = y * rowStride + x * pixelStride
            if (offset + 2 >= buffer.capacity()) continue
            val r = buffer.get(offset).toInt() and 0xFF
            val g = buffer.get(offset + 1).toInt() and 0xFF
            val b = buffer.get(offset + 2).toInt() and 0xFF

            if (framesSeen <= calibrationFrames) {
                baseline[i] = intArrayOf(r, g, b)
                continue
            }

            val base = baseline[i] ?: continue
            val diff = colorDistance(r, g, b, base[0], base[1], base[2])

            val now = System.currentTimeMillis()
            if (diff > colorDiffThreshold && now - lastTapTime[i] > cooldownMs) {
                lastTapTime[i] = now
                ColorTapAccessibilityService.instance?.tap(lx, ly)
            } else if (diff < colorDiffThreshold / 2) {
                baseline[i] = intArrayOf(
                    (base[0] * 9 + r) / 10,
                    (base[1] * 9 + g) / 10,
                    (base[2] * 9 + b) / 10
                )
            }
        }
    }

    private fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val dr = (r1 - r2).toDouble()
        val dg = (g1 - g2).toDouble()
        val db = (b1 - b2).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoDrum กำลังทำงาน")
            .setContentText("บอทกำลังจับสีและแตะอัตโนมัติ")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "AutoDrum", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        mediaProjection?.stop()
        handlerThread.quitSafely()
    }

    companion object {
        const val CHANNEL_ID = "autodrum_channel"
        const val NOTIF_ID = 1
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
