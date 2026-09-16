package dev.boar.checktime.ui.allocation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.boar.checktime.R
import dev.boar.checktime.appContainer
import dev.boar.checktime.ui.theme.CheckTimeTheme

/**
 * Полноэкранный попап распределения. Отдельная задача (taskAffinity=""), singleTask,
 * показывается над блокировкой. Жест «назад» = отложить.
 */
class AllocationActivity : ComponentActivity() {
    private val viewModel: AllocationViewModel by viewModels {
        AllocationViewModel.factory(applicationContext, appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CheckTimeTheme {
                AllocationRoute(viewModel, onFinished = { finish() })
            }
        }
    }
}

@Composable
fun AllocationRoute(viewModel: AllocationViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AllocationEvent.Saved, AllocationEvent.Postponed -> onFinished()
                AllocationEvent.Stale -> Toast.makeText(context, R.string.allocation_stale, Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(state) {
        if (state is AllocationUiState.Empty) onFinished()
    }
    BackHandler { viewModel.postpone() }

    AllocationScreen(
        state = state,
        onIncrement = { id -> viewModel.edit { it.increment(id) } },
        onDecrement = { id -> viewModel.edit { it.decrement(id) } },
        onAssignRest = { id -> viewModel.edit { it.assignRest(id) } },
        onSave = viewModel::save,
        onPostpone = viewModel::postpone,
    )
}
