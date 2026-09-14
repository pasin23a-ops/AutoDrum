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
        if (intent == null) { stopSelf(); return START_NOT_STICKY }

        startForeground(
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        )

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode == -1 || data == null) { stopSelf(); return START_NOT_STICKY }

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
            val image = try { reader.acquireLatestImage() } catch (e: Exception) { null }
            if (image != null) {
                try {
                    processFrame(image)
                } catch (_:
