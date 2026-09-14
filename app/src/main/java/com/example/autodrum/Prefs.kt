package com.example.autodrum

import android.content.SharedPreferences

object Prefs {
    const val NAME = "autodrum_prefs"
    const val CALIBRATED = "calibrated"
    const val LANE_COUNT = 4

    fun getLane(prefs: SharedPreferences, index: Int): Pair<Float, Float>? {
        val x = prefs.getFloat("lane_${index}_x", -1f)
        val y = prefs.getFloat("lane_${index}_y", -1f)
        return if (x < 0 || y < 0) null else Pair(x, y)
    }

    fun setLane(editor: SharedPreferences.Editor, index: Int, x: Float, y: Float) {
        editor.putFloat("lane_${index}_x", x)
        editor.putFloat("lane_${index}_y", y)
    }
}
