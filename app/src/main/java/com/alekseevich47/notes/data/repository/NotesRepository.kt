package com.alekseevich47.notes.data.repository

import android.content.Context
import android.util.LruCache
import com.alekseevich47.notes.data.local.NoteDao
import com.alekseevich47.notes.data.local.NoteEntity
import com.alekseevich47.notes.data.secure.NotesPrefs
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteListItem
import com.alekseevich47.notes.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    private val listItems = LruCache<Long, Pair<Long, NoteListItem>>(256)

    fun observe(query: String, fromMs: Long?, toMs: Long?): Flow<List<NoteListItem>> {
        val needle = escapeSearchNeedle(query.trim().lowercase(Locale.getDefault()))
        return dao.observe(fromMs, toMs, needle)
            .map { rows -> rows.map { it.toListItem() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    suspend fun get(id: Long): LoadedNote? {
        val row = dao.get(id) ?: return null
        val decoded = NoteDocuments.read(row.body)
        if (decoded.unreadable) AppLog.e(TAG, "note json unreadable")
        return LoadedNote(row.toDomain(decoded.document), decoded.unreadable)
    }

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
        val updated = withContext(Dispatchers.Default) {
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
                null
            } else {
                current.copy(
                    title = note.title,
                    body = NoteDocuments.encode(note.document),
                    searchBlob = NoteDocuments.searchBlob(note.title, note.document),
                    audioPath = note.audioPath,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        } ?: return
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

    private fun NoteEntity.toListItem(): NoteListItem {
        val hit = listItems.get(id)
        if (hit != null && hit.first == updatedAt) return hit.second
        val item = NoteListItem(
            id = id,
            title = title,
            preview = NoteDocuments.previewParts(NoteDocuments.decode(body)),
            updatedAt = updatedAt,
            hasAudio = audioPath != null,
        )
        listItems.put(id, updatedAt to item)
        return item
    }

    private fun NoteEntity.toDomain(document: NoteDocument = NoteDocuments.decode(body)): Note = Note(
        id = id,
        title = title,
        document = document,
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

data class LoadedNote(
    val note: Note,
    val unreadable: Boolean,
)

/** LIKE metacharacters, backslash first so an escaped `%` is not escaped again. */
internal fun escapeSearchNeedle(needle: String): String {
    if (needle.isEmpty()) return needle
    val out = StringBuilder(needle.length)
    for (ch in needle) {
        if (ch == '\\' || ch == '%' || ch == '_') out.append('\\')
        out.append(ch)
    }
    return out.toString()
}
