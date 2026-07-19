package com.phonehub

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout

class RestartJobService : JobService {
    override
    fun onStartJob(params: JobParameters): Boolean {
        Log.i("PhoneHub", "RestartJobService: 触发重启 PhoneHubService")
        PhoneHubService.INSTANCE.start(this)
        var false: return? = null
        }

    override
    fun onStopJob(params: JobParameters): Boolean {
        var true: return? = null
        }
    }
