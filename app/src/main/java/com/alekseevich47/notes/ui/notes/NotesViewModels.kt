package com.alekseevich47.notes.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.TextMark
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

    fun deleteMany(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.delete(it) }
        }
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
        // Activity-scoped: leaving and opening the same note must ask for the PIN again.
        _unlocked.value = false
        if (boundId == id && _note.value != null) return
        boundId = id
        viewModelScope.launch {
            _note.value = repository.get(id)
        }
    }

    fun setTitle(title: String) {
        val current = _note.value ?: return
        if (current.title == title) return
        _note.value = current.copy(title = title, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    fun setDocument(document: NoteDocument) {
        val current = _note.value ?: return
        if (current.document == document) return
        _note.value = current.copy(document = document, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    fun setBlockText(index: Int, text: String, marks: List<TextMark>) {
        val current = _note.value ?: return
        val blocks = NoteDocuments.flow(current.document).toMutableList()
        val block = blocks.getOrNull(index) ?: return
        if (block.kind != BlockKind.TEXT) return
        if (block.text == text && block.marks == marks) return
        blocks[index] = block.copy(text = text, marks = marks)
        setDocument(NoteDocuments.pack(blocks))
    }

    fun updateTable(table: NoteTable) {
        val current = _note.value ?: return
        val blocks = NoteDocuments.flow(current.document).map { block ->
            if (block.table?.id == table.id) block.copy(table = table) else block
        }
        setDocument(NoteDocuments.pack(blocks))
    }

    fun removeTable(id: String) {
        val current = _note.value ?: return
        setDocument(NoteDocuments.withoutTable(current.document, id))
    }

    fun addTable(rows: Int, cols: Int, blockIndex: Int, cursor: Int) {
        val current = _note.value ?: return
        val tableId = "t-${System.currentTimeMillis()}"
        setDocument(NoteDocuments.insertTable(current.document, blockIndex, cursor, rows, cols, tableId))
    }

    fun unlock(pin: String): PinResult {
        return when (EditorPins.check(pin, settings.pinHash.value)) {
            EditorPinCheck.OK -> {
                _unlocked.value = true
                PinResult.OK
            }
            EditorPinCheck.WRONG -> PinResult.WRONG
            EditorPinCheck.MISSING -> PinResult.MISSING
        }
    }

    /**
     * Create-PIN step used when hidden text is opened before a PIN exists.
     * 4757 is stored like any other four digits. Reset stays in Settings.
     */
    fun saveCreatedPin(first: String, second: String): PinSetup {
        if (first.length != 4 || first != second || first.any { it !in '0'..'9' }) return PinSetup.MISMATCH
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
        _unlocked.value = false
        saveJob?.cancel()
        viewModelScope.launch {
            val snapshot = _note.value
            if (snapshot != null) {
                if (isPristine(snapshot)) repository.delete(snapshot.id) else repository.save(snapshot)
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
        if (isPristine(snapshot)) repository.delete(snapshot.id)
    }

    private fun isPristine(snapshot: Note): Boolean {
        if (snapshot.audioPath != null) return false
        if (snapshot.title != "Заметка #${snapshot.seq}") return false
        val blocks = NoteDocuments.flow(snapshot.document)
        val noTables = blocks.none { it.kind == BlockKind.TABLE }
        val noText = blocks.all { block ->
            block.kind != BlockKind.TEXT || (block.text.isBlank() && block.marks.isEmpty())
        }
        return noTables && noText
    }

    enum class PinResult { OK, WRONG, RESET, MISSING }

    enum class PinSetup { SAVED, MISMATCH, RESET }
}
