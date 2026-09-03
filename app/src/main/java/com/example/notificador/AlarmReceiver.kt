package com.example.notificador

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.example.notificador.ACTION_ALARM"
        const val ACTION_DISMISS = "com.example.notificador.ACTION_DISMISS_ALARM"
        const val CHANNEL_ALARM = "alarm_channel"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                val alarmId = intent.getIntExtra("alarm_id", -1)
                if (alarmId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(alarmId)
                    Log.i(TAG, "Alarma apagada por tap/dismiss alarmId=$alarmId")
                }
                // También abrir la app si se pulsó "Apagar" o se descartó, opcional:
                // No abrimos actividad en dismiss por swipe para no ser intrusivo
                return
            }
            ACTION_ALARM -> { /* continúa abajo */ }
            else -> {
                Log.w(TAG, "onReceive action inesperado: ${intent.action}")
                return
            }
        }

        val id = intent.getIntExtra("notification_id", -1)
        val title = intent.getStringExtra("title") ?: "Recordatorio"
        val desc = intent.getStringExtra("desc") ?: ""
        val time = intent.getStringExtra("time") ?: ""

        Log.i(TAG, "Alarma disparada id=$id title=$title time=$time")

        createAlarmChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val contentText = if (time.isNotEmpty() && desc.isNotEmpty()) {
            "Hora: $time\n$desc"
        } else if (time.isNotEmpty()) {
            "Hora: $time"
        } else {
            desc.ifEmpty { "¡Es hora de tu recordatorio!" }
        }

        // ID separado para no pisar la fija (id es de la fija ongoing)
        val alarmId = if (id != -1) id + 100000 else System.currentTimeMillis().toInt()

        // Intent al pulsar la notificación -> apaga la alarma (cancela notificación) y abre la app
        // Usamos un Broadcast de auto-cancel + Activity para asegurar que el sonido se corte al tap
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("alarm_id", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, alarmId, dismissIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, alarmId + 1, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.notification_log_svgrepo_com)
            .setContentTitle("⏰ $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            // En Android O+ el sonido lo define el canal, pero lo dejamos para compatibilidad pre-O
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setLights(0xFF0000FF.toInt(), 500, 500)
            // Acción explícita para apagar
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Apagar", dismissPendingIntent)
            .build()

        notificationManager.notify(alarmId, notification)
        Log.i(TAG, "Notificación de alarma mostrada alarmId=$alarmId (tap para apagar)")
    }

    private fun createAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CHANNEL_ALARM)

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Si el canal ya existe pero tiene importancia baja o sin sonido, lo recreamos
            if (existing != null) {
                val needsRecreate = existing.importance < NotificationManager.IMPORTANCE_HIGH ||
                        existing.sound == null
                if (!needsRecreate) {
                    Log.d(TAG, "Canal alarma ya existe con configuración correcta")
                    return
                }
                Log.w(TAG, "Recreando canal alarma (importance=${existing.importance} sound=${existing.sound})")
                manager.deleteNotificationChannel(CHANNEL_ALARM)
            }

            val channel = NotificationChannel(
                CHANNEL_ALARM,
                "Alarma de Recordatorio",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para alarmas programadas con sonido"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setSound(alarmSound, audioAttributes)
                // Mostrar badge y en pantalla de bloqueo
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
            Log.i(TAG, "Canal alarma creado con sonido $alarmSound")
        }
    }
}
