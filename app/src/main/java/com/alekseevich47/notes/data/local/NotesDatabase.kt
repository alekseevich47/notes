package com.alekseevich47.notes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class], version = 2, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_createdAt ON notes(createdAt)")
            }
        }
    }
}
