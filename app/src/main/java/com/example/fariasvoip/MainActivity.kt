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
//import com.example.fariassip.ui.theme.FariasSIPTheme

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import com.example.fariasvoip.ui.theme.FariasVOIPTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Inicializa o banco de dados
        val db = AppDatabase.getDatabase(this)

        // cria o viewModel
        val viewModel = ContatoViewModel(db.contatoDao())

        // Inicializa o motor VOIP passando 'this' (O contexto do activity)
        SipManager.setup(this)
        enableEdgeToEdge()

        // Solicitação de permissão de áudio em tempo de execução
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                // Opcional: Mostrar um aviso que sem microfone não haverá áudio
            }
        }
        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)

        setContent {
            FariasVOIPTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val status = SipManager.statusRegistro

                    // logica da navegacao
                    if (status == "Conectado ao SNEP!") {
                        // Se estiver logado consegue enxergar
                        MainHost(viewModel)
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
fun MainHost(viewModel: ContatoViewModel) {
    // Estado que guarda qual aba do Enum 'Telas' esta ativa
    var telaAtual by remember { mutableStateOf(Telas.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Percorremos o nosso Enum para criar os botoes dinamicamente
                Telas.values().forEach { tela ->
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
                Telas.Contatos -> TelaContatos(viewModel)
            }
        }

    }
}

@Composable
fun TelaBoasVindas() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bem-vindo ao Farias SIP",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Gray
        )
        Text(
            text = "USUARIO AQUI", // Nome que pegamos do SNEP
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Selecione uma opção abaixo para começar",
            style = MaterialTheme.typography.bodyMedium
        )
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
                    OutlinedTextField(value = ramalNovo, onValueChange = { ramalNovo = it }, label = { Text("Ramal") })
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
    var ramal by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var dominio by remember { mutableStateOf("192.168.10.1:5050") }
    var expandirDominios by remember { mutableStateOf(false) }

    val status = SipManager.statusRegistro

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Farias SIP", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(text = "Status: $status", color = if (status.contains("Erro")) Color.Red else MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(32.dp))

        // Campo de Texto para o Ramal
        OutlinedTextField(
            value = ramal,
            onValueChange = { ramal = it },
            label = { Text("Número do Ramal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Campo de Texto para a Senha
        OutlinedTextField(
            value = senha,
            onValueChange = { senha = it },
            label = { Text("Senha do Ramal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
                    text = { Text("Externo (voip.hostcg.com.br)") },
                    onClick = {
                        dominio = "voip.hostcg.com.br"
                        expandirDominios = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                SipManager.registrarRamal(ramal, senha, dominio)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("ENTRAR")
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
        //val emProcesso = SipManager.mostrarInterfaceTransferencia
        var numeroDestinoTransferencia by remember { mutableStateOf("") }

        // Um Box que cobre a tela com um fundo semi-transparente
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                Spacer(modifier = Modifier.height(20.dp))

                // Cronometro formatado
                val minutos = SipManager.duracaoChamada / 60
                val segundos = SipManager.duracaoChamada % 60
                Text(
                    text = String.format("%02d:%02d", minutos, segundos),
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium
                )

                Spacer(modifier = Modifier.height(50.dp))

                if (!emProcessoDeTransferencia) {
                    // Botao inicial de transferencia
                    Button(onClick = { emProcessoDeTransferencia = true}) {
                        Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null)
                        Text("Transferir")
                    }
                } else {
                    if (emProcessoDeTransferencia) {
                        Text(
                            text = "Chamando colega...",
                            color = Color.Yellow,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Interface de transferencia ativa
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedTextField(
                            value = numeroDestinoTransferencia,
                            onValueChange = { numeroDestinoTransferencia = it },
                            label = { Text("Ramal de Destino", color = Color.White) },
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Gray)
                        )

                        Row(Modifier.padding(top = 16.dp)) {
                            // Botao ligar para colega
                            Button(onClick = {
                                SipManager.iniciarTransferenciaAssistida(numeroDestinoTransferencia)
                            }) {
                                Text("Ligar para colega")
                            }
                        }

                        Row(Modifier.padding(top = 8.dp)) {
                            // Botao concluir (Une as duas chamadas)
                            Button(
                                onClick = {
                                    SipManager.concluirTransferencia()
                                    emProcessoDeTransferencia = false
                                },
                            ) {
                                Text("Unir e sair")
                            }

                            Spacer(Modifier.width(8.dp))

                            // Botao Cancelar (Volta para o cliente)
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
                }

                // Botão de encerrar ligacao
                Button(
                    onClick = { SipManager.encerrarChamada() },
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