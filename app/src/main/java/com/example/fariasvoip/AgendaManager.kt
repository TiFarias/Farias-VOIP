package com.example.fariasvoip

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File


object AgendaManager {

    // Configuracao do Json para ser 'tolerante'
    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true // Deixa o texto mais bonitin
    }

    // Transforma a lista de contatos em uma String para você mandar para outro usuário
    fun exportarContatosParaJson(lista: List<Contato>): String {
        return jsonConfig.encodeToString<List<Contato>>(lista)
    }

    // Pega o texto que o outro usuário enviou e transforma em lista de objetos
    fun importarContatosDoJson(jsonTexto: String): List<Contato>? {
        return try {
            jsonConfig.decodeFromString<List<Contato>>(jsonTexto)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun compartilharAgenda(context: Context, jsonTexto: String) {
        try {
            // 1. Criar arquivo temporario
            val arquivo = File(context.cacheDir, "contatos_farias_sip.json")
            arquivo.writeText(jsonTexto)

            // 2. Obter a URI do arquivo (Precisa configurar no manifest. Mas vou testar aqui primeiro)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivo
            )

            // 3. Criar a intencao de compartilhamento
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Exportar Contatos"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun processarImportacao(jsonTexto: String, dao: ContatoDao, scope: CoroutineScope) {
        val contatosImportados = importarContatosDoJson(jsonTexto)

        if (contatosImportados != null) {
            scope.launch {
                contatosImportados.forEach { contato ->
                    // O id 0 garante que o Room gere um novo ID e nao sobrescreva
                    val novoContato = contato.copy(id = 0)
                    dao.inserir(contato)
                }
            }
        }
    }
}