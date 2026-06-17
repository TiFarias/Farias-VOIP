package com.example.fariasvoip

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.linphone.core.*
import java.util.Timer
import java.util.TimerTask

object SipManager {

    var statusRegistro by mutableStateOf("Desconectado")
    private lateinit var core: Core
    private val factory = Factory.instance()

    // Variaveis para INTERFACE GRAFICA
    var estaEmChamada by mutableStateOf(false)
    var estaTocando by mutableStateOf(false)
    var numeroDestino by mutableStateOf("")
    var duracaoChamada by mutableStateOf(0) // segundos
    private var timerChamada: Timer? = null

    private var chamadaAtual: Call? = null

    fun setup(context: Context) {
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
        // Isso resolve o problema de silêncio quando os aparelhos estão em redes diferentes.
        core.isNetworkReachable = true

        // ---------------------------------------------------------
        // 3. BLOCO DE HARDWARE (AJUSTADO PARA ELIMINAR ECO)
        // ---------------------------------------------------------
        // Ativamos o cancelamento de eco ANTES de iniciar o Core
        core.isEchoCancellationEnabled = true
        core.isEchoLimiterEnabled = true

        // Calibração automática de eco (O Linphone tenta medir o atraso do PC)
        core.isAdaptiveRateControlEnabled = true

        val devices = core.audioDevices
        // Removido o bloqueio forçado do AAudio para permitir que o Linphone escolha o melhor driver automaticamente
        // Isso costuma resolver problemas de áudio mudo em alguns aparelhos
        /*val aaudio = devices.firstOrNull { it.driverName == "AAudio" }
        aaudio?.let {
            core.inputAudioDevice = it
            core.outputAudioDevice = it
        }*/

        core.isMicEnabled = true

        // 4. BLOCO DE CODECS
        for (payloadType in core.audioPayloadTypes) {
            val mime = payloadType.mimeType
            if (mime == "PCMU" || mime == "PCMA") {
                payloadType.enable(true)
            } else {
                payloadType.enable(false)
            }
        }

        // ---------------------------------------------------------
        // 5. BLOCO DE EVENTOS (AJUSTE DE GANHO NO STREAMS RUNNING)
        // ---------------------------------------------------------
        core.addListener(object : CoreListenerStub() {
            override fun onAccountRegistrationStateChanged(c: Core, acc: Account, state: RegistrationState?, msg: String) {
                when (state) {
                    RegistrationState.Ok -> statusRegistro = "Conectado ao SNEP!"
                    RegistrationState.Failed -> statusRegistro = "Erro: $msg"
                    else -> {}
                }
            }

            override fun onCallStateChanged(c: Core, call: Call, state: Call.State?, msg: String) {
                when (state) {
                    Call.State.IncomingReceived -> {
                        estaTocando = true
                        numeroDestino = call.remoteAddress.username ?: "Desconhecido"
                        chamadaAtual = call
                    }

                    Call.State.OutgoingInit -> {
                        estaEmChamada = true
                        duracaoChamada = 0 // Corrige o problema de iniciar a chamada com duracao da anterior
                        numeroDestino = call.remoteAddress.username ?: "Desconhecido"// Pega o número (ex: 4000)
                        chamadaAtual = call
                    }

                    Call.State.Connected -> {
                        estaTocando = false
                        estaEmChamada = true
                    }

                    Call.State.StreamsRunning -> {
                        //println("SNEP: Áudio estabelecido. Aplicando supressão de eco...")
                        iniciarCronometro()

                        // Reduzimos o ganho do microfone para 0.8 (ligeiramente abaixo do padrão)
                        // Isso diminui a sensibilidade do microfone do PC às caixas de som
                        call.microphoneVolumeGain = 0.8f

                        // Mantemos o speaker em 1.0 ou 1.2
                        call.speakerVolumeGain = 1.0f
                    }
                    Call.State.Released -> {
                        /*println("SNEP: Chamada finalizada.")
                        estaEmChamada = false
                        pararCronometro()
                        duracaoChamada = 0 // Corrige o problema de iniciar a chamada com duracao da anterior*/

                        // Se a chamada que acabou for a do colega (4004)
                        if (call == segundaChamada) {
                            println("SNEP: O colega desligou. Retomando cliente original...")
                            segundaChamada = null
                            // Tira o 4003 do pause automaticamente
                            primeiraChamada?.resume()
                        } else if (call == primeiraChamada) {
                            // Se o cliente original desligar enquanto esperava
                            primeiraChamada = null
                            segundaChamada?.terminate() // Avisa o colega que o cliente caiu
                        }

                        // Lógica global de encerramento que você já tinha
                        if (c.callsNb == 0) {
                            estaEmChamada = false
                            estaTocando = false
                            chamadaAtual = null
                            pararCronometro()
                        }
                    }
                    else -> {}


                }
            }
        })

        core.start()

        Timer().schedule(object : TimerTask() {
            override fun run() { core.iterate() }
        }, 0, 20)
    }

