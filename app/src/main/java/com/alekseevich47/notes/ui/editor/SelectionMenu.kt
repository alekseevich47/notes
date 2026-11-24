package com.alekseevich47.notes.ui.editor

import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.alekseevich47.notes.domain.MarkStyle
import com.alekseevich47.notes.ui.components.softShadowPadding
import com.alekseevich47.notes.ui.components.softShadowRoom
import com.alekseevich47.notes.ui.components.softShadowSteps
import com.alekseevich47.notes.ui.theme.LocalPerfTier
import com.alekseevich47.notes.ui.theme.NotesFont
import kotlin.math.max
import kotlin.math.min

/**
 * Visible editor area in window coordinates. Updated from layout, not from
 * snapshot state, so a scroll frame does not recompose the note. The menu
 * reads [top] and [bottom] inside [PopupPositionProvider.calculatePosition].
 */
internal class VisibleViewport {
    var top: Int = 0
    var bottom: Int = 0
}

internal val LocalSelectionViewport = staticCompositionLocalOf<VisibleViewport?> { null }

/**
 * Telegram-style selection bubble. Primary row is Cut / Copy / overflow.
 * Paste lives on the overflow page, first, and only when the clipboard has
 * text. The formatting list can grow to ten rows; at most six stay visible
 * and the rest scroll. Back stays put under the scroller. Active styles
 * carry a check. Quote is intentionally absent.
 */
@Composable
fun SelectionActionsPopup(
    startHandle: Rect,
    endHandle: Rect,
    overflow: Boolean,
    activeStyles: Set<MarkStyle>,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onUnderline: () -> Unit,
    onMono: () -> Unit,
    onLink: () -> Unit,
    onPlain: () -> Unit,
    menuAlpha: () -> Float = { 1f },
) {
    // Filled during popup measure, before the window is positioned. Not state:
    // a new size must not recompose the editor on the next key.
    val reserve = remember { PlateReserve() }
    SelectionBubble(
        startHandle = startHandle,
        endHandle = endHandle,
        onDismiss = onDismiss,
        reserve = reserve,
        menuAlpha = menuAlpha,
    ) {
        val canPaste = LocalClipboardManager.current.hasText()
        SubcomposeLayout { constraints ->
            val windowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else -1
            if (!reserve.matches(windowWidth, canPaste, activeStyles)) {
                val menuConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                val primary = subcompose(ReservePrimary) {
                    PrimaryMenu(
                        onCut = onCut,
                        onCopy = onCopy,
                        onMore = onMore,
                    )
                }.first().measure(menuConstraints)
                val overflowPage = subcompose(ReserveOverflow) {
                    OverflowMenu(
                        canPaste = canPaste,
                        onPaste = onPaste,
                        activeStyles = activeStyles,
                        onHidden = onHidden,
                        onSelectAll = onSelectAll,
                        onBold = onBold,
                        onItalic = onItalic,
                        onStrike = onStrike,
                        onUnderline = onUnderline,
                        onMono = onMono,
                        onLink = onLink,
                        onPlain = onPlain,
                        onBack = onBack,
                    )
                }.first().measure(menuConstraints)
                reserve.store(
                    windowWidth = windowWidth,
                    canPaste = canPaste,
                    styles = activeStyles,
                    size = IntSize(
                        max(primary.width, overflowPage.width),
                        max(primary.height, overflowPage.height),
                    ),
                )
            }
            val page = subcompose(ReserveContent) {
                AnimatedContent(
                    targetState = overflow,
                    transitionSpec = {
                        (fadeIn(tween(140)) togetherWith fadeOut(tween(100))).using(
                            SizeTransform(clip = false) { _, _ -> snap() },
                        )
                    },
                    label = "selection-menu",
                ) { formatting ->
                    if (formatting) {
                        OverflowMenu(
                            canPaste = canPaste,
                            onPaste = onPaste,
                            activeStyles = activeStyles,
                            onHidden = onHidden,
                            onSelectAll = onSelectAll,
                            onBold = onBold,
                            onItalic = onItalic,
                            onStrike = onStrike,
                            onUnderline = onUnderline,
                            onMono = onMono,
                            onLink = onLink,
                            onPlain = onPlain,
                            onBack = onBack,
                        )
                    } else {
                        PrimaryMenu(
                            onCut = onCut,
                            onCopy = onCopy,
                            onMore = onMore,
                        )
                    }
                }
            }.first().measure(constraints)
            layout(page.width, page.height) {
                page.place(0, 0)
            }
        }
    }
}

/**
 * Shared chrome for the selection menu and the one-button paste bubble.
 * [reserve] is the plate used to decide above/below and the horizontal center.
 * An empty reserve follows the plate that is on screen.
 */
