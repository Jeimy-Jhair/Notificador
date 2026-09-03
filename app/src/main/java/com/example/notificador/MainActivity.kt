package com.example.notificador

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
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

    fun showNotification(
        context: Context,
        id: Int,
        title: String,
        desc: String,
        priority: String,
        date: String,
        time: String = "",
        hasAlarm: Boolean = false,
        alarmMinutesBefore: Int = 0
    ) {
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
                val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaDate = inputFormat.parse(date)
                val spanishLocale = Locale("es", "ES")
                val outputFormat = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", spanishLocale)
                val stringFecha = fechaDate?.let { outputFormat.format(it) } ?: date
                stringFecha.replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }
            } catch (e: Exception) {
                date
            }
        } else ""

        // 2. Formateamos hora si existe
        val horaFormateada = if (time.isNotEmpty()) "Hora: $time" else ""

        // 3. Construimos el texto: fecha + hora + descripción
        val contentText = buildString {
            if (fechaFormateada.isNotEmpty()) {
                append(fechaFormateada)
                if (horaFormateada.isNotEmpty() || desc.isNotEmpty()) append("\n")
            }
            if (horaFormateada.isNotEmpty()) {
                append(horaFormateada)
                if (desc.isNotEmpty()) append("\n")
            }
            if (desc.isNotEmpty()) {
                append(desc)
            }
            // Info de alarma opcional en la notificación fija
            if (hasAlarm && time.isNotEmpty() && date.isNotEmpty()) {
                val alarmInfo = if (alarmMinutesBefore == 0) "🔔 Alarma a la hora" else "🔔 Alarma $alarmMinutesBefore min antes"
                if (isNotEmpty()) append("\n")
                append(alarmInfo)
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
            .setContentText(if (contentText.isEmpty()) title else contentText.take(50))
            .setPriority(importance)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_delete, "Desfijar", unpinPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText.ifEmpty { title }))
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
    var time by remember { mutableStateOf("") }
    var hasAlarm by remember { mutableStateOf(false) }
    var alarmMinutesBefore by remember { mutableIntStateOf(10) }
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

    val timePickerDialog = android.app.TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            // Formato 24h HH:mm para almacenamiento, pero mostramos HH:mm
            time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true // 24h format
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            capitalization = KeyboardCapitalization.Sentences
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
                        placeholder = { Text("dd/MM/yyyy") },
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

                    // Campo Hora (NUEVO)
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Hora (opcional)") },
                        placeholder = { Text("HH:mm") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            Row {
                                if (time.isNotEmpty()) {
                                    IconButton(onClick = { time = ""; hasAlarm = false }) {
                                        Icon(
                                            imageVector = Icons.Filled.DateRange,
                                            contentDescription = "Limpiar hora"
                                        )
                                    }
                                }
                                IconButton(onClick = { timePickerDialog.show() }) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = "Seleccionar hora"
                                    )
                                }
                            }
                        }
                    )

                    // Switch y opciones de alarma (NUEVO - opcional, se puede quitar)
                    if (time.isNotEmpty() && date.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Alarm,
                                    contentDescription = null,
                                    tint = if (hasAlarm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text("Recordatorio con alarma", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Sonará como alarma",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = hasAlarm,
                                onCheckedChange = { hasAlarm = it }
                            )
                        }

                        if (hasAlarm) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "¿Cuánto antes quieres que suene?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Selector de minutos antes
                            val minuteOptions = listOf(0, 5, 10, 15, 30, 60)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Primera fila: 0, 5, 10
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    minuteOptions.take(3).forEach { mins ->
                                        val label = if (mins == 0) "A la hora" else "${mins} min antes"
                                        FilterChip(
                                            selected = alarmMinutesBefore == mins,
                                            onClick = { alarmMinutesBefore = mins },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                                // Segunda fila: 15, 30, 60
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    minuteOptions.drop(3).forEach { mins ->
                                        val label = "${mins} min antes"
                                        FilterChip(
                                            selected = alarmMinutesBefore == mins,
                                            onClick = { alarmMinutesBefore = mins },
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }

                            // Preview del horario de alarma
                            val alarmPreview = remember(time, date, alarmMinutesBefore) {
                                AlarmHelper.getTriggerTimeFormatted(date, time, alarmMinutesBefore)
                            }
                            if (alarmPreview.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Alarm,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Alarma sonará: $alarmPreview",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else if (time.isNotEmpty() || date.isNotEmpty()) {
                        // Hint si solo uno está completo
                        Text(
                            "Completa fecha y hora para activar alarma",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "El título es obligatorio", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    // Validar que si quiere alarma, tenga fecha y hora
                    if (hasAlarm && (date.isEmpty() || time.isEmpty())) {
                        Toast.makeText(context, "Para la alarma necesitas fecha y hora", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val id = System.currentTimeMillis().toInt()
                    saveNotification(context, id, title, desc, selectedPriority, date, time, hasAlarm, alarmMinutesBefore)
                    NotificationHelper.showNotification(context, id, title, desc, selectedPriority, date, time, hasAlarm, alarmMinutesBefore)

                    // Programar alarma si está activada
                    if (hasAlarm) {
                        // Verificar permiso de alarmas exactas en Android 12+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            if (!alarmManager.canScheduleExactAlarms()) {
                                Toast.makeText(context, "Concede permiso de alarmas en Ajustes para que suene", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        }
                        val scheduled = AlarmHelper.scheduleAlarm(context, id, title, desc, date, time, alarmMinutesBefore)
                        if (scheduled) {
                            val trigger = AlarmHelper.getTriggerTimeFormatted(date, time, alarmMinutesBefore)
                            Toast.makeText(context, "Notificación fijada - Alarma: $trigger", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Notificación fijada (alarma no programada: hora ya pasó)", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Notificación fijada", Toast.LENGTH_SHORT).show()
                    }

                    title = ""
                    desc = ""
                    date = ""
                    time = ""
                    hasAlarm = false
                    alarmMinutesBefore = 10
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Fijar Notificación", style = MaterialTheme.typography.titleMedium)
            }

            // Espacio extra para scroll
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun saveNotification(
    context: Context,
    id: Int,
    title: String,
    desc: String,
    priority: String,
    date: String,
    time: String = "",
    hasAlarm: Boolean = false,
    alarmMinutesBefore: Int = 0
) {
    val prefs = context.getSharedPreferences("notificador_prefs", Context.MODE_PRIVATE)
    val notificationsJson = prefs.getString("notifications", "[]") ?: "[]"
    val array = JSONArray(notificationsJson)
    
    val obj = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("desc", desc)
        put("priority", priority)
        put("date", date)
        put("time", time)
        put("hasAlarm", hasAlarm)
        put("alarmMinutesBefore", alarmMinutesBefore)
    }
    array.put(obj)
    prefs.edit().putString("notifications", array.toString()).apply()
}
