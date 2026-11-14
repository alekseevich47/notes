package com.alekseevich47.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR bodyJson LIKE '%' || :query || '%')
          AND (:fromMs IS NULL OR createdAt >= :fromMs)
          AND (:toMs IS NULL OR createdAt <= :toMs)
        ORDER BY createdAt DESC
        """,
    )
    fun observeFiltered(query: String, fromMs: Long?, toMs: Long?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT COALESCE(MAX(noteNumber), 0) FROM notes")
    suspend fun maxNoteNumber(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NoteEntity): Long

    @Update
    suspend fun update(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}
