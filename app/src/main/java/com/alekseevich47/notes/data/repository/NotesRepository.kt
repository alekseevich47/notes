package com.alekseevich47.notes.data.repository

import com.alekseevich47.notes.data.local.NoteDao
import com.alekseevich47.notes.data.local.toDomain
import com.alekseevich47.notes.data.local.toEntity
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.AppSettings
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val settingsStore: SecureSettingsStore,
) {
    val settings: StateFlow<AppSettings> = settingsStore.settings

    fun observeNotes(query: String, fromMs: Long?, toMs: Long?): Flow<List<Note>> =
        noteDao.observeFiltered(query.trim(), fromMs, toMs).map { list -> list.map { it.toDomain() } }

    fun observeNote(id: Long): Flow<Note?> =
        noteDao.observeById(id).map { it?.toDomain() }

    suspend fun getNote(id: Long): Note? = noteDao.getById(id)?.toDomain()

    suspend fun nextNoteNumber(): Int = noteDao.maxNoteNumber() + 1

    suspend fun createNote(
        title: String,
        body: NoteBody,
        audioPath: String? = null,
        noteNumber: Int? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val number = noteNumber ?: nextNoteNumber()
        return noteDao.insert(
            Note(
                title = title.ifBlank { "Заметка #$number" },
                body = body,
                audioPath = audioPath,
                createdAt = now,
                updatedAt = now,
                noteNumber = number,
            ).toEntity(),
        )
    }

    suspend fun updateNote(note: Note) {
        noteDao.update(note.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteNote(id: Long) = noteDao.delete(id)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) =
        settingsStore.update(transform)
}
