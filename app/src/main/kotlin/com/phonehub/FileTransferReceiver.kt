package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FileTransferReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CANCEL_DOWNLOAD: String = "com.phonehub.action.CANCEL_FILE_DOWNLOAD"
        const val ACTION_START_DOWNLOAD: String = "com.phonehub.action.START_FILE_DOWNLOAD"
        const val EXTRA_FILE_ID: String = "file_id"
        const val EXTRA_FILE_NAME: String = "file_name"
        const val EXTRA_FILE_SIZE: String = "file_size"
    }

    override fun onReceive(context: Context, intent: Intent?): Unit {
        if (intent == null) {
            return
        }
        val action: String? = intent.action
        if (action == null) {
            return
        }
        val fileId: String = intent.getStringExtra("file_id") ?: ""
        val stringExtra: String? = intent.getStringExtra(EXTRA_FILE_NAME)
        val fileName: String = stringExtra ?: ""
        val fileSize: Long = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        Log.i("PhoneHub", "FileTransferReceiver: action=" + action + ", fileId=" + fileId + ", name=" + fileName + ", size=" + fileSize)
        if (action == ACTION_START_DOWNLOAD) {
            ConnectionManager.startFileDownloadFromNotification(fileId, fileName, fileSize)
        } else if (action == ACTION_CANCEL_DOWNLOAD) {
            ConnectionManager.cancelTransfer()
            ConnectionManager.cancelFileTransferNotification()
        }
    }
}
