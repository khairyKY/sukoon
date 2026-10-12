package com.sukoon.app.sharing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sukoon.app.MainActivity
import com.sukoon.app.R
import com.sukoon.app.SukoonApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Reema now follows you": every 15 minutes, who follows this account, and a notification for anyone
 * new, with Remove on it (unless the parent lock guards followers: then it opens Sukoon). The first
 * check on an account only remembers who is there.
 */
class NewFollowers(private val context: Context, private val sharing: Sharing, private val lockedFollowers: () -> Boolean) {
    private val prefs = context.getSharedPreferences("sukoon_prefs", Context.MODE_PRIVATE)

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                runCatching { check() }
                delay(CHECK_EVERY_MS)
            }
        }
    }

    suspend fun check() {
        val session = sharing.supabase.session.value ?: return
        val key = KEY_KNOWN + session.userId
        val now = sharing.followers()
        val known = prefs.getStringSet(key, null)
        prefs.edit().putStringSet(key, now.map { it.id }.toSet()).apply()
        newOnes(known, now).forEach(::notify)
    }

    private fun notify(person: Person) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.new_follower_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val name = person.name.ifBlank { context.getString(R.string.new_follower_someone) }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sukoon)
            .setContentTitle(context.getString(R.string.new_follower_title, name))
            .setContentText(context.getString(R.string.new_follower_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.new_follower_body)))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (!lockedFollowers()) {
            val remove = Intent(context, RemoveFollowerReceiver::class.java).putExtra(EXTRA_ID, person.id).putExtra(EXTRA_NAME, name)
            builder.addAction(0, context.getString(R.string.new_follower_remove), PendingIntent.getBroadcast(context, person.id.hashCode(), remove, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        manager.notify(TAG, person.id.hashCode(), builder.build())
    }

    companion object {
        private const val CHANNEL = "new_followers"
        private const val TAG = "new_follower"
        private const val KEY_KNOWN = "followers_known_"
        internal const val EXTRA_ID = "follower_id"
        internal const val EXTRA_NAME = "follower_name"
        private val CHECK_EVERY_MS = TimeUnit.MINUTES.toMillis(15)

        /** Who in [now] wasn't [known]; nobody on the first check ([known] null). */
        internal fun newOnes(known: Set<String>?, now: List<Person>): List<Person> = if (known == null) emptyList() else now.filter { it.id !in known }

        /** After Remove: the notification says it's done (or that it didn't go through). */
        internal fun removed(context: Context, id: String, name: String, ok: Boolean) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val text = context.getString(if (ok) R.string.new_follower_removed else R.string.new_follower_remove_failed, name)
            manager.notify(
                TAG, id.hashCode(),
                NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_sukoon).setContentTitle(text).setTimeoutAfter(TimeUnit.MINUTES.toMillis(10)).build(),
            )
        }
    }
}

/** The new-follower notification's Remove. */
class RemoveFollowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(NewFollowers.EXTRA_ID) ?: return
        val name = intent.getStringExtra(NewFollowers.EXTRA_NAME).orEmpty()
        val container = (context.applicationContext as SukoonApp).container
        val pending = goAsync()
        container.appScope.launch {
            val ok = runCatching { container.sharing.removeFollower(id) }.isSuccess
            NewFollowers.removed(context, id, name, ok)
            pending.finish()
        }
    }
}
