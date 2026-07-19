package com.phonehub

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout

class NativeButtonKt {
    public static  Unit applyDarkTheme$default(Button button, int i, int i2, boolean z, int i3, Object obj) {
        if ((i3 & 1) != 0) {
            i = Color.parseColor("#2d2d2d")
            }
        if ((i3 & 2) != 0) {
            i2 = -1
            }
        if ((i3 & 4) != 0) {
            z = false
            }
        applyDarkTheme(button, i, i2, z)
        }

    fun applyDarkTheme(/* Button $this$applyDarkTheme */, bgColor: Int, textColor: Int, primary: Boolean): Unit {
        Intrinsics.checkNotNullParameter($this$applyDarkTheme, "<this>")
        val bg: Int = primary ? Color.parseColor("#0078d4") : bgColor
        GradientDrawable $this$applyDarkTheme_u24lambda_u240 = GradientDrawable()
        $this$applyDarkTheme_u24lambda_u240.setShape(0)
        $this$applyDarkTheme_u24lambda_u240.setCornerRadius(dpToPx(2.0f))
        $this$applyDarkTheme_u24lambda_u240.setColor(bg)
        $this$applyDarkTheme_u24lambda_u240.setStroke(dpToPx(1.0f), Color.parseColor("#404040"))
        val rippleColor: ColorStateList = ColorStateList.valueOf(Color.parseColor("#40ffffff"))
        Intrinsics.checkNotNullExpressionValue(rippleColor, "valueOf(...)")
        $this$applyDarkTheme.setBackground(RippleDrawable(rippleColor, $this$applyDarkTheme_u24lambda_u240, null))
        $this$applyDarkTheme.setTextColor(textColor)
        $this$applyDarkTheme.setTextSize(2, 13.0f)
        $this$applyDarkTheme.setPadding(dpToPx(16.0f), dpToPx(6.0f), dpToPx(16.0f), dpToPx(6.0f))
        $this$applyDarkTheme.setMinimumHeight(dpToPx(32.0f))
        $this$applyDarkTheme.setAllCaps(false)
        $this$applyDarkTheme.setIncludeFontPadding(false)
        }

    fun dpToPx(dp: Float): Float {
        return App.INSTANCE.getContext().getResources().getDisplayMetrics().density * dp
        }
    }
