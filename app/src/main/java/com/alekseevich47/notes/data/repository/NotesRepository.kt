package com.alekseevich47.notes.data.repository

import android.content.Context
import com.alekseevich47.notes.data.local.NoteDao
import com.alekseevich47.notes.data.local.NoteEntity
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotesRepository @Inject constructor(
    private val dao: NoteDao,
    @ApplicationContext private val context: Context,
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
        if (
            NoteDocuments.sameStored(
                storedTitle = current.title,
                storedBody = current.body,
                storedAudio = current.audioPath,
                title = note.title,
                document = note.document,
                audioPath = note.audioPath,
            )
        ) {
            return
        }
        val updated = current.copy(
            title = note.title,
            body = NoteDocuments.encode(note.document),
            searchBlob = NoteDocuments.searchBlob(note.title, note.document),
            audioPath = note.audioPath,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
    }

    /**
     * Rewrites searchBlob once so hidden spans indexed by older builds drop out.
     * The prefs flag is set only after every row is updated.
     */
    suspend fun reindexSearchIfNeeded() {
        withContext(Dispatchers.IO) {
            val prefs = NotesPrefs.open(context)
            if (prefs.getBoolean(NotesPrefs.SEARCH_INDEX_V2, false)) return@withContext
            try {
                val rows = dao.all()
                for (row in rows) {
                    val blob = NoteDocuments.searchBlob(row.title, NoteDocuments.decode(row.body))
                    dao.updateSearchBlob(row.id, blob)
                }
                prefs.edit().putBoolean(NotesPrefs.SEARCH_INDEX_V2, true).commit()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e(TAG, "search reindex failed", error)
            }
        }
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

    private companion object {
        const val TAG = "Notes"
    }
}
