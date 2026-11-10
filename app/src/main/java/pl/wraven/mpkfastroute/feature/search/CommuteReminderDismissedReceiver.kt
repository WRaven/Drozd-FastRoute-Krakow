package pl.wraven.mpkfastroute.feature.search

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class CommuteReminderDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val destinationName = intent.getStringExtra(CommuteReminderScheduler.DESTINATION_KEY)
        val destination = SavedDestination.values().firstOrNull { it.name == destinationName }
            ?: return
        val arrivalTarget = intent.getStringExtra(CommuteReminderScheduler.ARRIVAL_TARGET_KEY)
            ?: return
        val workName = intent.getStringExtra(CommuteReminderScheduler.OCCURRENCE_KEY)
            ?: return

        context.getSharedPreferences(
            CommuteReminderScheduler.DISMISSED_OCCURRENCES_PREFERENCES,
            Context.MODE_PRIVATE
        ).edit()
            .putString("dismissed_${destination.name}", arrivalTarget)
            .apply()
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }
}