    fun registrarRamal(usuario: String, senha: String, servidor: String) {
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

        // --- O PONTO CHAVE: Vincular a política de NAT (STUN) e configurar o tempo de registro ---
        params.natPolicy = core.natPolicy
        params.expires = 3600 // Aumenta o tempo de expiração para manter o registro estável

        val account = core.createAccount(params)
        core.addAuthInfo(authInfo)
        core.addAccount(account)
        core.defaultAccount = account
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
    }

    fun aceitarChamada() {
        val params = core.createCallParams(chamadaAtual)
        chamadaAtual?.acceptWithParams(params)
    }

    fun rejeitarChamada() {
        chamadaAtual?.terminate()
    }

    fun iniciarCronometro() {
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

    // Desenvolvendo funcionalidade da transferencia
    private var primeiraChamada: Call? = null
    private var segundaChamada: Call? = null

    fun iniciarTransferenciaAssistida(numeroDestino: String) {
        primeiraChamada = core.currentCall

        // montando o endereço do ramal da transferencia
        val dominio = core.defaultAccount?.params?.serverAddress?.domain

        // montando uri completa
        val uriCompleta = "sip:${numeroDestino}@${dominio}"
        // 1. Tenta interpretar o numero de destino
        val enderecoDestino = factory.createAddress(uriCompleta)
        // 2. Cria os paramatros de chamada (Pode retornar nulo. Entao precisa tratar)
        val params = core.createCallParams(null)

        // Verificamos se ambos sao nulos antes de prosseguir
        if (enderecoDestino != null && params != null) {
            // 3. Coloca o cliente atual em espera
            primeiraChamada?.pause()
            // 4. Inicia a chamada com o usuario que ira receber a transferencia
            segundaChamada = core.inviteAddressWithParams(enderecoDestino, params)
            println("SNEP: Iniciando segunda perna da transferência para $uriCompleta")
        } else {
            // Opcional: Log ou Toast informando que o ramal é inválido
            println("Erro: Endereço ($enderecoDestino) ou Parâmetros ($params) nulos.")
        }
    }

    fun concluirTransferencia() {
        val call1 = primeiraChamada
        val call2 = segundaChamada

        if (call1 != null && call2 != null) {
            // Log para debug: Ver se os estados aparecem no logcat
            println("Estado Call1: ${call1.state} | Estado Call2: ${call2.state}")

            // segunda chamada seja atendida antes de transferir.
            if (call2.state == Call.State.StreamsRunning || call2.state == Call.State.Connected) {
                call1.transferToAnother(call2)

                // Limpa as referencias
                segundaChamada = null
                primeiraChamada = null
                estaEmChamada = false
                pararCronometro()
            } else {
                println("O colega ainda não atendeu (Estado): ${call2.state}")
            }

            // Une a chamada que está em espera com a chamada atual
            //primeiraChamada?.transferToAnother(segundaChamada!!)

        }
    }

    fun cancelarTransferencia() {
        // Se o colega nao atender ou nao puder falar, encerramos a segunda e voltamos para primeira
        segundaChamada?.terminate()
        primeiraChamada?.resume()

        segundaChamada = null
        //primeiraChamada = null
    }
}