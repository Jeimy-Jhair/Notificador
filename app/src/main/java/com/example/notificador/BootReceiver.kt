package com.example.notificador

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import org.json.JSONArray

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                reloadNotifications(context)
            }
            "com.example.notificador.ACTION_UNPIN" -> {
                val id = intent.getIntExtra("notification_id", -1)
                if (id != -1) {
                    unpinNotification(context, id)
                }
            }
        }
    }

    private fun reloadNotifications(context: Context) {
        val prefs = context.getSharedPreferences("notificador_prefs", Context.MODE_PRIVATE)
        val notificationsJson = prefs.getString("notifications", "[]") ?: "[]"
        val array = JSONArray(notificationsJson)
        
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getInt("id")
            val title = obj.getString("title")
            val desc = obj.getString("desc")
            val priority = obj.getString("priority")
            val date = if (obj.has("date")) obj.getString("date") else ""
            val time = if (obj.has("time")) obj.getString("time") else ""
            val hasAlarm = if (obj.has("hasAlarm")) obj.getBoolean("hasAlarm") else false
            val alarmMinutesBefore = if (obj.has("alarmMinutesBefore")) obj.getInt("alarmMinutesBefore") else 0
            
            NotificationHelper.showNotification(context, id, title, desc, priority, date, time, hasAlarm, alarmMinutesBefore)

            // Reprogramar alarma si estaba activa
            if (hasAlarm && date.isNotEmpty() && time.isNotEmpty()) {
                AlarmHelper.scheduleAlarm(context, id, title, desc, date, time, alarmMinutesBefore)
            }
        }
    }

    private fun unpinNotification(context: Context, id: Int) {
        // Cancel notification fija
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
        // Cancel también la notificación de alarma si ya sonó (id+100000)
        notificationManager.cancel(id + 100000)

        // Cancelar alarma programada
        AlarmHelper.cancelAlarm(context, id)

        // Remove from SharedPreferences
        val prefs = context.getSharedPreferences("notificador_prefs", Context.MODE_PRIVATE)
        val notificationsJson = prefs.getString("notifications", "[]") ?: "[]"
        val array = JSONArray(notificationsJson)
        val newArray = JSONArray()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getInt("id") != id) {
                newArray.put(obj)
            }
        }
        prefs.edit {
            putString("notifications", newArray.toString())
        }
    }
}
