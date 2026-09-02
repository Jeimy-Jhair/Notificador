package com.example.notificador

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            
            NotificationHelper.showNotification(context, id, title, desc, priority, date)
        }
    }

    private fun unpinNotification(context: Context, id: Int) {
        // Cancel notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)

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
        prefs.edit().putString("notifications", newArray.toString()).apply()
    }
}
