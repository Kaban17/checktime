package dev.boar.checktime.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinutesFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun focusedFieldKeepsTypedTextOverLateEmissionUntilCommit() {
        val committed = mutableListOf<Int>()
        var state by mutableStateOf(30)
        compose.setContent {
            MinutesField("x", value = state, onCommit = { committed.add(it) })
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("70")

        // Simulate a late DataStore emission for the earlier value arriving while typing.
        state = 7

        compose.onNode(hasSetTextAction()).assertTextContains("70")
        // Нет коммита, пока поле в фокусе и не было Done/потери фокуса.
        assertTrue(committed.isEmpty())
    }

    @Test fun unfocusedFieldSyncsFromValue() {
        var state by mutableStateOf(30)
        compose.setContent {
            MinutesField("x", value = state, onCommit = {})
        }

        state = 15

        compose.onNode(hasSetTextAction()).assertTextContains("15")
    }

    @Test fun imeDoneCommitsValidValue() {
        val committed = mutableListOf<Int>()
        var state by mutableStateOf(30)
        compose.setContent {
            MinutesField("x", value = state, onCommit = { committed.add(it) })
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("70")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertEquals(listOf(70), committed)
    }

    @Test fun imeDoneRollsBackInvalidValue() {
        val committed = mutableListOf<Int>()
        var state by mutableStateOf(30)
        compose.setContent {
            MinutesField("x", value = state, onCommit = { committed.add(it) })
        }

        compose.onNode(hasSetTextAction()).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("0")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertTrue(committed.isEmpty())
        compose.onNode(hasSetTextAction()).assertTextContains("30")
    }
}
