package com.alekseevich47.notes.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.NoteTables
import com.alekseevich47.notes.player.NotePlayer
import com.alekseevich47.notes.player.PlayerUi
import com.alekseevich47.notes.ui.components.DayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val range = MutableStateFlow<DayRange?>(null)

    val search: StateFlow<String> = query.asStateFlow()
    val dateRange: StateFlow<DayRange?> = range.asStateFlow()

    val notes: StateFlow<List<Note>> = combine(query.debounce(80), range) { q, dates ->
        val zone = ZoneId.systemDefault()
        val from = dates?.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val to = dates?.end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        Triple(q, from, to)
    }.flatMapLatest { (q, from, to) ->
        repository.observe(q, from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setRange(value: DayRange?) {
        range.value = value
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: NotesRepository,
    private val settings: SecureSettingsStore,
    val player: NotePlayer,
) : ViewModel() {
    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    val playerUi: StateFlow<PlayerUi> = player.state

    private var boundId: Long = -1
    private var saveJob: kotlinx.coroutines.Job? = null

    fun bind(id: Long) {
        if (boundId == id && _note.value != null) return
        boundId = id
        _unlocked.value = false
        viewModelScope.launch {
            _note.value = repository.get(id)
        }
    }

    fun setTitle(title: String) {
        val current = _note.value ?: return
        _note.value = current.copy(title = title)
        scheduleSave()
    }

    fun setDocument(document: NoteDocument) {
        val current = _note.value ?: return
        _note.value = current.copy(document = document)
        scheduleSave()
    }

    fun updateTable(table: NoteTable) {
        val current = _note.value ?: return
        val tables = current.document.tables.map { if (it.id == table.id) table else it }
        setDocument(current.document.copy(tables = tables))
    }

    fun setTrailing(trailing: String) {
        val current = _note.value ?: return
        if (current.document.tables.isEmpty()) return
        setDocument(current.document.copy(trailing = trailing))
    }

    fun removeTable(id: String) {
        val current = _note.value ?: return
        setDocument(NoteDocuments.withoutTable(current.document, id))
    }

    fun addTable(rows: Int, cols: Int) {
        val current = _note.value ?: return
        val table = NoteTable(
            id = "t-${System.currentTimeMillis()}",
            cells = NoteTables.blank(rows, cols),
        )
        setDocument(current.document.copy(tables = current.document.tables + table))
    }

    fun unlock(pin: String): PinResult {
        if (PinHasher.isServiceReset(pin)) {
            settings.setPinHash(null)
            _unlocked.value = true
            return PinResult.RESET
        }
        val hash = settings.pinHash.value
        if (hash.isNullOrBlank()) return PinResult.MISSING
        if (PinHasher.matches(pin, hash)) {
            _unlocked.value = true
            return PinResult.OK
        }
        return PinResult.WRONG
    }

    /**
     * Create-PIN step used when hidden text is opened before a PIN exists.
     * Both entries are checked; a service-reset code clears storage and unlocks.
     */
    fun saveCreatedPin(first: String, second: String): PinSetup {
        if (PinHasher.isServiceReset(first) || PinHasher.isServiceReset(second)) {
            settings.setPinHash(null)
            _unlocked.value = true
            return PinSetup.RESET
        }
        if (first.length != 4 || first != second) return PinSetup.MISMATCH
        settings.setPinHash(PinHasher.hash(first))
        _unlocked.value = true
        return PinSetup.SAVED
    }

    fun hasPin(): Boolean = settings.hasPin

    private fun scheduleSave() {
        val snapshot = _note.value ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(280)
            repository.save(snapshot)
        }
    }

    fun flush() {
        val snapshot = _note.value ?: return
        viewModelScope.launch { repository.save(snapshot) }
    }

    fun close(onDone: () -> Unit) {
        viewModelScope.launch {
            val snapshot = _note.value
            if (snapshot != null) {
                val doc = snapshot.document
                val pristine = snapshot.audioPath == null &&
                    doc.text.isBlank() &&
                    doc.tables.isEmpty() &&
                    doc.marks.isEmpty() &&
                    snapshot.title == "Заметка #${snapshot.seq}"
                if (pristine) repository.delete(snapshot.id) else repository.save(snapshot)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _note.value?.id
        viewModelScope.launch {
            if (id != null) {
                player.stop()
                repository.delete(id)
            }
            onDone()
        }
    }

    suspend fun abandonIfEmpty() {
        val snapshot = _note.value ?: return
        val doc = snapshot.document
        val pristine = snapshot.audioPath == null &&
            doc.text.isBlank() &&
            doc.tables.isEmpty() &&
            doc.marks.isEmpty() &&
            snapshot.title == "Заметка #${snapshot.seq}"
        if (pristine) repository.delete(snapshot.id)
    }

    enum class PinResult { OK, WRONG, RESET, MISSING }

    enum class PinSetup { SAVED, MISMATCH, RESET }
}
