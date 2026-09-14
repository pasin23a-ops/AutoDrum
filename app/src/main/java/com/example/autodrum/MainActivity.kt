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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
            intent.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "สถานะ บอททำงานอยู่"
        } else {
            Toast.makeText(this, "ไม่ได้รับสิทธิบันทึกหน้าจอ", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val btnOverlayPermission = findViewById<Button>(R.id.btnOverlayPermission)
        btnOverlayPermission.setOnClickListener {
            val hasOverlay = Settings.canDrawOverlays(this)
            if (hasOverlay == false) {
                val uri = Uri.parse("package:" + packageName)
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                startActivity(intent)
            } else {
                Toast.makeText(this, "มีสิทธิ Overlay แล้ว", Toast.LENGTH_SHORT).show()
            }
        }

        val btnCalibrate = findViewById<Button>(R.id.btnCalibrate)
        btnCalibrate.setOnClickListener {
            val hasOverlay = Settings.canDrawOverlays(this)
            if (hasOverlay == false) {
                Toast.makeText(this, "กรุณาเปิดสิทธิ Overlay ก่อน", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, CalibrationOverlayService::class.java))
            }
        }

        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            val accessibilityOn = checkAccessibilityEnabled()
            if (accessibilityOn == false) {
                Toast.makeText(this, "กรุณาเปิดสิทธิ Accessibility ก่อน", Toast.LENGTH_SHORT).show()
            } else {
                val prefs = getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
                val calibrated = prefs.getBoolean(Prefs.CALIBRATED, false)
                if (calibrated == false) {
                    Toast.makeText(this, "กรุณาตั้งค่าตำแหน่งเลนก่อน", Toast.LENGTH_SHORT).show()
                } else {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                }
            }
        }

        val btnStop = findViewById<Button>(R.id.btnStop)
        btnStop.setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            tvStatus.text = "สถานะ หยุดแล้ว"
        }
    }

    private fun checkAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, ColorTapAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabled == null) {
            return false
        }
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val next = splitter.next()
            if (next.equals(expected, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
