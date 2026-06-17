package com.example.fariasvoip

import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Este é o cara que estava faltando
import kotlinx.coroutines.launch

class ContatoViewModel(private val dao: ContatoDao) : ViewModel() {
    val todosContatos: Flow<List<Contato>> = dao.listarTodos()

    fun adicionarContato(nome: String, ramal: String) {
        viewModelScope.launch {
            dao.inserir(Contato(nome = nome, ramal = ramal))
        }
    }

    fun excluirContato(contato: Contato) {
        viewModelScope.launch {
            dao.deletar(contato)
        }
    }

    fun importarLista(contatos: List<Contato>) {
        viewModelScope.launch {
            contatos.forEach { dao.inserir(it.copy(id = 0)) }
        }
    }
}