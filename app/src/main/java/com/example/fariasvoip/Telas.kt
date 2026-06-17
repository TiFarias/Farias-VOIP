package com.example.fariasvoip

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

enum class Telas(val titulo: String, val icone: ImageVector) {
    Dashboard("Início", Icons.Default.Home),
    Discador("Telefone", Icons.Default.Call),
    Contatos("Contatos", Icons.Default.Person)
}