package com.example.notificador

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmHelper {

    private const val TAG = "AlarmHelper"
    // RequestCode separado para no colisionar con "Desfijar" (que usa id en BootReceiver)
    private const val REQUEST_CODE_OFFSET = 50000

    fun scheduleAlarm(
        context: Context,
        id: Int,
        title: String,
        desc: String,
        date: String,
        time: String,
        minutesBefore: Int
    ): Boolean {
        if (date.isEmpty() || time.isEmpty()) {
            Log.w(TAG, "scheduleAlarm: fecha u hora vacía")
            return false
        }

        var triggerAtMillis = parseDateTimeToMillis(date, time, minutesBefore)
        if (triggerAtMillis == null) {
            Log.e(TAG, "scheduleAlarm: parse falló date=$date time=$time")
            return false
        }

        val now = System.currentTimeMillis()
        // --- FIX principal: tolerancia por segundos ---
        // SimpleDateFormat parsea "09:40" como 09:40:00.000
        // Si son las 09:40:15, trigger (09:40:00) sería < now y se consideraba "pasado".
        // Permitimos 60s de gracia: si la alarma es de hace <60s, la reprogramamos a now+2s para que suene inmediata.
        if (triggerAtMillis <= now) {
            val diff = now - triggerAtMillis
            if (diff < 60_000) {
                Log.w(TAG, "scheduleAlarm: trigger en el pasado por ${diff}ms, reprogramando a +2s")
                triggerAtMillis = now + 2_000
            } else {
                Log.w(TAG, "scheduleAlarm: trigger ya pasó (diff=${diff}ms), no se programa. now=$now trigger=$triggerAtMillis")
                return false
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra("notification_id", id)
            putExtra("title", title)
            putExtra("desc", desc)
            putExtra("time", time)
        }

        // Usamos offset para no colisionar con PendingIntent de "Desfijar"
        val requestCode = id + REQUEST_CODE_OFFSET
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent para mostrar al pulsar el icono de alarma (requerido por setAlarmClock)
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context, requestCode + 1, showIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "canScheduleExactAlarms=false, usando setAlarmClock (no requiere permiso)")
                    // setAlarmClock funciona sin permiso y es la más fiable para recordatorios tipo alarma
                    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                    alarmManager.setAlarmClock(info, pendingIntent)
                    Log.i(TAG, "Alarma programada con setAlarmClock para $triggerAtMillis (${java.util.Date(triggerAtMillis)})")
                    return true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.i(TAG, "Alarma programada exacta para $triggerAtMillis (${java.util.Date(triggerAtMillis)}) id=$id minsBefore=$minutesBefore")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException al programar exacta, fallback a setAlarmClock", e)
            try {
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(info, pendingIntent)
                Log.i(TAG, "Fallback setAlarmClock exitoso")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback setAlarmClock falló, usando setAndAllowWhileIdle", e2)
                try {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    true
                } catch (e3: Exception) {
                    Log.e(TAG, "setAndAllowWhileIdle también falló", e3)
                    false
                }
            }
        }
    }

    fun cancelAlarm(context: Context, id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
        }
        val requestCode = id + REQUEST_CODE_OFFSET
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.i(TAG, "Alarma cancelada id=$id requestCode=$requestCode")
    }

    fun parseDateTimeToMillis(date: String, time: String, minutesBefore: Int): Long? {
        return try {
            // date: dd/MM/yyyy , time: HH:mm (24h)
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.isLenient = false
            val dateTimeStr = "$date $time"
            val parsed = sdf.parse(dateTimeStr) ?: return null
            val cal = Calendar.getInstance().apply {
                this.time = parsed
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.add(Calendar.MINUTE, -minutesBefore)
            cal.timeInMillis
        } catch (e: Exception) {
            Log.e(TAG, "parseDateTimeToMillis error", e)
            null
        }
    }

    fun getTriggerTimeFormatted(date: String, time: String, minutesBefore: Int): String {
        val millis = parseDateTimeToMillis(date, time, minutesBefore) ?: return ""
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}
