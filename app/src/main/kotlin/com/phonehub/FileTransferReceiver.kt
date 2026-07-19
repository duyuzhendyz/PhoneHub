package com.phonehub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout

class FileTransferReceiver : BroadcastReceiver {
    val ACTION_CANCEL_DOWNLOAD: public static final String = "com.phonehub.action.CANCEL_FILE_DOWNLOAD"
    val ACTION_START_DOWNLOAD: public static final String = "com.phonehub.action.START_FILE_DOWNLOAD"
    val EXTRA_FILE_ID: public static final String = "file_id"
    val EXTRA_FILE_NAME: public static final String = "file_name"
    val EXTRA_FILE_SIZE: public static final String = "file_size"

    override
    fun onReceive(context: Context, intent: Intent): Unit {
        var action: String? = null
        Intrinsics.checkNotNullParameter(context, "context")
        if (intent == null || (action = intent.getAction()) == null) {
            return
            }
        val fileId: String = intent.getStringExtra("file_id")
        if (fileId == null) {
            fileId = ""
            }
        val stringExtra: String = intent.getStringExtra(EXTRA_FILE_NAME)
        val fileName: String = stringExtra != null ? stringExtra : ""
        val fileSize: Long = intent.getLongExtra(EXTRA_FILE_SIZE, 0L)
        Log.i("PhoneHub", "FileTransferReceiver: action=" + action + ", fileId=" + fileId + ", name=" + fileName + ", size=" + fileSize)
        if (Intrinsics.areEqual(action, ACTION_START_DOWNLOAD)) {
            ConnectionManager.INSTANCE.startFileDownloadFromNotification(fileId, fileName, fileSize)
            } else if (Intrinsics.areEqual(action, ACTION_CANCEL_DOWNLOAD)) {
            ConnectionManager.INSTANCE.cancelTransfer()
            ConnectionManager.INSTANCE.cancelFileTransferNotification()
            }
        }
    }
