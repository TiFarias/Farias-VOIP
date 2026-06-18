package com.example.fariasvoip

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "farias_voip_prefs"
    private const val KEY_RAMAL = "ramal"
    private const val KEY_SENHA = "senha"
    private const val KEY_DOMINIO = "dominio"
    private const val KEY_LOGADO = "esta_logado"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun salvarLogin(context: Context, ramal: String, senha: String, dominio: String) {
        getPrefs(context).edit().apply {
            putString(KEY_RAMAL, ramal)
            putString(KEY_SENHA, senha)
            putString(KEY_DOMINIO, dominio)
            putBoolean(KEY_LOGADO, true)
            apply()
        }
    }

    fun buscarCredenciais(context: Context): Triple<String?, String?, String?> {
        val prefs = getPrefs(context)
        return Triple(
            prefs.getString(KEY_RAMAL, null),
            prefs.getString(KEY_SENHA, null),
            prefs.getString(KEY_DOMINIO, null)
        )
    }

    fun estaLogado(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOGADO, false)
    }

    fun limparLogin(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