@Composable
private fun SelectionBubble(
    startHandle: Rect,
    endHandle: Rect,
    onDismiss: () -> Unit,
    reserve: PlateReserve = emptyPlateReserve,
    menuAlpha: () -> Float = { 1f },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.roundToPx() }
    val marginPx = with(density) { MenuScreenMargin.roundToPx() }
    val shadowRoom = softShadowRoom(10.dp)
    val shadowPadPx = with(density) { shadowRoom.roundToPx() }
    val viewport = LocalSelectionViewport.current
    val alphaNow = rememberUpdatedState(menuAlpha)
    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val view = viewport
                return placeSelectionMenu(
                    anchorBounds = anchorBounds,
                    windowSize = windowSize,
                    popupContentSize = popupContentSize,
                    startHandle = startHandle,
                    endHandle = endHandle,
                    gapPx = gapPx,
                    marginPx = marginPx,
                    shadowPadPx = shadowPadPx,
                    reserve = reserve.size,
                    viewportTop = view?.top ?: 0,
                    viewportBottom = view?.bottom ?: windowSize.height,
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
        // The popup is measured against the window. Keep the plate inside the
        // same margin placeSelectionMenu uses, so the ⋮ button is never the
        // piece that gets squeezed off the screen.
        BoxWithConstraints {
            val maxPlate = if (maxWidth.value.isFinite()) {
                (maxWidth - MenuScreenMargin * 2).coerceAtLeast(0.dp)
            } else {
                maxWidth
            }
            Box(
                modifier = Modifier
                    .softShadowPadding(
                        shape = shape,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        room = shadowRoom,
                        steps = softShadowSteps(LocalPerfTier.current),
                    )
                    .widthIn(max = maxPlate)
                    .clip(shape)
                    .background(plate),
            ) {
                KeepMenuAboveHandles()
                Box(Modifier.graphicsLayer { alpha = alphaNow.value.invoke() }) {
                    content()
                }
            }
        }
    }
}

@Composable
fun PasteActionPopup(anchor: Rect, onPaste: () -> Unit, onDismiss: () -> Unit) {
    SelectionBubble(
        startHandle = anchor,
        endHandle = anchor,
        onDismiss = onDismiss,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            BubbleLabel("Вставить", onPaste)
        }
    }
}

private val MenuScreenMargin = 8.dp
private val OverflowRowHeight = 48.dp
private val BackRowHeight = 48.dp
private val OverflowDividerHeight = 0.6.dp
private val OverflowDividerTop = 2.dp

/**
 * How many overflow rows fit above the fixed back block.
 * Short windows floor at 3; anything taller than six rows stays at 6.
 */
internal fun overflowVisibleRows(maxHeightDp: Float, rowDp: Float, backBlockDp: Float): Int {
    if (!(rowDp > 0f) || !maxHeightDp.isFinite()) return 6
    val rows = ((maxHeightDp - backBlockDp) / rowDp).toInt()
    return rows.coerceIn(3, 6)
}

private const val ReservePrimary = "reserve-primary"
private const val ReserveOverflow = "reserve-overflow"
private const val ReserveContent = "reserve-content"

/**
 * Max plate of both menu pages, excluding the shadow padding. Recomputed only
 * when [matches] fails, so a keystroke that does not change paste, width, or
 * styles does not measure the hidden page again.
 */
private class PlateReserve {
    private var windowWidth: Int = Int.MIN_VALUE
    private var canPaste: Boolean = false
    private var styles: Set<MarkStyle> = emptySet()
    var size: IntSize = IntSize.Zero
        private set

    fun matches(windowWidth: Int, canPaste: Boolean, styles: Set<MarkStyle>): Boolean {
        return this.windowWidth == windowWidth && this.canPaste == canPaste && this.styles == styles
    }

    fun store(windowWidth: Int, canPaste: Boolean, styles: Set<MarkStyle>, size: IntSize) {
        this.windowWidth = windowWidth
        this.canPaste = canPaste
        this.styles = styles.toSet()
        this.size = size
    }
}

private val emptyPlateReserve = PlateReserve()

/**
 * Places the plate so the horizontal center and the edge nearest the text stay
 * put when the page changes. [reserve] is the larger page, without shadow padding.
 * Above or below is chosen from that height, not from the page on screen.
 */
