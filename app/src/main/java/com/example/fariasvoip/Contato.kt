package com.example.fariasvoip

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "contatos")
data class Contato(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val ramal: String
)