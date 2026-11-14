package com.alekseevich47.notes.domain

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class MicVisualState { IDLE, RECORDING, PROCESSING, DONE }

enum class ModelKind(val folder: String, val label: String, val approxMb: Int) {
    T_ONE("t-one", "T-one Streaming (RU)", 140),
    GIGA_AM("gigaam-v3-e2e", "GigaAM v3 e2e (пунктуация)", 230),
}

data class ModelDownloadState(
    val kind: ModelKind,
    val installed: Boolean = false,
    val progress: Float = 0f,
    val downloading: Boolean = false,
    val error: String? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pinHash: String? = null,
    val gigaFinalizeEnabled: Boolean = true,
    val tOneInstalled: Boolean = false,
    val gigaInstalled: Boolean = false,
)

@Serializable
data class RichSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val hidden: Boolean = false,
)

@Serializable
data class TableBlock(
    val id: String,
    val rows: Int,
    val cols: Int,
    val cells: List<List<String>>,
)

@Serializable
data class NoteBody(
    val text: String = "",
    val spans: List<RichSpan> = emptyList(),
    val tables: List<TableBlock> = emptyList(),
)

data class Note(
    val id: Long = 0,
    val title: String,
    val body: NoteBody,
    val audioPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val noteNumber: Int,
)

data class RecordingUiState(
    val active: Boolean = false,
    val processing: Boolean = false,
    val done: Boolean = false,
    val amplitude: Float = 0f,
    val partialText: String = "",
    val finalText: String = "",
    val noteId: Long? = null,
    val error: String? = null,
)
