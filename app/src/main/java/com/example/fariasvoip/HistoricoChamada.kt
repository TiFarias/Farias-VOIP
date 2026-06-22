package com.example.fariasvoip

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "historico_chamadas")
data class HistoricoChamada(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ramal: String,
    val nome: String?, // Nome do contato se existir
    val tipo: TipoChamada, // ENTRADA, SAIDA, PERDIDA
    val dataHora: Long, // Timestamp
    val duracao: Int, // Segundos
    val foiAtendida: Boolean
)

enum class TipoChamada {
    ENTRADA, SAIDA, PERDIDA
}
