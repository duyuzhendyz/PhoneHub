package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 文件传输通知的按钮点击接收器
 *
 * 处理通知中的操作：
 *  - 开始下载（用户点击"开始下载"按钮）
 *  - 取消下载（用户点击"取消"按钮）
 *  - 文件冲突处理（S3）：覆盖原有文件、添加编号接收、取消接收
 *
 * 通知主体点击通过 PendingIntent 走 MainActivity，不经过本接收器。
 */
class FileTransferReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val fileId = intent.getStringExtra(EXTRA_FILE_ID) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: ""
        val fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        val existingFile = intent.getStringExtra("existing_file") ?: ""
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
            // S3: 文件冲突处理
            ACTION_CONFLICT_OVERWRITE -> {
                ConnectionManager.sendConflictResponse(fileId, "overwrite", "")
                ConnectionManager.cancelFileConflictNotification(fileId)
            }
            ACTION_CONFLICT_RENAME -> {
                // 需要生成编号文件名
                val newFileName = ConnectionManager.generateNumberedFileName(fileName, existingFile)
                ConnectionManager.sendConflictResponse(fileId, "rename", newFileName)
                ConnectionManager.cancelFileConflictNotification(fileId)
            }
            ACTION_CONFLICT_SKIP -> {
                ConnectionManager.sendConflictResponse(fileId, "skip", "")
                ConnectionManager.cancelFileConflictNotification(fileId)
            }
        }
    }

    companion object {
        const val ACTION_START_DOWNLOAD = "com.phonehub.action.START_FILE_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.phonehub.action.CANCEL_FILE_DOWNLOAD"
        // S3: 文件冲突处理
        const val ACTION_CONFLICT_OVERWRITE = "com.phonehub.action.CONFLICT_OVERWRITE"
        const val ACTION_CONFLICT_RENAME = "com.phonehub.action.CONFLICT_RENAME"
        const val ACTION_CONFLICT_SKIP = "com.phonehub.action.CONFLICT_SKIP"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_FILE_SIZE = "file_size"
    }
}
