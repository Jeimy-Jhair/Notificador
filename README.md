# Notificador Lite 📌

Una aplicación nativa para Android diseñada para mantener **notificaciones fijas en la barra de estado**. Inspirada en la app original Collateral, te permite priorizar pendientes y recordatorios sin que se borren por accidente.

---

### ¿Qué hace?

* **Notificaciones fijas:** Los recordatorios se quedan en la barra superior (`setOngoing`) y no se borran al limpiar las notificaciones.
* **Organización por color:** Prioridad Alta (urgente), Media y Baja para identificar rápido tus pendientes.
* **Sobrevive a reinicios:** Si apagas o reinicias el teléfono, las notificaciones activas reaparecen automáticamente.
* **Un toque para terminar:** Botón directo de "Desfijar" en la misma notificación para quitarla cuando completes la tarea.

---

### Tecnologías

* **Lenguaje:** Kotlin
* **Diseño:** Jetpack Compose (Material 3)
* **Datos:** SharedPreferences
* **Módulos:** NotificationManager & BroadcastReceiver (compatible con Android 13+)

---

### Estructura rápida
├── receivers/  
│   └── BootReceiver.kt       // Recupera las notas al encender el teléfono   
├── ui/  
│   └── MainActivity.kt       // Formulario simple en Compose  
└── utils/  
└── NotificationHelper.kt // Creación y control de canales  

### Cómo probarlo

1. Clona el repo:
   ```bash
   git clone https://github.com/Jeimy-Jhair/Notificador

Como Instalar:

1. Abre el proyecto en Android Studio.
2. Conecta tu teléfono y dale a Run.
