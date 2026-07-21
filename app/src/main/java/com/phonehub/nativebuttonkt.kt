package com.phonehub

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.widget.Button

/**
 * Button 深色主题样式扩展函数
 * - primary = true: 蓝色实心按钮
 * - primary = false: 深色描边按钮
 */
fun Button.applyDarkTheme(primary: Boolean = false) {
    try {
        val bg = GradientDrawable()
        if (primary) {
            bg.setColor(Color.parseColor("#FF0078D4"))
            bg.cornerRadius = 8f.dp
            bg.setStroke(0, Color.TRANSPARENT)
        } else {
            bg.setColor(Color.parseColor("#FF2D2D2D"))
            bg.cornerRadius = 8f.dp
            bg.setStroke(1, Color.parseColor("#FF3A3A3A"))
        }

        val rippleColor = if (primary) Color.parseColor("#40FFFFFF") else Color.parseColor("#30FFFFFF")
        val rippleDrawable = RippleDrawable(
            android.content.res.ColorStateList.valueOf(rippleColor),
            bg,
            null
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            background = rippleDrawable
        } else {
            setBackgroundDrawable(rippleDrawable)
        }

        setTextColor(Color.WHITE)
    } catch (e: Exception) {
        // 忽略样式失败
    }
}

private val Float.dp: Float
    get() = this * android.content.res.Resources.getSystem().displayMetrics.density