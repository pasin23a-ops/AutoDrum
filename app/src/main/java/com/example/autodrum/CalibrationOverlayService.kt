package com.example.autodrum

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class CalibrationOverlayService : Service() {

    private lateinit var wm: WindowManager
    private val markerViews = mutableListOf<View>()
    private var saveButton: Button? = null
    private var label: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        label = TextView(this)
        label?.text = "ลากวงกลมสีไปวางบนตำแหน่งเลนกลองแต่ละอัน แล้วกด บันทึก"
        label?.setBackgroundColor(Color.parseColor("#CC000000"))
        label?.setTextColor(Color.WHITE)
        label?.setPadding(16, 16, 16, 16)

        val labelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        labelParams.gravity = Gravity.TOP or Gravity.START
        labelParams.x = 20
        labelParams.y = 60
        wm.addView(label, labelParams)

        val colors = intArrayOf(
            Color.parseColor("#FF5252"),
            Color.parseColor("#FFD740"),
            Color.parseColor("#40C4FF"),
            Color.parseColor("#69F0AE")
        )

        for (i in 0 until 4) {
            val defaultX = ((screenW / 5) * (i + 1)).toFloat()
            val defaultY = (screenH * 0.8f)
            val saved = Prefs.getLane(prefs, i)
            val startX = saved?.first ?: defaultX
            val startY = saved?.second ?: defaultY

            val marker = View(this)
            marker.setBackgroundColor(colors[i])

            val size = 90
            val params = WindowManager.LayoutParams(
                size, size, overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = (startX - size / 2).toInt()
            params.y = (startY - size / 2).toInt()

            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            marker.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        wm.updateViewLayout(v, params)
                        true
                    }
                    else -> false
                }
            }

            wm.addView(marker, params)
            markerViews.add(marker)
        }

        saveButton = Button(this)
        saveButton?.text = "บันทึกตำแหน่ง"
        saveButton?.setOnClickListener {
            val editor = prefs.edit()
            for (i in 0 until 4) {
                val lp = markerViews[i].layoutParams as WindowManager.LayoutParams
                val px = lp.x + 45f
                val py = lp.y + 45f
                Prefs.setLane(editor, i, px, py)
            }
            editor.putBoolean(Prefs.CALIBRATED, true)
            editor.apply()
            stopSelf()
        }

        val btnParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        btnParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        btnParams.y = 100
        wm.addView(saveButton, btnParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        for (v in markerViews) {
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
        try {
            if (saveButton != null) wm.removeView(saveButton)
            if (label != null) wm.removeView(label)
        } catch (e: Exception) {
        }
    }
}
