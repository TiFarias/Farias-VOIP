package com.example.fariasvoip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.*
import java.util.Timer
import java.util.TimerTask

object SipManager {
    private var appContext: Context? = null
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private const val PERSISTENT_CHANNEL_ID = "sip_persistent_channel"
    private const val CALL_CHANNEL_ID = "sip_call_channel"
    private const val SERVICE_NOTIFICATION_ID = 1000
    private const val CALL_NOTIFICATION_ID = 1001

    var statusRegistro by mutableStateOf("Desconectado")
    private lateinit var core: Core
    private val factory = Factory.instance()

    // Variaveis para INTERFACE GRAFICA
    var estaEmChamada by mutableStateOf(false)
    var estaTocando by mutableStateOf(false)
    var estaEmConferencia by mutableStateOf(false)
    var numeroDestino by mutableStateOf("")
    var duracaoChamada by mutableStateOf(0)
    var contagemParticipantes by mutableStateOf(0)
    var estaMutado by mutableStateOf(false)
    var estaNoVivaVoz by mutableStateOf(false)
    private var timerChamada: Timer? = null

    private var chamadaAtual: Call? = null
    private var tipoChamadaAtual: TipoChamada? = null
    private var foiAtendida: Boolean = false

    private var db: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var coreIniciado = false
    private var ultimoTimestampRegistro = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iterateRunnable = object : Runnable {
        override fun run() {
            core.iterate()
            mainHandler.postDelayed(this, 20)
        }
    }