internal fun placeSelectionMenu(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    startHandle: Rect,
    endHandle: Rect,
    gapPx: Int,
    marginPx: Int,
    shadowPadPx: Int,
    reserve: IntSize,
    viewportTop: Int = 0,
    viewportBottom: Int = windowSize.height,
    gapBelowPx: Int = gapPx,
): IntOffset {
    val selLeft = min(startHandle.left, endHandle.left)
    val selRight = max(startHandle.right, endHandle.right)
    val selTop = min(startHandle.top, endHandle.top)
    val selBottom = max(startHandle.bottom, endHandle.bottom)
    // Shadow padding is part of the popup window. Place the plate, not the padding.
    val plateWidth = (popupContentSize.width - shadowPadPx * 2).coerceAtLeast(0)
    val plateHeight = (popupContentSize.height - shadowPadPx * 2).coerceAtLeast(0)
    val reserved = if (reserve.width == 0 && reserve.height == 0) {
        IntSize(plateWidth, plateHeight)
    } else {
        reserve
    }
    val rawCenter = anchorBounds.left + ((selLeft + selRight) / 2f).toInt()
    val low = marginPx + reserved.width / 2
    val high = windowSize.width - marginPx - reserved.width / 2
    val centerX = if (low <= high) {
        rawCenter.coerceIn(low, high)
    } else {
        // The reserved plate is wider than the window. Stay inside the window
        // without clamping against the page that happens to be showing.
        rawCenter.coerceIn(0, windowSize.width.coerceAtLeast(0))
    }
    val x = centerX - plateWidth / 2 - shadowPadPx
    val selTopWin = anchorBounds.top + selTop
    val selBottomWin = anchorBounds.top + selBottom
    val viewTop = viewportTop
    val viewBottom = if (viewportBottom > viewportTop) viewportBottom else windowSize.height
    val reservedHeight = reserved.height
    val plateTop = when {
        selBottomWin < viewTop -> (viewTop + marginPx).toFloat()
        selTopWin > viewBottom -> (viewBottom - marginPx - plateHeight).toFloat()
        else -> {
            val aboveTop = max(selTopWin, viewTop.toFloat()) - gapPx - reservedHeight
            if (aboveTop >= viewTop + marginPx) {
                max(selTopWin, viewTop.toFloat()) - gapPx - plateHeight
            } else {
                val belowTop = min(selBottomWin, viewBottom.toFloat()) + gapBelowPx
                if (belowTop + reservedHeight <= viewBottom - marginPx) {
                    belowTop
                } else {
                    (viewTop + marginPx).toFloat()
                }
            }
        }
    }
    return IntOffset(x, plateTop.toInt() - shadowPadPx)
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
    // Labels take only the width left after the divider and the ⋮ button.
    // Those two have no weight, so Row measures them first and cannot
    // collapse the button into a stick when the labels are wide.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BubbleLabel("Вырезать", onCut)
            BubbleLabel("Копировать", onCopy)
        }
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        fontFamily = NotesFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun OverflowMenu(
    canPaste: Boolean,
    onPaste: () -> Unit,
    activeStyles: Set<MarkStyle>,
    onHidden: () -> Unit,
    onSelectAll: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onUnderline: () -> Unit,
    onMono: () -> Unit,
    onLink: () -> Unit,
    onPlain: () -> Unit,
    onBack: () -> Unit,
) {
    val scroll = rememberScrollState()
    val plate = MaterialTheme.colorScheme.surface
    BoxWithConstraints(Modifier.widthIn(min = 212.dp, max = 280.dp)) {
        // A popup can be measured with an infinite max. Scroll needs a finite cap.
        val finiteHeight = maxHeight.value.isFinite()
        val backBlockDp = BackRowHeight.value + OverflowDividerTop.value + OverflowDividerHeight.value
        val visibleRows = if (finiteHeight) {
            overflowVisibleRows(maxHeight.value, OverflowRowHeight.value, backBlockDp)
        } else {
            6
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (finiteHeight) Modifier else Modifier.heightIn(max = 480.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(OverflowRowHeight * visibleRows)
                    .menuScrollFades(scroll, plate),
            ) {
                Column(Modifier.verticalScroll(scroll)) {
                    if (canPaste) OverflowLabel("Вставить", onClick = onPaste)
                    OverflowLabel("Выбрать всё", onClick = onSelectAll)
                    OverflowLabel("Обычный", onClick = onPlain)
                    OverflowLabel("Жирный", checked = MarkStyle.BOLD in activeStyles, onClick = onBold)
                    OverflowLabel("Курсив", checked = MarkStyle.ITALIC in activeStyles, onClick = onItalic)
                    OverflowLabel("Подчёркнутый", checked = MarkStyle.UNDERLINE in activeStyles, onClick = onUnderline)
                    OverflowLabel("Скрытый", checked = MarkStyle.HIDDEN in activeStyles, onClick = onHidden)
                    OverflowLabel("Зачёркнутый", checked = MarkStyle.STRIKE in activeStyles, onClick = onStrike)
                    OverflowLabel("Моно", checked = MarkStyle.MONO in activeStyles, onClick = onMono)
                    OverflowLabel("Ссылка", checked = MarkStyle.LINK in activeStyles, onClick = onLink)
                }
            }
            Box(
                Modifier
                    .padding(top = OverflowDividerTop)
                    .fillMaxWidth()
                    .height(OverflowDividerHeight)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BackRowHeight)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp),
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

/**
 * Fades the plate color over the scroll edges that still have hidden rows.
 * Drawn with the content, so a hidden menu does not animate and nothing is
 * rendered into an offscreen layer.
 */
private fun Modifier.menuScrollFades(scroll: ScrollState, plate: Color): Modifier = drawWithContent {
    drawContent()
    val fadePx = 12.dp.toPx()
    if (fadePx <= 0f) return@drawWithContent
    if (scroll.canScrollForward) {
        val top = (size.height - fadePx).coerceAtLeast(0f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(plate.copy(alpha = 0f), plate),
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, size.height - top),
        )
    }
    if (scroll.canScrollBackward) {
        val height = fadePx.coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(plate, plate.copy(alpha = 0f)),
                startY = 0f,
                endY = height,
            ),
            size = Size(size.width, height),
        )
    }
}

@Composable
private fun OverflowLabel(
    text: String,
    checked: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OverflowRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontFamily = NotesFont,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (checked) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
