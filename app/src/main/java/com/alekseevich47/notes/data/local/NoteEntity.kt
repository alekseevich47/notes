package com.alekseevich47.notes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val searchBlob: String,
    val audioPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val seq: Int,
)
