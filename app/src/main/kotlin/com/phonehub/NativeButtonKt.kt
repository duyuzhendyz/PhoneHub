@file:JvmName("NativeButtonKt")

package com.phonehub

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.widget.Button

fun Button.applyDarkTheme(
    bgColor: Int = Color.parseColor("#2d2d2d"),
    textColor: Int = -1,
    primary: Boolean = false
) {
    val bg = if (primary) Color.parseColor("#0078d4") else bgColor
    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(2.0f)
        setColor(bg)
        setStroke(dpToPx(1.0f).toInt(), Color.parseColor("#404040"))
    }
    val rippleColor = ColorStateList.valueOf(Color.parseColor("#40ffffff"))
    background = RippleDrawable(rippleColor, drawable, null)
    setTextColor(textColor)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.0f)
    setPadding(
        dpToPx(16.0f).toInt(), dpToPx(6.0f).toInt(),
        dpToPx(16.0f).toInt(), dpToPx(6.0f).toInt()
    )
    minimumHeight = dpToPx(32.0f).toInt()
    isAllCaps = false
    includeFontPadding = false
}

fun dpToPx(dp: Float): Float {
    return App.getContext().resources.displayMetrics.density * dp
}
