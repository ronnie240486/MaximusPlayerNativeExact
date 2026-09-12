package com.interactiveplayer.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lembrete de "vai começar agora" para um programa da grade — portado
 * de `frontend/src/state/program-reminders.ts`. O original agenda uma
 * notificação real do sistema via expo-notifications, que funciona com
 * o app fechado; aqui o equivalente é AlarmManager + um
 * BroadcastReceiver que mostra a notificação na hora certa.
 */
object ProgramReminderStore {
    private const val PREFS = "maximus_native_program_reminders"
    private const val KEY = "reminders"

    data class Reminder(
        val id: String,
        val title: String,
        val channelStreamId: String,
        val channelName: String,
        val channelLogo: String?,
        val startsAtMs: Long,
    )

    fun list(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        Reminder(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            channelStreamId = item.optString("channelStreamId"),
                            channelName = item.optString("channelName"),
                            channelLogo = item.optStringOrNull("channelLogo"),
                            startsAtMs = item.optLong("startsAtMs"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun isScheduled(context: Context, id: String): Boolean = list(context).any { it.id == id }

    /** Devolve true se ficou agendado, false se foi cancelado. */
    fun toggle(context: Context, reminder: Reminder): Boolean {
        val current = list(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == reminder.id }

        if (existingIndex >= 0) {
            cancelAlarm(context, reminder.id)
            current.removeAt(existingIndex)
            persist(context, current)
            return false
        }

        if (reminder.startsAtMs > System.currentTimeMillis()) {
            scheduleAlarm(context, reminder)
        }
        current.add(reminder)
        persist(context, current)
        return true
    }

    private fun scheduleAlarm(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, reminder)
        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startsAtMs, pendingIntent)
            } else {
                // Sem permissão de alarme exato: ainda dispara, só que o
                // sistema pode atrasar alguns minutos.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.startsAtMs, pendingIntent)
            }
        }
    }

    private fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context, id))
    }

    private fun buildPendingIntent(context: Context, reminder: Reminder): PendingIntent =
        buildPendingIntent(context, reminder.id, reminder)

    private fun buildPendingIntent(context: Context, id: String, reminder: Reminder? = null): PendingIntent {
        val intent = Intent(context, ProgramReminderReceiver::class.java).apply {
            putExtra("id", id)
            reminder?.let {
                putExtra("title", it.title)
                putExtra("channelName", it.channelName)
                putExtra("channelStreamId", it.channelStreamId)
                putExtra("channelLogo", it.channelLogo)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    private fun persist(context: Context, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("title", reminder.title)
                    put("channelStreamId", reminder.channelStreamId)
                    put("channelName", reminder.channelName)
                    put("channelLogo", reminder.channelLogo ?: "")
                    put("startsAtMs", reminder.startsAtMs)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
