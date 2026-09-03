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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.notificador.ui.theme.NotificadorTheme
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels(this)
        setContent {
            NotificadorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

// Notification Helper
object NotificationHelper {
    const val CHANNEL_HIGH = "high_priority"
    const val CHANNEL_MEDIUM = "medium_priority"
    const val CHANNEL_LOW = "low_priority"

    fun showNotification(context: Context, id: Int, title: String, desc: String, priority: String, date: String) {
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

        // 1. Formateamos la fecha si no está vacía
        val fechaFormateada = if (date.isNotEmpty()) {
            try {
                // Formato con el que entra la fecha desde el DatePickerDialog ("dd/MM/yyyy")
                val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaDate = inputFormat.parse(date)

                // Formato deseado: "día, numero de mes de año" en español (ej: "miércoles, 02 de septiembre de 2026")
                val spanishLocale = Locale("es", "ES")
                val outputFormat = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", spanishLocale)

                val stringFecha = fechaDate?.let { outputFormat.format(it) } ?: date

                // Capitalizar la primera letra del día de la semana
                stringFecha.replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }
            } catch (e: Exception) {
                date // Si ocurre un error al parsear, usa la fecha original
            }
        } else ""

        // 2. Construimos el texto concatenando fecha (si existe) y la descripción en la siguiente línea (\n)
        val contentText = buildString {
            if (fechaFormateada.isNotEmpty()) {
                append(fechaFormateada)
                if (desc.isNotEmpty()) append("\n")
            }
            if (desc.isNotEmpty()) {
                append(desc)
            }
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
            .setSmallIcon(R.drawable.notification_log_svgrepo_com)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(importance)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_delete, "Desfijar", unpinPendingIntent)
            // Importante: BigTextStyle permite mostrar múltiples líneas al desplegar la notificación
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NotificadorTheme {
        MainScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    val priorities = listOf("Alta", "Media", "Baja")
    var selectedPriority by remember { mutableStateOf(priorities[1]) }

    val calendar = Calendar.getInstance()
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            calendar.set(year, month, dayOfMonth)
            date = sdf.format(calendar.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

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
            CenterAlignedTopAppBar(
                title = { Text("Notificador", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de la nota") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences // Mayúscula en la primera letra de cada frase
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        minLines = 3
                    )

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Fecha") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(
                                    imageVector = Icons.Filled.DateRange,
                                    contentDescription = "Seleccionar fecha"
                                )
                            }
                        }
                    )
                }
            }

            Text("Prioridad", style = MaterialTheme.typography.titleMedium)
            
            Row(
                Modifier
                    .selectableGroup()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                priorities.forEach { priority ->
                    val color = when (priority) {
                        "Alta" -> Color(0xFFE57373)
                        "Media" -> Color(0xFFFFD54F)
                        else -> Color(0xFF81C784)
                    }
                    
                    FilterChip(
                        selected = selectedPriority == priority,
                        onClick = { selectedPriority = priority },
                        label = { Text(priority) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color,
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
                        saveNotification(context, id, title, desc, selectedPriority, date)
                        NotificationHelper.showNotification(context, id, title, desc, selectedPriority, date)
                        title = ""
                        desc = ""
                        date = ""
                        Toast.makeText(context, "Notificación fijada", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "El título es obligatorio", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Fijar Notificación", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

fun saveNotification(context: Context, id: Int, title: String, desc: String, priority: String, date: String) {
    val prefs = context.getSharedPreferences("notificador_prefs", Context.MODE_PRIVATE)
    val notificationsJson = prefs.getString("notifications", "[]") ?: "[]"
    val array = JSONArray(notificationsJson)
    
    val obj = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("desc", desc)
        put("priority", priority)
        put("date", date)
    }
    array.put(obj)
    prefs.edit().putString("notifications", array.toString()).apply()
}
