package gov.disastraaa.companion.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import gov.disastraaa.companion.DisastraaaApplication
import gov.disastraaa.companion.utils.Constants
import java.util.concurrent.TimeUnit

class SyncLocationsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DisastraaaApplication

        // User & session isolation: do not attempt to sync if user is logged out
        if (!app.tokenManager.isLoggedIn()) {
            return Result.failure()
        }

        // Bounded retry policy: prevent infinite work execution loops
        if (runAttemptCount >= Constants.MAX_SYNC_RETRY_COUNT) {
            return Result.failure()
        }

        val repository = app.locationRepository
        val syncResult = repository.syncQueuedLocations()

        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            val exception = syncResult.exceptionOrNull()
            if (exception is IllegalStateException) {
                // Permanent auth or precondition failure: do not retry
                Result.failure()
            } else {
                // Retryable network or 5xx server issue: back off and retry
                Result.retry()
            }
        }
    }

    companion object {
        fun enqueuePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<SyncLocationsWorker>(
                    15, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    Constants.UNIQUE_PERIODIC_SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            } catch (_: Exception) {}
        }

        fun enqueueOneTimeSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeRequest = OneTimeWorkRequestBuilder<SyncLocationsWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    Constants.UNIQUE_SYNC_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    oneTimeRequest
                )
            } catch (_: Exception) {}
        }

        fun cancelSyncWork(context: Context) {
            try {
                val workManager = WorkManager.getInstance(context)
                workManager.cancelUniqueWork(Constants.UNIQUE_SYNC_WORK_NAME)
                workManager.cancelUniqueWork(Constants.UNIQUE_PERIODIC_SYNC_WORK_NAME)
            } catch (_: Exception) {}
        }
    }
}
