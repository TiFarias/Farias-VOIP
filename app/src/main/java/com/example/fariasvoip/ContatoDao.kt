package com.example.fariasvoip

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ContatoDao {

    @Query("SELECT * FROM contatos ORDER BY nome ASC")
    fun listarTodos(): Flow<List<Contato>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(contato: Contato)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserirVarios(contatos: List<Contato>)

    @Delete
    suspend fun deletar(contato: Contato)
}

/*
@Database(entities = [Contato::class], version = 1)
abstract class ContatoDatabase : RoomDatabase() {
    abstract fun contatoDao(): ContatoDao

    companion object{
        @Volatile
        private var INSTANCE: ContatoDatabase? = null

        fun getDatabase(context: Context): ContatoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ContatoDatabase::class.java,
                    "agenda_snep_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}*/