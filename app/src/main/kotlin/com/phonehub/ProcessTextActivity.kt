package com.phonehub

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.text.StringsKt

class ProcessTextActivity : Activity {
    override
    fun onCreate(savedInstanceState: Bundle): Unit {
        super.onCreate(savedInstanceState)
        val stringExtra: String = getIntent().getStringExtra("android.intent.extra.PROCESS_TEXT")
        val text: String = stringExtra != null ? StringsKt.trim((CharSequence) stringExtra).toString() : null
        val str: String = text
        if (!(str == null || str.length() == 0)) {
            ConnectionManager.INSTANCE.sendClipboard(text)
            Toast.makeText(this, "已同步复制到电脑", 0).show()
            }
        finish()
        }
    }
