package com.phonehub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * 处理系统文本选择菜单中的「同步复制」操作。
 * 通过 ACTION_PROCESS_TEXT 注册，长按文字后的悬浮菜单会显示此选项。
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.trim()
        if (!text.isNullOrEmpty()) {
            ConnectionManager.sendClipboard(text)
            Toast.makeText(this, "已同步复制到电脑", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}