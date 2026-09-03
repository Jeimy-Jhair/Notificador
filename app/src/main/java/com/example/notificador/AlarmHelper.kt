package com.example.notificador

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmHelper {

    fun scheduleAlarm(
        context: Context,
        id: Int,
        title: String,
        desc: String,
        date: String,
        time: String,
        minutesBefore: Int
    ): Boolean {
        if (date.isEmpty() || time.isEmpty()) return false

        val triggerAtMillis = parseDateTimeToMillis(date, time, minutesBefore) ?: return false

        // Si la hora ya pasó, no programar
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return false
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Android 12+ requiere permiso para alarmas exactas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Fallback: intentará programar igual, pero el sistema puede bloquearlo
                // El usuario debe conceder permiso en Ajustes
                try {
                    // Intentamos igual, si falla capturamos
                } catch (_: SecurityException) {
                    Toast.makeText(context, "Activa el permiso de alarmas exactas en Ajustes", Toast.LENGTH_LONG).show()
                    return false
                }
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("notification_id", id)
            putExtra("title", title)
            putExtra("desc", desc)
            putExtra("time", time)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // Usamos setExactAndAllowWhileIdle para que suene incluso en Doze
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            return true
        } catch (e: SecurityException) {
            Toast.makeText(context, "Sin permiso para programar alarmas exactas", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun cancelAlarm(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun parseDateTimeToMillis(date: String, time: String, minutesBefore: Int): Long? {
        return try {
            // date: dd/MM/yyyy , time: HH:mm (24h)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateTimeStr = "$date $time"
            val parsed = sdf.parse(dateTimeStr) ?: return null
            val cal = Calendar.getInstance().apply { this.time = parsed }
            cal.add(Calendar.MINUTE, -minutesBefore)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    fun getTriggerTimeFormatted(date: String, time: String, minutesBefore: Int): String {
        val millis = parseDateTimeToMillis(date, time, minutesBefore) ?: return ""
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}
