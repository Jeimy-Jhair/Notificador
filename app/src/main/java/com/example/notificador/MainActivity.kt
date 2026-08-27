package com.example.notificador

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.notificador.ui.theme.NotificadorTheme
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels(this)
        setContent {
            NotificadorTheme {
                MainScreen()
            }
        }
    }
}

// Notification Helper
object NotificationHelper {
    const val CHANNEL_HIGH = "high_priority"
    const val CHANNEL_MEDIUM = "medium_priority"
    const val CHANNEL_LOW = "low_priority"

    fun showNotification(context: Context, id: Int, title: String, desc: String, priority: String) {
        val channelId = when (priority) {
            "Alta" -> CHANNEL_HIGH
            "Media" -> CHANNEL_MEDIUM
            else -> CHANNEL_LOW
        }

        val importance = when (priority) {
            "Alta" -> NotificationCompat.PRIORITY_HIGH
            "Media" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }

        // Action to Unpin (via BroadcastReceiver)
        val unpinIntent = Intent(context, BootReceiver::class.java).apply {
            action = "com.example.notificador.ACTION_UNPIN"
            putExtra("notification_id", id)
        }
        val unpinPendingIntent = PendingIntent.getBroadcast(
            context, id, unpinIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(desc)
            .setPriority(importance)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_delete, "Desfijar", unpinPendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }
}

fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        
        val channels = listOf(
            NotificationChannel(NotificationHelper.CHANNEL_HIGH, "Alta Prioridad", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(NotificationHelper.CHANNEL_MEDIUM, "Media Prioridad", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(NotificationHelper.CHANNEL_LOW, "Baja Prioridad", NotificationManager.IMPORTANCE_LOW)
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val priorities = listOf("Alta", "Media", "Baja")
    var selectedPriority by remember { mutableStateOf(priorities[1]) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permiso de notificaciones necesario", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Notificador 📌") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la nota") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Text("Prioridad", style = MaterialTheme.typography.titleMedium)
            
            Row(
                Modifier
                    .selectableGroup()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                priorities.forEach { priority ->
                    val color = when (priority) {
                        "Alta" -> Color.Red
                        "Media" -> Color.Yellow
                        else -> Color.Cyan
                    }
                    
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { selectedPriority = priority },
                        label = { Text(priority) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.3f),
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val id = System.currentTimeMillis().toInt()
                        saveNotification(context, id, title, desc, selectedPriority)
                        NotificationHelper.showNotification(context, id, title, desc, selectedPriority)
                        title = ""
                        desc = ""
                        Toast.makeText(context, "Notificación fijada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "El título es obligatorio", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Fijar Notificación")
            }
        }
    }
}

fun saveNotification(context: Context, id: Int, title: String, desc: String, priority: String) {
    val prefs = context.getSharedPreferences("notificador_prefs", Context.MODE_PRIVATE)
    val notificationsJson = prefs.getString("notifications", "[]") ?: "[]"
    val array = JSONArray(notificationsJson)
    
    val obj = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("desc", desc)
        put("priority", priority)
    }
    array.put(obj)
    prefs.edit().putString("notifications", array.toString()).apply()
}