    fun setup(context: Context) {
        if (coreIniciado) return
        coreIniciado = true
        appContext = context.applicationContext
        db = AppDatabase.getDatabase(context)
        
        // Inicializa Vibrador
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        criarCanalNotificacao()

        core = factory.createCore(null, null, context)

        // 2. BLOCO DE REDE
        core.isIpv6Enabled = false
        core.setAudioPortRange(10000, 20000)

        // Configuração de NAT e STUN para evitar queda de chamadas e falta de áudio
        val natPolicy = core.createNatPolicy()
        natPolicy.stunServer = "stun.l.google.com:19302" // Servidor STUN gratuito do Google
        natPolicy.isStunEnabled = true
        natPolicy.isIceEnabled = true
        core.natPolicy = natPolicy

        // Impede que o Asterisk tente o "Direct Media", forçando o áudio a passar pelo servidor.
        core.isNetworkReachable = true

        // Otimização de áudio para reduzir DELAY (Latência)
        core.config.setBool("rtp", "jitter_buffer_enabled", true)
        core.config.setInt("rtp", "jitter_buffer_min_size", 40)
        core.config.setInt("rtp", "jitter_buffer_max_size", 120)
        core.config.setBool("rtp", "jitter_buffer_adaptive_enabled", true)
        
        // Evita que chamadas existentes entrem em HOLD ao iniciar uma nova
        core.config.setBool("sip", "all_to_pause", false)

        // Configuração de conferência local
        val confParams = core.createConferenceParams(null)
        confParams.setConferenceFactoryAddress(null)
        
        core.isEchoCancellationEnabled = true
        core.isEchoLimiterEnabled = false 
        
        core.config.setBool("sound", "local_participant", true)
        core.isAutoIterateEnabled = false
        core.isAdaptiveRateControlEnabled = true

        core.isMicEnabled = true

        // 4. BLOCO DE CODECS
        for (payloadType in core.audioPayloadTypes) {
            val mime = payloadType.mimeType
            if (mime == "PCMU" || mime == "PCMA" || mime == "opus") {
                payloadType.enable(true)
            } else {
                payloadType.enable(false)
            }
        }

        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(c: Core, acc: Account, state: RegistrationState?, msg: String) {
                // Sempre atualizar UI na Main Thread
                mainHandler.post {
                    val novoStatus = when (state) {
                        RegistrationState.Ok -> "Conectado ao SNEP!"
                        RegistrationState.Failed -> "Erro: $msg"
                        RegistrationState.Progress -> {
                            if (statusRegistro != "Conectado ao SNEP!") "Conectando..." else statusRegistro
                        }
                        RegistrationState.None -> {
                            if (statusRegistro == "Conectando...") "Desconectado" else statusRegistro
                        }
                        else -> statusRegistro
                    }
                    
                    if (statusRegistro != novoStatus) {
                        statusRegistro = novoStatus
                        if (novoStatus == "Conectado ao SNEP!") {
                            cancelarNotificacao() // Atualiza para o estado persistente de "Ativo"
                        }
                    }
                }
            }

            override fun onCallStateChanged(c: Core, call: Call, state: Call.State?, msg: String) {
                mainHandler.post {
                    tratarMudancaEstadoChamada(c, call, state, msg)
                }
            }
        })

        core.start()
        mainHandler.post(iterateRunnable)
    }

    private fun tratarMudancaEstadoChamada(c: Core, call: Call, state: Call.State?, msg: String) {
        val ramalRemoto = call.remoteAddress.username ?: "Desconhecido"
        
        when (state) {
            Call.State.IncomingReceived -> {
                estaTocando = true
                chamadaAtual = call
                tipoChamadaAtual = TipoChamada.ENTRADA
                foiAtendida = false
                
                scope.launch {
                    val nome = buscarNomeContato(ramalRemoto)
                    mainHandler.post { 
                        numeroDestino = nome ?: ramalRemoto
                        iniciarVibracao()
                        iniciarToque()
                        mostrarNotificacaoChamada(numeroDestino)
                    }
                }
            }

            Call.State.OutgoingInit -> {
                estaEmChamada = true
                if (!estaEmConferencia) {
                    duracaoChamada = 0
                }
                chamadaAtual = call
                tipoChamadaAtual = TipoChamada.SAIDA
                foiAtendida = true // Chamada sainte "atendida" no sentido de completada
                
                scope.launch {
                    val nome = buscarNomeContato(ramalRemoto)
                    mainHandler.post {
                        numeroDestino = nome ?: ramalRemoto
                        mostrarNotificacaoEmAndamento()
                    }
                }
            }

            Call.State.Connected -> {
                estaTocando = false
                estaEmChamada = true
                foiAtendida = true
                pararVibracao()
                pararToque()
                cancelarNotificacao()
                
                if (estaEmConferencia) {
                    core.addToConference(call)
                    atualizarContagemRemota()
                }
            }

            Call.State.StreamsRunning -> {
                iniciarCronometro()
                
                if (estaEmConferencia) {
                    core.addToConference(call)
                    core.enterConference()
                    atualizarContagemRemota()
                }

                call.microphoneVolumeGain = 1.2f
                call.speakerVolumeGain = 1.2f
            }

            Call.State.UpdatedByRemote -> {
                call.acceptWithParams(call.currentParams)
            }

            Call.State.Paused, Call.State.PausedByRemote -> {
                call.resume()
                if (estaEmConferencia) {
                    core.addToConference(call)
                    core.enterConference()
                }
            }
            Call.State.Released -> {
                // SALVAR NO HISTÓRICO
                val ramalHistorico = ramalRemoto
                val duracaoFinal = duracaoChamada
                val tipoFinal = if (tipoChamadaAtual == TipoChamada.ENTRADA && !foiAtendida) TipoChamada.PERDIDA else tipoChamadaAtual
                
                scope.launch {
                    val nome = buscarNomeContato(ramalHistorico)
                    val entrada = HistoricoChamada(
                        ramal = ramalHistorico,
                        nome = nome,
                        tipo = tipoFinal ?: TipoChamada.ENTRADA,
                        dataHora = System.currentTimeMillis(),
                        duracao = duracaoFinal,
                        foiAtendida = foiAtendida
                    )
                    db?.historicoDao()?.inserir(entrada)
                }

                val chamadasAtivas = c.calls.filter { 
                    it != call && (it.state == Call.State.Connected || it.state == Call.State.StreamsRunning || it.state == Call.State.Paused || it.state == Call.State.PausedByRemote)
                }
                val nRestantes = chamadasAtivas.size
                
                if (call == segundaChamada) segundaChamada = null
                if (call == primeiraChamada) primeiraChamada = null

                if (nRestantes == 0) {
                    estaEmChamada = false
                    estaTocando = false
                    estaEmConferencia = false
                    contagemParticipantes = 0
                    chamadaAtual = null
                    pararCronometro()
                    pararVibracao()
                    pararToque()
                    cancelarNotificacao()
                    estaMutado = false
                    estaNoVivaVoz = false
                } else {
                    val callRestante = chamadasAtivas.firstOrNull()
                    callRestante?.let {
                        chamadaAtual = it
                        val ramalNovo = it.remoteAddress.username ?: "Ramal"
                        scope.launch {
                            val nomeNovo = buscarNomeContato(ramalNovo)
                            mainHandler.post { numeroDestino = nomeNovo ?: ramalNovo }
                        }
                    }

                    if (estaEmConferencia) {
                        if (nRestantes < 2) {
                            estaEmConferencia = false
                            contagemParticipantes = 0
                            c.leaveConference() 
                            callRestante?.let {
                                c.removeFromConference(it)
                                it.resume()
                            }
                        } else {
                            contagemParticipantes = nRestantes
                            c.enterConference()
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private suspend fun buscarNomeContato(ramal: String): String? {
        return withContext(Dispatchers.IO) {
            val contato = db?.contatoDao()?.buscarPorRamal(ramal)
            contato?.nome
        }
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Canal para o serviço em segundo plano (Silencioso/Baixa prioridade)
            val pName = "Farias VOIP - Serviço"
            val pImportance = NotificationManager.IMPORTANCE_LOW
            val pChannel = NotificationChannel(PERSISTENT_CHANNEL_ID, pName, pImportance).apply {
                description = "Mantém o ramal conectado em segundo plano"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(pChannel)

            // 2. Canal para chamadas recebidas (Alta prioridade / Wake-up)
            val cName = "Chamadas Recebidas"
            val cImportance = NotificationManager.IMPORTANCE_HIGH
            val cChannel = NotificationChannel(CALL_CHANNEL_ID, cName, cImportance).apply {
                description = "Notificações de chamadas de voz"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null) // Som gerenciado manualmente
                enableVibration(false) // Vibração gerenciada manualmente
            }
            notificationManager.createNotificationChannel(cChannel)
        }
    }

    private fun mostrarNotificacaoChamada(nome: String) {
        val context = appContext ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Chamada de $nome")
            .setContentText("Toque para atender")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true) 
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    private fun mostrarNotificacaoEmAndamento() {
        val context = appContext ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setContentTitle("Farias VOIP")
            .setContentText("Chamada em andamento...")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
            
        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    private fun cancelarNotificacao() {
        val context = appContext ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Remove notificação de chamada
        notificationManager.cancel(CALL_NOTIFICATION_ID)

        if (estaEmChamada) {
            mostrarNotificacaoEmAndamento()
        } else {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, PERSISTENT_CHANNEL_ID)
                .setContentTitle("Farias VOIP")
                .setContentText("Serviço de Voz Ativo")
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
        }
    }

    private fun iniciarVibracao() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 1000)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 1000), 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pararVibracao() {
        vibrator?.cancel()
    }

    private fun iniciarToque() {
        try {
            if (ringtone == null) {
                val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(appContext, notificationUri)
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pararToque() {
        ringtone?.stop()
        ringtone = null
    }

    fun registrarRamal(usuario: String, senha: String, servidor: String, iniciarServico: Boolean = true) {
        val agora = System.currentTimeMillis()
        if (agora - ultimoTimestampRegistro < 2000) {
            println("SNEP: Registro ignorado (muito frequente)")
            return
        }
        ultimoTimestampRegistro = agora

        // Verifica se já estamos registrados ou tentando registrar com os mesmos dados
        val defaultAccount = core.defaultAccount
        if (defaultAccount != null) {
            val identity = defaultAccount.params.identityAddress
            if (identity?.username == usuario && identity?.domain == servidor) {
                if (statusRegistro == "Conectado ao SNEP!" || statusRegistro == "Conectando...") {
                    return
                }
            }
        }

        mainHandler.post { 
            if (statusRegistro != "Conectando...") statusRegistro = "Conectando..." 
        }

        core.clearAccounts()
        core.clearAllAuthInfo()

        val identity = factory.createAddress("sip:$usuario@$servidor")
        val authInfo = factory.createAuthInfo(usuario, null, senha, null, null, servidor, null)
        val params = core.createAccountParams()

        params.identityAddress = identity
        params.isPublishEnabled = true

        val serverAddress = factory.createAddress("sip:$servidor")
        params.serverAddress = serverAddress
        params.isRegisterEnabled = true
        params.transport = TransportType.Udp
        params.natPolicy = core.natPolicy
        params.expires = 3600 

        core.isSessionExpiresEnabled = false

        val account = core.createAccount(params)
        core.addAuthInfo(authInfo)
        core.addAccount(account)
        core.defaultAccount = account

        // Inicia o serviço para manter o Core vivo em segundo plano
        if (iniciarServico) {
            appContext?.let { ctx ->
                val serviceIntent = Intent(ctx, SipService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(serviceIntent)
                } else {
                    ctx.startService(serviceIntent)
                }
            }
        }
    }

    fun deslogar() {
        core.clearAccounts()
        core.clearAllAuthInfo()
        statusRegistro = "Desconectado"
        appContext?.stopService(Intent(appContext, SipService::class.java))
    }

    fun fazerLigacao(numero: String) {
        if (core.callsNb > 0) return
        val dominio = core.defaultAccount?.params?.serverAddress?.domain
        val destino = factory.createAddress("sip:$numero@$dominio")
        val params = core.createCallParams(null)
        if (destino != null && params != null) {
            core.inviteAddressWithParams(destino, params)
        }
    }

    fun encerrarChamada() {
        core.currentCall?.terminate()
        chamadaAtual?.terminate()
        pararCronometro()
        estaMutado = false
        estaNoVivaVoz = false
    }

    fun aceitarChamada() {
        val params = core.createCallParams(chamadaAtual)
        chamadaAtual?.acceptWithParams(params)
        core.isMicEnabled = true
        estaMutado = false
    }

    fun decidirAceitarOuRecusar() {
        // Método opcional se quiser tratar lógica extra antes de aceitar
    }

    fun rejeitarChamada() {
        chamadaAtual?.terminate()
    }

    fun alternarMudo() {
        estaMutado = !estaMutado
        core.isMicEnabled = !estaMutado
    }

    fun alternarVivaVoz() {
        estaNoVivaVoz = !estaNoVivaVoz
        if (estaNoVivaVoz) {
            val speaker = core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Speaker }
            speaker?.let { core.outputAudioDevice = it }
        } else {
            val earpiece = core.audioDevices.firstOrNull { it.type == AudioDevice.Type.Earpiece }
            earpiece?.let { core.outputAudioDevice = it }
        }
    }

    private fun atualizarContagemRemota() {
        contagemParticipantes = core.calls.count { 
            val s = it.state
            s == Call.State.Connected || s == Call.State.StreamsRunning || s == Call.State.Paused || s == Call.State.PausedByRemote
        }
    }

    fun iniciarCronometro() {
        if (timerChamada != null) return 
        duracaoChamada = 0
        timerChamada = Timer()
        timerChamada?.schedule(object : TimerTask() {
            override fun run() {
                duracaoChamada++
            }
        }, 1000, 1000)
    }
    fun pararCronometro() {
        timerChamada?.cancel()
        timerChamada = null
    }

    private var primeiraChamada: Call? = null
    private var segundaChamada: Call? = null

    fun iniciarTransferenciaAssistida(numeroDestino: String) {
        primeiraChamada = core.currentCall
        val dominio = core.defaultAccount?.params?.serverAddress?.domain
        val uriCompleta = "sip:${numeroDestino}@${dominio}"
        val enderecoDestino = factory.createAddress(uriCompleta)
        val params = core.createCallParams(null)

        if (enderecoDestino != null && params != null) {
            primeiraChamada?.pause()
            segundaChamada = core.inviteAddressWithParams(enderecoDestino, params)
        }
    }

    fun concluirTransferencia() {
        val call1 = primeiraChamada
        val call2 = segundaChamada
        if (call1 != null && call2 != null) {
            if (call2.state == Call.State.StreamsRunning || call2.state == Call.State.Connected) {
                call1.transferToAnother(call2)
                segundaChamada = null
                primeiraChamada = null
                estaEmChamada = false
                pararCronometro()
            }
        }
    }

    fun cancelarTransferencia() {
        segundaChamada?.terminate()
        primeiraChamada?.resume()
        segundaChamada = null
    }

    fun iniciarConferencia() {
        val calls = core.calls
        if (calls.isNotEmpty()) {
            estaEmConferencia = true
            core.addAllToConference() 
            core.enterConference() 
            contagemParticipantes = calls.size
        }
    }

    fun adicionarParticipante(numero: String) {
        if (core.callsNb >= 5) return
        val dominio = core.defaultAccount?.params?.serverAddress?.domain
        val uri = "sip:$numero@$dominio"
        val endereco = factory.createAddress(uri)
        val params = core.createCallParams(null)

        if (endereco != null && params != null) {
            if (!estaEmConferencia) {
                estaEmConferencia = true
                core.addAllToConference()
                core.enterConference()
            }
            core.calls.forEach { 
                if (it.state == Call.State.Paused || it.state == Call.State.PausedByRemote) {
                    it.resume()
                }
            }
            core.inviteAddressWithParams(endereco, params)
        }
    }

    fun removerDaConferencia(call: Call) {
        core.removeFromConference(call)
        contagemParticipantes = core.callsNb
    }

    fun encerrarConferencia() {
        val chamadasAtivas = core.calls.filter { 
            val s = it.state
            s == Call.State.Connected || s == Call.State.StreamsRunning || s == Call.State.Paused || s == Call.State.PausedByRemote
        }
        
        if (chamadasAtivas.size == 2) {
            val call1 = chamadasAtivas[0]
            val call2 = chamadasAtivas[1]
            core.leaveConference()
            core.removeFromConference(call1)
            core.removeFromConference(call2)
            try {
                call1.transferToAnother(call2)
            } catch (e: Exception) {
                call1.terminate()
                call2.terminate()
            }
        } else {
            core.terminateAllCalls()
        }
        
        estaEmConferencia = false
        estaEmChamada = false
        contagemParticipantes = 0
        pararCronometro()
        chamadaAtual = null
        estaMutado = false
        estaNoVivaVoz = false
    }
}
