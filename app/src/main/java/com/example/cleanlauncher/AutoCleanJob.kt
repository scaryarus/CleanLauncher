package com.example.cleanlauncher

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

class AutoCleanJob : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        fun schedule(c: Context) {
            val js = c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val job = JobInfo.Builder(1, ComponentName(c, AutoCleanJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(job)
        }

        fun cancel(c: Context) {
            (c.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(1)
        }
    }
}
