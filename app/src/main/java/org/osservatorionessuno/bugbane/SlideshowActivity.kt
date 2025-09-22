package org.osservatorionessuno.bugbane

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osservatorionessuno.bugbane.components.SlideshowPage
import org.osservatorionessuno.bugbane.ui.theme.Theme
import org.osservatorionessuno.bugbane.utils.AppState
import org.osservatorionessuno.bugbane.utils.ConfigurationViewModel
import org.osservatorionessuno.bugbane.utils.ViewModelFactory

const val INTENT_EXIT_BACKPRESS = "EXIT_ON_BACK"
private const val TAG = "SlideshowActivity"

class SlideshowActivity : ComponentActivity() {

    private val configViewModel by lazy {
        ViewModelFactory.get(application)
    }

    private var shouldExitOnBackPress: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // During first-time onboarding and by default, a backpress quits the app,
        // but re-launching the slideshow from another screen (ScanScreen) should not
        shouldExitOnBackPress = intent.getBooleanExtra(INTENT_EXIT_BACKPRESS, shouldExitOnBackPress)

        // Exit application when back is pressed during slideshow
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (shouldExitOnBackPress) {
                    // Exit the application
                    finishAffinity()
                } else {
                    // Default back button behaviour
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            Theme {
                SlideshowScreen(
                    configViewModel,
                    onSlideshowComplete = {
                        restartMainActivity()
                    }
                )
            }
        }
    }

    private fun restartMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SlideshowActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SlideshowScreen(
    viewModel: ConfigurationViewModel,
    onSlideshowComplete: () -> Unit,
) {
    val allStates = AppState.valuesInOrder().filter { it !in AppState.hiddenStates() }
    val permissionState = viewModel.configurationState.collectAsStateWithLifecycle()

    // Initial page index (for the circle indicators at the top of the slideshow)
    var initialPage = 0
    val index: Int = allStates.indexOf(permissionState.value)
    if (index in allStates.indices) {
        initialPage = index
    }

    // pageCount = allStates.size - hiddenStates
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { allStates.size - AppState.hiddenStates().size})

    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun updatePager(state: AppState) {
        Log.d(TAG, "updatePager to $state")
        val currentIndex = allStates.indexOf(state)
        if (currentIndex in allStates.indices) {
            pagerState.animateScrollToPage(currentIndex)
        } else if (state == AppState.AdbConnected) {
            onSlideshowComplete()
        }
    }

    // Skip screens already satisfied
    LaunchedEffect(permissionState.value) {
        Log.d(TAG, "SlideSHowActivity got new state ${permissionState.value}")
        updatePager(permissionState.value)
    }

    // Re-check state when the user resumes the app
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // todo
                Log.d(TAG, "onResume [skip recalculating for now?]")
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Page indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            allStates.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Horizontal pager (swipe disabled)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false
        ) { pageIndex ->
            val state = allStates[pageIndex]
            SlideshowPage(
                state = state,
                onClickContinue = {
                    Log.d(TAG, "onClickContinue with state $state")
                    viewModel.onChangeStateRequest(state)
                }
            )
        }
    }
}


 