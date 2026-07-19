package com.phonehub

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button

fun Button.applyDarkTheme(
    bgColor: Int = Color.parseColor("#2d2d2d"),
    textColor: Int = Color.WHITE,
    primary: Boolean = false
) {
    val bg = if (primary) Color.parseColor("#0078d4") else bgColor
    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(2f)
        setColor(bg)
        setStroke(dpToPx(1f).toInt(), Color.parseColor("#404040"))
    }
    val rippleColor = ColorStateList.valueOf(Color.parseColor("#40ffffff"))
    background = RippleDrawable(rippleColor, drawable, null)
    setTextColor(textColor)
    setTextSize(2, 13f)
    setPadding(dpToPx(16f).toInt(), dpToPx(6f).toInt(), dpToPx(16f).toInt(), dpToPx(6f).toInt())
    minimumHeight = dpToPx(32f).toInt()
    isAllCaps = false
    includeFontPadding = false
}

fun dpToPx(dp: Float): Float {
    return App.context.resources.displayMetrics.density * dp
}
