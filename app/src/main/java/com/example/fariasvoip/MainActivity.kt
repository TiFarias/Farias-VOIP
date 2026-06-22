package com.example.fariasvoip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import com.example.fariasvoip.ui.theme.FariasVOIPTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation


import android.view.WindowManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.content.Intent

import androidx.compose.material.icons.filled.Call

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurações para acordar a tela e mostrar sobre a lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Solicitar permissão para sobrepor outros apps (necessário para wake-up em segundo plano em alguns aparelhos)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // 1. Inicializa o banco de dados
        val db = AppDatabase.getDatabase(this)

        // cria os viewModels
        val contatoViewModel = ContatoViewModel(db.contatoDao())
        val historicoViewModel = HistoricoViewModel(db.historicoDao())

        // Inicializa o motor VOIP passando 'this' (O contexto do activity)
        SipManager.setup(this)

        // Tenta login automático se houver dados salvos
        val (ramalSalvo, senhaSalva, dominioSalvo) = PrefsManager.buscarCredenciais(this)
        if (ramalSalvo != null && senhaSalva != null && dominioSalvo != null) {
            SipManager.registrarRamal(ramalSalvo, senhaSalva, dominioSalvo)
        }

        enableEdgeToEdge()

        val permissions = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            // Feedback opcional para o usuário se as permissões foram negadas
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        setContent {
            FariasVOIPTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val status = SipManager.statusRegistro

                    // logica da navegacao
                    // Se estiver Conectado OU Conectando, mostramos a MainHost para evitar flickering
                    if (status == "Conectado ao SNEP!" || status == "Conectando...") {
                        MainHost(contatoViewModel, historicoViewModel)
                    } else {
                        LoginScreen()
                    }

                    TabelaDeChamada() // intarface da chamada
                }
            }

        }
    }
}

@Composable
fun MainHost(contatoViewModel: ContatoViewModel, historicoViewModel: HistoricoViewModel) {
    // Estado que guarda qual aba do Enum 'Telas' esta ativa
    var telaAtual by remember { mutableStateOf(Telas.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Percorremos o nosso Enum para criar os botoes dinamicamente
                Telas.entries.forEach { tela ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = tela.icone, contentDescription = null) },
                        label = {Text(tela.titulo) },
                        selected = telaAtual == tela,
                        onClick = { telaAtual = tela }
                    )

                }
            }
        }
    ) { paddingInterno ->
        // O paddingInterno evita que o conteúdo fique "atrás" da barra inferior
        Box(modifier = Modifier.padding(paddingInterno)) {
            when (telaAtual) {
                Telas.Dashboard -> TelaBoasVindas()
                Telas.Discador -> TelaDiscador()
                Telas.Historico -> TelaHistorico(historicoViewModel)
                Telas.Contatos -> TelaContatos(contatoViewModel)
            }
        }

    }
}

