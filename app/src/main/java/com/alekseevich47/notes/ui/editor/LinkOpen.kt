package com.alekseevich47.notes.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.domain.TextMark
import com.alekseevich47.notes.domain.isSafeLink
import com.alekseevich47.notes.domain.linkTarget
import kotlinx.coroutines.withTimeoutOrNull

internal fun openSafeLink(context: Context, url: String) {
    if (!isSafeLink(url)) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

internal fun coveringLinkUrl(marks: List<TextMark>, start: Int, end: Int): String {
    return marks.firstOrNull { mark ->
        mark.style == MarkStyle.LINK && mark.start < end && mark.end > start && isSafeLink(mark.url.orEmpty())
    }?.url.orEmpty()
}

/**
 * A short tap on a link opens it. The pointer is not consumed, so the field
 * still places the caret. A selection menu, a long press, and locked hidden
 * text leave the link closed.
 */
@Composable
internal fun Modifier.followLinkTaps(
    marks: List<TextMark>,
    menuVisible: Boolean,
    layout: TextLayoutResult?,
    hiddenUnlocked: Boolean,
): Modifier {
    val context = LocalContext.current
    val marksState = rememberUpdatedState(marks)
    val menuState = rememberUpdatedState(menuVisible)
    val layoutState = rememberUpdatedState(layout)
    val unlockedState = rememberUpdatedState(hiddenUnlocked)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: return@withTimeoutOrNull null
                    if (!change.pressed) return@withTimeoutOrNull change
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (dx * dx + dy * dy > slop * slop) return@withTimeoutOrNull null
                }
            }
            if (tapped == null) return@awaitEachGesture
            if (menuState.value) return@awaitEachGesture
            val result = layoutState.value ?: return@awaitEachGesture
            val offset = result.getOffsetForPosition(down.position)
            val url = linkTarget(marksState.value, offset, unlockedState.value) ?: return@awaitEachGesture
            openSafeLink(context, url)
        }
    }
}
