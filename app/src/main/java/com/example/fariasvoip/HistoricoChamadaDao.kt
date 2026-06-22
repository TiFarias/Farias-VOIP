package com.example.fariasvoip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoricoChamadaDao {
    @Query("SELECT * FROM historico_chamadas ORDER BY dataHora DESC")
    fun listarTodos(): Flow<List<HistoricoChamada>>

    @Insert
    suspend fun inserir(chamada: HistoricoChamada)

    @Query("DELETE FROM historico_chamadas")
    suspend fun limparHistorico()
}
