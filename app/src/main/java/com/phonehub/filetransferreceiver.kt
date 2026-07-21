package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 文件传输通知的按钮点击接收器
 *
 * 处理通知中的三种操作：
 *  - 开始下载（用户点击"开始下载"按钮）
 *  - 取消下载（用户点击"取消"按钮）
 *
 * 通知主体点击通过 PendingIntent 走 MainActivity，不经过本接收器。
 */
class FileTransferReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val fileId = intent.getStringExtra(EXTRA_FILE_ID) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        Log.i("PhoneHub", "FileTransferReceiver: action=$action, fileId=$fileId, name=$fileName, size=$fileSize")

        when (action) {
            ACTION_START_DOWNLOAD -> {
                // 用户点击"开始下载"：调用 ConnectionManager 启动下载
                ConnectionManager.startFileDownloadFromNotification(fileId, fileName, fileSize)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                // 用户点击"取消"：取消传输并移除通知
                ConnectionManager.cancelTransfer()
                ConnectionManager.cancelFileTransferNotification()
            }
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.phonehub.action.START_FILE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.phonehub.action.CANCEL_FILE_DOWNLOAD"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_SIZE = "file_size"
    }
}
