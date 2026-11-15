package com.alekseevich47.notes.data.repository

import com.alekseevich47.notes.data.local.NoteDao
import com.alekseevich47.notes.data.local.NoteEntity
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val dao: NoteDao,
) {
    fun observe(query: String, fromMs: Long?, toMs: Long?): Flow<List<Note>> {
        val needle = query.trim().lowercase(Locale.getDefault())
        return dao.observe(fromMs, toMs).map { rows ->
            rows.asSequence()
                .map { it.toDomain() }
                .filter { needle.isEmpty() || it.searchBlob.contains(needle) }
                .toList()
        }
    }

    suspend fun get(id: Long): Note? = dao.get(id)?.toDomain()

    suspend fun nextSeq(): Int = dao.maxSeq() + 1

    suspend fun create(
        title: String? = null,
        document: NoteDocument = NoteDocument(),
        audioPath: String? = null,
    ): Long {
        val seq = nextSeq()
        val resolved = title?.takeIf { it.isNotBlank() } ?: "Заметка #$seq"
        val now = System.currentTimeMillis()
        return dao.insert(
            NoteEntity(
                title = resolved,
                body = NoteDocuments.encode(document),
                searchBlob = NoteDocuments.searchBlob(resolved, document),
                audioPath = audioPath,
                createdAt = now,
                updatedAt = now,
                seq = seq,
            ),
        )
    }

    suspend fun save(note: Note) {
        val current = dao.get(note.id) ?: return
        val updated = current.copy(
            title = note.title,
            body = NoteDocuments.encode(note.document),
            searchBlob = NoteDocuments.searchBlob(note.title, note.document),
            audioPath = note.audioPath,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
    }

    suspend fun delete(id: Long) {
        val row = dao.get(id)
        row?.audioPath?.let { path -> runCatching { File(path).delete() } }
        dao.delete(id)
    }

    private fun NoteEntity.toDomain(): Note = Note(
        id = id,
        title = title,
        document = NoteDocuments.decode(body),
        audioPath = audioPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        seq = seq,
        searchBlob = searchBlob,
    )
}
