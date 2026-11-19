package com.goldcalculateur.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

const val HISTORY_LIMIT = 50

/**
 * One finished calculation. Language-neutral: only inputs (WorkStateCodec text) and the key numbers.
 * Re-opening an entry restores the inputs and recomputes the result with the exact engine.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val mode: String,
    val inputs: String,
    /** Practical weight to add or to cut, plain text (e.g. "26.667"). */
    val resultWeight: String,
    /** Final karat, plain text (e.g. "748.000"). */
    val finalKarat: String,
)

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC, id DESC")
    abstract fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    abstract suspend fun get(id: Long): HistoryEntity?

    @Insert
    abstract suspend fun insert(entity: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY createdAt DESC, id DESC LIMIT $HISTORY_LIMIT)")
    abstract suspend fun trim()

    @Query("DELETE FROM history WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    abstract suspend fun clear()

    /** Insert and keep only the latest [HISTORY_LIMIT] operations. */
    @Transaction
    open suspend fun insertAndTrim(entity: HistoryEntity) {
        insert(entity)
        trim()
    }
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}
