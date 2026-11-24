package com.alekseevich47.notes.ui.notes

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.notes.data.repository.NotesRepository
import com.alekseevich47.notes.data.secure.PinHasher
import com.alekseevich47.notes.data.secure.PinThrottle
import com.alekseevich47.notes.data.secure.SecureSettingsStore
import com.alekseevich47.notes.domain.BlockCaret
import com.alekseevich47.notes.log.AppLog
import com.alekseevich47.notes.domain.BlockKind
import com.alekseevich47.notes.domain.EditHistory
import com.alekseevich47.notes.domain.EditKind
import com.alekseevich47.notes.domain.EditSnapshot
import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteDocument
import com.alekseevich47.notes.domain.NoteDocuments
import com.alekseevich47.notes.domain.NoteListItem
import com.alekseevich47.notes.domain.NoteTable
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.player.NotePlayer
import com.alekseevich47.notes.player.PlayerUi
import com.alekseevich47.notes.ui.components.DayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    val notes: StateFlow<List<NoteListItem>> = combine(query.debounce(80), range) { q, dates ->
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
        viewModelScope.launch { repository.deleteMany(ids) }
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
    private val _unreadable = MutableStateFlow(false)
    val unreadable: StateFlow<Boolean> = _unreadable.asStateFlow()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    val playerUi: StateFlow<PlayerUi> = player.state

    private var boundId: Long = -1
    private var debounceJob: kotlinx.coroutines.Job? = null
    private var loadJob: kotlinx.coroutines.Job? = null
    private var lastSaved: Note? = null
    private var editGen = 0L
    private var savedGen = 0L
    private val saveMutex = Mutex()
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var restoringHistory = false
    private var breakCoalesce = false
    private var liveCaret: BlockCaret? = null
    private var caretToken = 0
    private val _caretJump = MutableStateFlow<CaretJump?>(null)
    val caretJump: StateFlow<CaretJump?> = _caretJump.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun bind(id: Long) {
        // Activity-scoped: leaving and opening the same note must ask for the PIN again.
        _unlocked.value = false
        if (boundId == id && _note.value?.id == id) return
        boundId = id
        undoStack.clear()
        redoStack.clear()
        breakCoalesce = true
        liveCaret = null
        _caretJump.value = null
        refreshHistory()
        _unreadable.value = false
        // Drop the previous note before the load so the editor cannot paint it.
        if (_note.value?.id != id) _note.value = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loaded = repository.get(id)
            if (boundId != id) return@launch
            _note.value = loaded?.note
            _unreadable.value = loaded?.unreadable == true
            if (boundId == id) {
                lastSaved = loaded?.note
                savedGen = editGen
                AppLog.i("Editor", "editor open id=$id unreadable=${_unreadable.value}")
            }
        }
    }

    fun setTitle(title: String) {
        if (_unreadable.value) return
        val current = _note.value ?: return
        if (current.title == title) return
        rememberEdit(current, EditKind.TYPING, EditHistory.inserted(current.title, title), caret = null)
        _note.value = current.copy(title = title, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    internal fun setDocument(
        document: NoteDocument,
        kind: EditKind = EditKind.STRUCTURE,
        insertion: String = "",
        caret: BlockCaret? = null,
    ) {
        if (_unreadable.value) return
        val current = _note.value ?: return
        if (current.document == document) return
        rememberEdit(current, kind, insertion, caret)
        _note.value = current.copy(document = document, updatedAt = System.currentTimeMillis())
        scheduleSave()
    }

    fun undo() {
        if (_unreadable.value) return
        restore(undoStack, redoStack)
    }

    fun redo() {
        if (_unreadable.value) return
        restore(redoStack, undoStack)
    }

    internal fun setBlockText(
        index: Int,
        text: String,
        marks: List<TextMark>,
        kind: EditKind = EditKind.TYPING,
        caret: BlockCaret? = null,
        caretAfter: BlockCaret? = null,
    ) {
        if (_unreadable.value) return
        val current = _note.value ?: return
        val blocks = NoteDocuments.flow(current.document).toMutableList()
        val block = blocks.getOrNull(index) ?: return
        if (block.kind != BlockKind.TEXT) return
        if (block.text == text && block.marks == marks) return
        val insertion = EditHistory.inserted(block.text, text)
        blocks[index] = block.copy(text = text, marks = marks)
        if (caretAfter != null) liveCaret = caretAfter
        setDocument(NoteDocuments.pack(blocks), kind, insertion, caret)
    }

    fun updateTable(table: NoteTable, caretBefore: BlockCaret? = null, caretAfter: BlockCaret? = null) {
        val current = _note.value ?: return
        val blocks = NoteDocuments.flow(current.document).map { block ->
            if (block.table?.id == table.id) block.copy(table = table) else block
        }
        if (caretAfter != null) liveCaret = caretAfter
        setDocument(NoteDocuments.pack(blocks), caret = caretBefore)
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
        val now = SystemClock.elapsedRealtime()
        val throttle = settings.loadPinThrottle(now)
        if (PinThrottle.isLocked(throttle, now)) return PinResult.LOCKED
        return when (EditorPins.check(pin, settings.pinHash.value)) {
            EditorPinCheck.OK -> {
                settings.savePinThrottle(PinThrottle.recordCorrect(throttle))
                _unlocked.value = true
                PinResult.OK
            }
            EditorPinCheck.WRONG -> {
                settings.savePinThrottle(PinThrottle.recordWrong(throttle, now))
                PinResult.WRONG
            }
            EditorPinCheck.MISSING -> PinResult.MISSING
        }
    }

    fun pinLockDeadline(): Long {
        val now = SystemClock.elapsedRealtime()
        return settings.loadPinThrottle(now).lockedUntilElapsed
    }

    /**
     * Create-PIN step used when hidden text is opened before a PIN exists.
     * Any four digits are stored the same way. Reset stays in Settings.
     */
    fun saveCreatedPin(first: String, second: String): PinSetup {
        if (first.length != 4 || first != second || first.any { it !in '0'..'9' }) return PinSetup.MISMATCH
        settings.setPinHash(PinHasher.hash(first))
        _unlocked.value = true
        return PinSetup.SAVED
    }

    fun hasPin(): Boolean = settings.hasPin

    private fun rememberEdit(
        current: Note,
        kind: EditKind,
        insertion: String,
        caret: BlockCaret?,
    ) {
        if (restoringHistory) return
        val next = EditHistory.record(
            undo = undoStack.toList(),
            title = current.title,
            document = current.document,
            kind = kind,
            atMs = System.currentTimeMillis(),
            insertion = insertion,
            caret = caret,
            forceNew = breakCoalesce,
        )
        breakCoalesce = false
        undoStack.clear()
        undoStack.addAll(next)
        redoStack.clear()
        refreshHistory()
    }

    private fun restore(from: ArrayDeque<EditSnapshot>, onto: ArrayDeque<EditSnapshot>) {
        val current = _note.value ?: return
        val snapshot = from.removeLastOrNull() ?: return
        onto.addLast(
            EditSnapshot(
                title = current.title,
                document = current.document,
                kind = EditKind.STRUCTURE,
                atMs = System.currentTimeMillis(),
                caret = liveCaret,
            ),
        )
        restoringHistory = true
        _note.value = current.copy(
            title = snapshot.title,
            document = snapshot.document,
            updatedAt = System.currentTimeMillis(),
        )
        liveCaret = snapshot.caret
        if (snapshot.caret != null) {
            caretToken += 1
            _caretJump.value = CaretJump(caretToken, snapshot.caret)
        }
        restoringHistory = false
        breakCoalesce = true
        refreshHistory()
        scheduleSave()
    }

    private fun refreshHistory() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleSave() {
        if (_unreadable.value) return
        val snapshot = _note.value ?: return
        editGen += 1
        val generation = editGen
        debounceJob?.cancel()
        // Delay only. A newer keystroke cancels the wait and must not cancel a write already in flight.
        debounceJob = viewModelScope.launch {
            delay(280)
            viewModelScope.launch { saveIfNeeded(snapshot, generation) }
        }
    }

    fun flush() {
        AppLog.i("Editor", "flush on stop")
        debounceJob?.cancel()
        debounceJob = null
        if (_unreadable.value) return
        val snapshot = _note.value ?: return
        val generation = editGen
        viewModelScope.launch { saveIfNeeded(snapshot, generation) }
    }

    fun close(onDone: () -> Unit) {
        AppLog.i("Editor", "editor close id=${_note.value?.id ?: boundId}")
        _unlocked.value = false
        debounceJob?.cancel()
        debounceJob = null
        val snapshot = _note.value
        val closingId = snapshot?.id
        val blocked = _unreadable.value
        val generation = editGen
        viewModelScope.launch {
            if (snapshot != null && !blocked) {
                if (isPristine(snapshot)) {
                    saveMutex.withLock { repository.delete(snapshot.id) }
                } else {
                    saveIfNeeded(snapshot, generation)
                }
            }
            releaseIfCurrent(closingId)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        debounceJob?.cancel()
        debounceJob = null
        val id = _note.value?.id
        viewModelScope.launch {
            if (id != null) {
                player.stop()
                saveMutex.withLock { repository.delete(id) }
            }
            releaseIfCurrent(id)
            onDone()
        }
    }

    private suspend fun saveIfNeeded(snapshot: Note, generation: Long) {
        saveMutex.withLock {
            if (_unreadable.value) return@withLock
            if (generation < savedGen) return@withLock
            if (snapshot == lastSaved) return@withLock
            try {
                repository.save(snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.e("Editor", "save failed id=${snapshot.id}", error)
                throw error
            }
            if (snapshot.id == boundId && generation >= savedGen) {
                lastSaved = snapshot
                savedGen = generation
            }
        }
    }

    private fun releaseIfCurrent(id: Long?) {
        if (id != null && boundId != id) return
        loadJob?.cancel()
        _note.value = null
        _unreadable.value = false
        boundId = -1
        undoStack.clear()
        redoStack.clear()
        breakCoalesce = true
        liveCaret = null
        refreshHistory()
    }

    suspend fun abandonIfEmpty() {
        if (_unreadable.value) return
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

    data class CaretJump(val token: Int, val caret: BlockCaret)

    enum class PinResult { OK, WRONG, RESET, MISSING, LOCKED }

    enum class PinSetup { SAVED, MISMATCH, RESET }
}
