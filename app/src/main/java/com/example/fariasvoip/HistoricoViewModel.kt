package com.example.fariasvoip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoricoViewModel(private val dao: HistoricoChamadaDao) : ViewModel() {
    val todasChamadas: Flow<List<HistoricoChamada>> = dao.listarTodos()

    fun limparHistorico() {
        viewModelScope.launch {
            dao.limparHistorico()
        }
    }
}
