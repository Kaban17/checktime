package dev.boar.checktime.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Кнопка: один вызов onClick по нажатию, затем автоповтор при удержании (после 400 мс, каждые 80 мс). */
@Composable
fun RepeatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val latestOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        latestOnClick()
                        coroutineScope {
                            val repeater = launch {
                                delay(400)
                                while (isActive) {
                                    latestOnClick()
                                    delay(80)
                                }
                            }
                            tryAwaitRelease()
                            repeater.cancel()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
