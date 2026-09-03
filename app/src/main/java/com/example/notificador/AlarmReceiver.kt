package com.example.notificador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.example.notificador.ACTION_ALARM"
        const val CHANNEL_ALARM = "alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return

        val id = intent.getIntExtra("notification_id", -1)
        val title = intent.getStringExtra("title") ?: "Recordatorio"
        val desc = intent.getStringExtra("desc") ?: ""
        val time = intent.getStringExtra("time") ?: ""

        createAlarmChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Sonido de alarma por defecto
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val contentText = if (time.isNotEmpty() && desc.isNotEmpty()) {
            "Hora: $time\n$desc"
        } else if (time.isNotEmpty()) {
            "Hora: $time"
        } else {
            desc.ifEmpty { "¡Es hora de tu recordatorio!" }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.notification_log_svgrepo_com)
            .setContentTitle("⏰ $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // Usamos un ID diferente para no sobrescribir la notificación fija (id + 100000)
        // Si no hay fija, igual se muestra
        val alarmId = if (id != -1) id + 100000 else System.currentTimeMillis().toInt()
        notificationManager.notify(alarmId, notification)
    }

    private fun createAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ALARM) != null) return

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val channel = NotificationChannel(
                CHANNEL_ALARM,
                "Alarma de Recordatorio",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para alarmas programadas"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(alarmSound, null)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
