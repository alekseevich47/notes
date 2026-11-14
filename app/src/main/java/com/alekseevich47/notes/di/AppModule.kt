package com.alekseevich47.notes.di

import android.content.Context
import androidx.room.Room
import com.alekseevich47.notes.data.local.NoteDao
import com.alekseevich47.notes.data.local.NotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideNoteDao(db: NotesDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
