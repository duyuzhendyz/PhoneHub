package com.phonehub

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * JobScheduler 重启服务：
 * 当 AlarmManager 被系统限制时（Doze 深度休眠），
 * JobScheduler 仍可在合适时机触发，作为保活的备用通道
 */
class RestartJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i("PhoneHub", "RestartJobService: 触发重启 PhoneHubService")
        PhoneHubService.start(this)
        return false  // 工作已完成
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true  // 重新调度
    }
}
