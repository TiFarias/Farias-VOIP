package com.example.fariasvoip

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

class SipService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1000 // Alterado de 1001 para 1000 para evitar conflito com chamadas
        const val PERSISTENT_CHANNEL_ID = "sip_persistent_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Inicializa o SipManager com o contexto do serviço
        SipManager.setup(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification("Serviço de Voz Ativo")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                
                // No Android 14, só podemos declarar MICROPHONE se a permissão já estiver concedida
                val temMicrofone = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && temMicrofone) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Tenta login automático se houver credenciais salvas
        // Passamos iniciarServico = false para evitar o loop infinito de chamadas entre Manager e Service
        val (ramal, senha, dominio) = PrefsManager.buscarCredenciais(this)
        if (ramal != null && senha != null && dominio != null) {
            SipManager.registrarRamal(ramal, senha, dominio, iniciarServico = false)
        }
        
        return START_STICKY
    }

    private fun createForegroundNotification(content: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Farias VOIP - Serviço"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(PERSISTENT_CHANNEL_ID, name, importance).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PERSISTENT_CHANNEL_ID)
            .setContentTitle("Farias VOIP")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
