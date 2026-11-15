package com.alekseevich47.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alekseevich47.notes.ui.theme.NotesFont
import kotlinx.coroutines.delay

@Composable
fun ToastHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2400)
            onDismiss()
        }
    }
    Box(modifier = modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 20.dp, end = 20.dp)) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .shadow(8.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontFamily = NotesFont,
                textAlign = TextAlign.Center,
            )
        }
    }
}
