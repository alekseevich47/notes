package com.alekseevich47.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE (:fromMs IS NULL OR createdAt >= :fromMs)
          AND (:toMs IS NULL OR createdAt < :toMs)
          AND (:needle = '' OR searchBlob LIKE '%' || :needle || '%' ESCAPE '\')
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observe(fromMs: Long?, toMs: Long?, needle: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(entity: NoteEntity): Long

    @Update
    suspend fun update(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(seq), 0) FROM notes")
    suspend fun maxSeq(): Int

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("UPDATE notes SET searchBlob = :searchBlob WHERE id = :id")
    suspend fun updateSearchBlob(id: Long, searchBlob: String)
}
