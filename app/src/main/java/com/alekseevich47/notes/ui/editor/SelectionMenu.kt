package com.alekseevich47.notes.ui.editor

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.alekseevich47.notes.ui.components.softShadowPadding
import com.alekseevich47.notes.ui.components.softShadowRoom
import com.alekseevich47.notes.ui.theme.NotesFont
import kotlin.math.max
import kotlin.math.min

/**
 * Telegram-style selection bubble. Primary row is Cut / Copy / overflow.
 * Overflow starts with «Выбрать всё», then hidden text and styles, then Back.
 * Quote is intentionally absent.
 */
@Composable
fun SelectionActionsPopup(
    startHandle: Rect,
    endHandle: Rect,
    overflow: Boolean,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onUnderline: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val shadowRoom = softShadowRoom(10.dp)
    val shadowPadPx = with(density) { shadowRoom.roundToPx() }
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                return placeSelectionMenu(
                    anchorBounds = anchorBounds,
                    windowSize = windowSize,
                    popupContentSize = popupContentSize,
                    startHandle = startHandle,
                    endHandle = endHandle,
                    gapPx = gapPx,
                    marginPx = marginPx,
                    shadowPadPx = shadowPadPx,
                )
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        val shape = RoundedCornerShape(16.dp)
        val plate = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .softShadowPadding(
                    shape = shape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    room = shadowRoom,
                )
                .clip(shape)
                .background(plate),
        ) {
            KeepMenuAboveHandles()
            if (overflow) {
                OverflowMenu(
                    onHidden = onHidden,
                    onSelectAll = onSelectAll,
                    onBold = onBold,
                    onItalic = onItalic,
                    onStrike = onStrike,
                    onUnderline = onUnderline,
                    onBack = onBack,
                )
            } else {
                PrimaryMenu(onCut = onCut, onCopy = onCopy, onMore = onMore)
            }
        }
    }
}

private fun placeSelectionMenu(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    startHandle: Rect,
    endHandle: Rect,
    gapPx: Int,
    marginPx: Int,
    shadowPadPx: Int,
): IntOffset {
    val selLeft = min(startHandle.left, endHandle.left)
    val selRight = max(startHandle.right, endHandle.right)
    val selTop = min(startHandle.top, endHandle.top)
    val selBottom = max(startHandle.bottom, endHandle.bottom)
    // Padding gives the fade room inside the popup window. Place the plate, not
    // the padding, so the labels stay on the same spot.
    val plateWidth = (popupContentSize.width - shadowPadPx * 2).coerceAtLeast(0)
    val plateHeight = (popupContentSize.height - shadowPadPx * 2).coerceAtLeast(0)
    val center = anchorBounds.left + ((selLeft + selRight) / 2f).toInt()
    var x = center - plateWidth / 2
    var y = anchorBounds.top + selTop.toInt() - plateHeight - gapPx
    if (y < marginPx) {
        y = anchorBounds.top + selBottom.toInt() + gapPx + 18
    }
    val maxX = (windowSize.width - plateWidth - marginPx).coerceAtLeast(marginPx)
    val maxY = (windowSize.height - plateHeight - marginPx).coerceAtLeast(marginPx)
    x = x.coerceIn(marginPx, maxX)
    y = y.coerceIn(marginPx, maxY)
    return IntOffset(x - shadowPadPx, y - shadowPadPx)
}

/**
 * Selection handles are Compose popups on [WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL].
 * Whichever popup is added last paints on top, so a handle update covers the menu.
 * Re-add this popup above that layer once it is attached. The window stays
 * [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE], so handle drags outside the menu still land
 * on the text field.
 *
 * The type change removes the window and adds it again. Hide it until that move
 * finishes, otherwise the menu flashes on, vanishes, and pops back.
 */
@Composable
private fun KeepMenuAboveHandles() {
    val view = LocalView.current
    DisposableEffect(view) {
        val root = view.rootView
        if (windowType(root) != selectionMenuWindowType) root.alpha = 0f
        val lift = Runnable {
            if (!root.isAttachedToWindow) return@Runnable
            if (windowType(root) != selectionMenuWindowType) {
                liftSelectionMenuWindow(root)
            }
            // Re-adding the window detaches it and runs a new effect. Only the
            // effect that still owns the view may reveal it.
            if (root.isAttachedToWindow) root.alpha = 1f
        }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post(lift)
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.removeCallbacks(lift)
            }
        }
        root.addOnAttachStateChangeListener(listener)
        if (root.isAttachedToWindow) root.post(lift)
        onDispose {
            root.removeOnAttachStateChangeListener(listener)
            root.removeCallbacks(lift)
        }
    }
}

// Public SDK stubs omit TYPE_APPLICATION_ABOVE_SUB_PANEL (@hide); value is 1005.
private val selectionMenuWindowType = 1005

private fun windowType(root: View): Int? = (root.layoutParams as? WindowManager.LayoutParams)?.type

@Suppress("WrongConstant")
private fun liftSelectionMenuWindow(root: View): Boolean {
    val params = root.layoutParams as? WindowManager.LayoutParams ?: return false
    if (!root.isAttachedToWindow) return false
    // Public SDK stubs omit TYPE_APPLICATION_ABOVE_SUB_PANEL (@hide).
    if (params.type == selectionMenuWindowType) return true
    val previous = params.type
    val windowManager = root.context.getSystemService(WindowManager::class.java) ?: return false
    params.type = selectionMenuWindowType
    return try {
        windowManager.removeViewImmediate(root)
        windowManager.addView(root, params)
        true
    } catch (_: RuntimeException) {
        params.type = previous
        if (!root.isAttachedToWindow) {
            try {
                windowManager.addView(root, params)
            } catch (_: RuntimeException) {
                return false
            }
        }
        false
    }
}

@Composable
private fun PrimaryMenu(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onMore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        BubbleLabel("Вырезать", onCut)
        BubbleLabel("Копировать", onCopy)
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .width(1.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "Ещё",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BubbleLabel(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        fontFamily = NotesFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun OverflowMenu(
    onHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onUnderline: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.widthIn(min = 212.dp, max = 280.dp)) {
        val extraStyles = maxHeight >= 360.dp
        Column(Modifier.fillMaxWidth()) {
        OverflowLabel("Выбрать всё", onSelectAll)
        OverflowLabel("Скрытый", onHidden)
        OverflowLabel("Жирный", onBold)
        OverflowLabel("Курсив", onItalic)
        if (extraStyles) {
            OverflowLabel("Зачёркнутый", onStrike)
            OverflowLabel("Подчёркнутый", onUnderline)
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
                .height(0.6.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Назад",
                fontFamily = NotesFont,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        }
    }
}

@Composable
private fun OverflowLabel(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        fontFamily = NotesFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
