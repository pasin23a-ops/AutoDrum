package com.example.autodrum

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private val screenCaptureLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val intent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                tvStatus.text = "สถานะ: บอททำงานอยู่"
            } else {
                Toast.makeText(this, "ไม่ได้รับสิทธิ์บันทึกหน้าจอ", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "มีสิทธิ์ Overlay แล้ว", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnCalibrate).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "กรุณาเปิดสิทธิ์ Overlay ก่อน (ขั้นตอนที่ 2)", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, CalibrationOverlayService::class.java))
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "กรุณาเปิดสิทธิ์ Accessibility ก่อน (ขั้นตอนที่ 1)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(Prefs.CALIBRATED, false)) {
                Toast.makeText(this, "กรุณาตั้งค่าตำแหน่งเลนก่อน (ขั้นตอนที่ 3)", Toast.LENGTH_SHORT).show()
