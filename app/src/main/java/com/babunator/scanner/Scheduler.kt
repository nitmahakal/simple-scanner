package com.babunator.scanner

import android.content.Context
import androidx.work.*
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

object Scheduler {
    private const val PREF = "schedule"
    private const val UNIQUE = "daily-update-starter"
    fun schedule(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("h", hour).putInt("m", minute).apply()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<DailyStarterWorker>().setInitialDelay(Duration.between(now, next)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, req)
    }
    fun rescheduleNext(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.contains("h")) return
        schedule(context, sp.getInt("h", 16), sp.getInt("m", 30))
    }
}

class DailyStarterWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        WorkChain.enqueue(applicationContext)
        Scheduler.rescheduleNext(applicationContext)
        return Result.success()
    }
}

object WorkChain {
    fun enqueue(context: Context) {
        val db = AppDb(context)
        val symbols = try { DataProvider().fetchSymbolList() } catch (_: Exception) { db.symbols() }
        val wm = WorkManager.getInstance(context)
        var chain = wm.beginUniqueWork("update-scan-run", ExistingWorkPolicy.REPLACE, updateRequest(0))
        var offset = 25
        while (offset < symbols.size) { chain = chain.then(updateRequest(offset)); offset += 25 }
        chain.then(OneTimeWorkRequestBuilder<ScanWorker>().build()).enqueue()
    }
    private fun updateRequest(offset: Int) = OneTimeWorkRequestBuilder<UpdateWorker>()
        .setInputData(Data.Builder().putInt("offset", offset).putInt("limit", 25).build())
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