@Composable
fun TelaHistorico(viewModel: HistoricoViewModel) {
    val historico by viewModel.todasChamadas.collectAsState(initial = emptyList())
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Histórico", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { viewModel.limparHistorico() }) {
                Icon(Icons.Default.Delete, contentDescription = "Limpar", tint = Color.Gray)
            }
        }

        if (historico.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma chamada registrada", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(historico) { chamada ->
                    val icone = when (chamada.tipo) {
                        TipoChamada.ENTRADA -> Icons.Default.CallReceived
                        TipoChamada.SAIDA -> Icons.Default.CallMade
                        TipoChamada.PERDIDA -> Icons.Default.CallMissed
                    }
                    val corIcone = if (chamada.tipo == TipoChamada.PERDIDA) Color.Red else Color(0xFF2E7D32)

                    ListItem(
                        leadingContent = {
                            Icon(imageVector = icone, contentDescription = null, tint = corIcone)
                        },
                        headlineContent = {
                            Text(text = chamada.nome ?: chamada.ramal, fontWeight = FontWeight.Bold)
                        },
                        supportingContent = {
                            val dataFormatada = sdf.format(Date(chamada.dataHora))
                            val duracaoFormatada = if (chamada.duracao > 0) {
                                " • ${chamada.duracao / 60}m ${chamada.duracao % 60}s"
                            } else ""
                            Text("$dataFormatada$duracaoFormatada")
                        },
                        trailingContent = {
                            IconButton(onClick = { SipManager.fazerLigacao(chamada.ramal) }) {
                                Icon(Icons.Default.Call, contentDescription = "Retornar", tint = Color(0xFF2E7D32))
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun TelaBoasVindas() {
    val context = LocalContext.current
    val vermelhoEmpresa = Color(0xFFD32F2F)

    Box(modifier = Modifier.fillMaxSize()) {
        // Botão de Sair no canto superior direito (ainda menor e discreto)
        IconButton(
            onClick = {
                PrefsManager.limparLogin(context)
                SipManager.deslogar()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp) // Reduzi o padding para ficar mais no canto
                .size(40.dp)   // Tamanho total do botão reduzido
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sair",
                tint = vermelhoEmpresa,
                modifier = Modifier.size(20.dp) // Ícone menor (20dp)
            )
        }

        // Conteúdo Principal Centralizado
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bem-vindo ao Farias VOIP",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Gray
            )
            Text(
                text = "Painel de Ramal Ativo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = vermelhoEmpresa
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Selecione uma opção na barra inferior para começar",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun TelaContatos(viewModel: ContatoViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contatosDB by viewModel.todosContatos.collectAsState(initial = emptyList())

    var exibirDialogoNovoContato by remember { mutableStateOf(false) }
    var nomeNovo by remember { mutableStateOf("") }
    var ramalNovo by remember { mutableStateOf("") }

    // Dialogo da exclusao
    var exibirDialogoExclusao by remember { mutableStateOf(false) }
    // Guarda qual contato foi clicado para ser excluido
    var contatoParaExcluir by remember { mutableStateOf<Contato?>(null) }

    // SELETOR DE ARQUIVO JSON
    val launcherArquivo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() }
            content?.let { json ->
                val lista = AgendaManager.importarContatosDoJson(json)
                if (lista != null) viewModel.importarLista(lista)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { exibirDialogoNovoContato = true }) {
                Icon(Icons.Default.Add, contentDescription = "Novo Contato")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Meus Contatos", style = MaterialTheme.typography.headlineMedium)

                Row {
                    // Botão Importar Arquivo
                    IconButton(onClick = { launcherArquivo.launch(arrayOf("application/json")) }) {
                        Icon(imageVector = Icons.Default.FileOpen, contentDescription = "Importar Arquivo")
                    }
                    // Botão Exportar
                    IconButton(onClick = {
                        val json = AgendaManager.exportarContatosParaJson(contatosDB)
                        AgendaManager.compartilharAgenda(context, json)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar")
                    }
                }
            }

            LazyColumn {
                items(contatosDB) { contato ->
                    ListItem(
                        headlineContent = { Text(contato.nome) },
                        supportingContent = { Text("Ramal: ${contato.ramal}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { SipManager.fazerLigacao(contato.ramal) }) {
                                    Icon(Icons.Default.Call, tint = Color(0xFF2E7D32), contentDescription = "Ligar")
                                }
                                // BOTÃO EXCLUIR
                                IconButton(onClick = {
                                    contatoParaExcluir = contato
                                    exibirDialogoExclusao = true
                                }) {
                                    Icon(Icons.Default.Delete, tint = Color.Red, contentDescription = "Excluir")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // DIÁLOGO PARA ADICIONAR MANUALMENTE
    if (exibirDialogoNovoContato) {
        AlertDialog(
            onDismissRequest = { exibirDialogoNovoContato = false },
            title = { Text("Novo Contato") },
            text = {
                Column {
                    OutlinedTextField(value = nomeNovo, onValueChange = { nomeNovo = it }, label = { Text("Nome") })
                    OutlinedTextField(
                        value = ramalNovo,
                        onValueChange = { ramalNovo = it.filter { char -> char.isDigit() } },
                        label = { Text("Ramal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.adicionarContato(nomeNovo, ramalNovo)
                    exibirDialogoNovoContato = false
                    nomeNovo = ""; ramalNovo = ""
                }) { Text("Salvar") }
            }
        )
    }

    if (exibirDialogoExclusao && contatoParaExcluir != null) {
        AlertDialog(
            onDismissRequest = {
                exibirDialogoExclusao = false
                contatoParaExcluir = null
            },
            title = { Text("Excluir Contato")},
            text = {
                Text("Deseja mesmo excluir o contato de ${contatoParaExcluir?.nome}?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        contatoParaExcluir?.let { viewModel.excluirContato(it) }
                        exibirDialogoExclusao = false
                        contatoParaExcluir = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Excluir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    exibirDialogoExclusao = false
                    contatoParaExcluir = null
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    var ramal by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var dominio by remember { mutableStateOf("192.168.10.1:5050") }
    var expandirDominios by remember { mutableStateOf(false) }

    val status = SipManager.statusRegistro

    // Definição da paleta de cores da empresa para harmonização
    val vermelhoEmpresa = Color(0xFFD32F2F) // Vermelho moderno e corporativo
    val cinzaFundo = Color(0xFFF5F5F5)     // Cinza bem claro para o fundo da tela
    val cinzaTexto = Color(0xFF616161)     // Cinza escuro para textos secundários

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cinzaFundo)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_empresa),
            contentDescription = "Logo da Empresa",
            modifier = Modifier
                .size(140.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "Farias VOIP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = vermelhoEmpresa // Agora o título segue a identidade visual!
        )

        Text(
            text = "Status: $status",
            color = if (status.contains("Erro")) vermelhoEmpresa else cinzaTexto,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo de Texto para o Ramal
        OutlinedTextField(
            value = ramal,
            onValueChange = { ramal = it.filter { char -> char.isDigit() } },
            label = { Text("Número do Ramal") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = vermelhoEmpresa,
                focusedLabelColor = vermelhoEmpresa,
                cursorColor = vermelhoEmpresa
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Campo de Texto para a Senha
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha do Ramal") },
            modifier = Modifier.fillMaxWidth(),
            // A LINHA ABAIXO TRANSFORMA OS CARACTERES EM PONTOS/ASTERISCOS AUTOMATICAMENTE
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = vermelhoEmpresa,
                focusedLabelColor = vermelhoEmpresa,
                cursorColor = vermelhoEmpresa
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Seleção de Domínio
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expandirDominios = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conectar em: $dominio")
            }
            DropdownMenu(
                expanded = expandirDominios,
                onDismissRequest = { expandirDominios = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                DropdownMenuItem(
                    text = { Text("Interno (192.168.10.1:5050)") },
                    onClick = {
                        dominio = "192.168.10.1:5050"
                        expandirDominios = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Externo (voip.hostcg.com.br:5050)") },
                    onClick = {
                        dominio = "voip.hostcg.com.br:5050"
                        expandirDominios = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botão de entrar
        Button(
            onClick = {
                if (ramal.isNotEmpty() && senha.isNotEmpty() && dominio.isNotEmpty()) {
                    PrefsManager.salvarLogin(context, ramal, senha, dominio)
                    SipManager.registrarRamal(ramal, senha, dominio)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = vermelhoEmpresa,
                contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("ENTRAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
@Composable
fun TelaDiscador() {
    var numeroDigitado by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Topo: Status e Visor
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ramal Ativo", style = MaterialTheme.typography.labelLarge)
            //Text(numeroDigitado, style = MaterialTheme.typography.displayMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = numeroDigitado,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.weight(1f, fill = false) // Texto ocupa espaco flexivel
                )

                // Botao de apagar so aparece se houver algo digitado
                if (numeroDigitado.isNotEmpty()) {
                    IconButton(onClick = {
                        numeroDigitado = numeroDigitado.dropLast(1)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Apagar",
                            tint = Color.Gray
                        )
                    }
                }
            }
        }

        // 2. O teclado (Simulacao de grade)
        val botoes = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

        Column {
            botoes.chunked(3).forEach { linha ->
                Row {
                    linha.forEach { digito ->
                        Button(
                            onClick = { numeroDigitado += digito },
                            modifier = Modifier.padding(8.dp).size(80.dp),
                            shape = CircleShape
                        ) {
                            Text(digito, fontSize = 24.sp)
                        }

                    }
                }

            }
        }

        // 3. Botao de ligar
        Button(
            onClick = { SipManager.fazerLigacao(numeroDigitado) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("LIGAR")
        }

    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Surface(color = MaterialTheme.colorScheme.background) {
        LoginScreen()
    }
}

@Composable
fun TabelaDeChamada() {
    if (SipManager.estaTocando) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Recebendo chamada de:", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                Text(SipManager.numeroDestino, color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(64.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Botão Recusar
                    IconButton(
                        onClick = { SipManager.rejeitarChamada() },
                        modifier = Modifier.size(80.dp).background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Recusar", tint = Color.White)
                    }
                    
                    // Botão Atender
                    IconButton(
                        onClick = { SipManager.aceitarChamada() },
                        modifier = Modifier.size(80.dp).background(Color(0xFF2E7D32), CircleShape)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Atender", tint = Color.White)
                    }
                }
            }
        }
    }

    if (SipManager.estaEmChamada) {
        // Variavel para controlar se estamos no meio de uma transferencia
        var emProcessoDeTransferencia by remember { mutableStateOf(false) }
        var emProcessoDeConferencia by remember { mutableStateOf(false) }
        
        var numeroDestinoAcao by remember { mutableStateOf("") }

        // Um Box que cobre a tela com um fundo semi-transparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (SipManager.estaEmConferencia) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(48.dp))
                    Text(
                        text = "Conferência Ativa",
                        color = Color.Cyan,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${SipManager.contagemParticipantes + 1} pessoas na sala",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Em chamada com:",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = SipManager.numeroDestino,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Cronometro formatado
                val minutos = SipManager.duracaoChamada / 60
                val segundos = SipManager.duracaoChamada % 60
                Text(
                    text = String.format("%02d:%02d", minutos, segundos),
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(30.dp))

                // BOTÕES DE ÁUDIO (Mudo e Viva-Voz)
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Botão Mudo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { SipManager.alternarMudo() },
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    if (SipManager.estaMutado) Color.Red else Color.DarkGray,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (SipManager.estaMutado) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mudo",
                                tint = Color.White
                            )
                        }
                        Text("Mudo", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    // Botão Viva-Voz
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { SipManager.alternarVivaVoz() },
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    if (SipManager.estaNoVivaVoz) Color.Cyan else Color.DarkGray,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (SipManager.estaNoVivaVoz) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Viva-Voz",
                                tint = if (SipManager.estaNoVivaVoz) Color.Black else Color.White
                            )
                        }
                        Text("Viva-voz", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (!emProcessoDeTransferencia && !emProcessoDeConferencia) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Botao inicial de transferencia
                        Button(onClick = { emProcessoDeTransferencia = true }) {
                            Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Transferir")
                        }

                        // Botão de Conferência
                        Button(
                            onClick = { emProcessoDeConferencia = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Conferência")
                        }
                    }
                } else if (emProcessoDeTransferencia) {
                    // Interface de transferencia ativa
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = numeroDestinoAcao,
                            onValueChange = { numeroDestinoAcao = it.filter { char -> char.isDigit() } },
                            label = { Text("Ramal de Destino", color = Color.White) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray)
                        )

                        Row(Modifier.padding(top = 16.dp)) {
                            Button(onClick = {
                                SipManager.iniciarTransferenciaAssistida(numeroDestinoAcao)
                            }) {
                                Text("Ligar para colega")
                            }
                        }

                        Row(Modifier.padding(top = 8.dp)) {
                            Button(onClick = {
                                SipManager.concluirTransferencia()
                                emProcessoDeTransferencia = false
                            }) {
                                Text("Unir e sair")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    SipManager.cancelarTransferencia()
                                    emProcessoDeTransferencia = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text("Voltar")
                            }
                        }
                    }
                } else if (emProcessoDeConferencia) {
                    // Interface para ADICIONAR na CONFERÊNCIA
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Adicionar participante (Máx 5)", color = Color.White)
                        OutlinedTextField(
                            value = numeroDestinoAcao,
                            onValueChange = { numeroDestinoAcao = it.filter { char -> char.isDigit() } },
                            label = { Text("Número do Ramal", color = Color.White) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )

                        Row(Modifier.padding(top = 16.dp)) {
                            Button(
                                onClick = {
                                    SipManager.adicionarParticipante(numeroDestinoAcao)
                                    numeroDestinoAcao = ""
                                },
                                enabled = (SipManager.contagemParticipantes < 4)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Adicionar")
                            }

                            Spacer(Modifier.width(8.dp))

                            Button(
                                onClick = { emProcessoDeConferencia = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text("Fechar")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Botão de encerrar ligacao / conferencia
                Button(
                    onClick = {
                        if (SipManager.estaEmConferencia) {
                            SipManager.encerrarConferencia()
                        } else {
                            SipManager.encerrarChamada()
                        }
                        emProcessoDeTransferencia = false
                        emProcessoDeConferencia = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Desligar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}