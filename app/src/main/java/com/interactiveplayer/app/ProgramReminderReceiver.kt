package com.interactiveplayer.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Dispara quando o alarme de um lembrete de programação chega — mesmo
 * com o app fechado. Conteúdo igual ao original ("Começando agora 📺" /
 * "{title} — {channelName}"). Tocar na notificação abre o canal direto.
 */
class ProgramReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "program-reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val channelName = intent.getStringExtra("channelName").orEmpty()
        val channelStreamId = intent.getStringExtra("channelStreamId").orEmpty()
        val channelLogo = intent.getStringExtra("channelLogo")
        val reminderId = intent.getStringExtra("id") ?: title

        ensureChannel(context)

        val openIntent = Intent(context, ChannelDetailsActivity::class.java).apply {
            putExtra("name", channelName)
            putExtra("logo", channelLogo)
            putExtra("streamId", channelStreamId)
            // O stream_id + as credenciais salvas já são suficientes pra
            // montar a URL de volta — ver ChannelDetailsActivity.itemFromIntent.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Começando agora 📺")
            .setContentText("$title — $channelName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(reminderId.hashCode(), notification) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Lembretes de programação", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
