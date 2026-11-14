package com.alekseevich47.notes.data.local

import com.alekseevich47.notes.domain.Note
import com.alekseevich47.notes.domain.NoteBody
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = runCatching { json.decodeFromString(NoteBody.serializer(), bodyJson) }
        .getOrElse { NoteBody(text = bodyJson) },
    audioPath = audioPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    noteNumber = noteNumber,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    bodyJson = json.encodeToString(NoteBody.serializer(), body),
    audioPath = audioPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    noteNumber = noteNumber,
)
