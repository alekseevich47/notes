package com.alekseevich47.notes.data.secure

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Highest notice id shipped with this build. Raise it when a new notice is added. */
internal const val LATEST_INFO_NOTICE_ID = 1

internal fun pendingNotice(seenId: Int, latestId: Int): Int? =
    if (seenId < latestId) latestId else null

/**
 * First-run notice. Plain [NotesPrefs] so the first frame does not open the keystore.
 * The preference is read once at construction and again only in [markSeen].
 */
@Singleton
class InfoNoticeStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = NotesPrefs.open(context)
    private val _pendingId = MutableStateFlow(
        pendingNotice(prefs.getInt(NotesPrefs.INFO_SEEN_ID, 0), LATEST_INFO_NOTICE_ID),
    )
    val pendingId: StateFlow<Int?> = _pendingId.asStateFlow()

    fun markSeen(id: Int, termsVersion: String?) {
        val saved = prefs.edit()
            .putInt(NotesPrefs.INFO_SEEN_ID, id)
            .putLong(NotesPrefs.TERMS_ACCEPTED_AT, System.currentTimeMillis())
            .putString(NotesPrefs.TERMS_VERSION, termsVersion)
            .commit()
        if (!saved) return
        val seenId = prefs.getInt(NotesPrefs.INFO_SEEN_ID, 0)
        _pendingId.value = pendingNotice(seenId, LATEST_INFO_NOTICE_ID)
    }
}
