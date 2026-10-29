package com.lechixy.kick

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.data.model.ChannelDetail
import com.lechixy.kick.ui.screens.channel.OfflineBannerScrollConnection
import com.lechixy.kick.ui.screens.channel.OfflineChannelBanner
import com.lechixy.kick.ui.screens.channel.findActivity
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBannerUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun keepTestVisible() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun reversingInTheMiddleKeepsBannerHiddenUntilContentReachesTop() {
        var collapsed by mutableStateOf(false)
        lateinit var jump: (Int) -> Unit
        lateinit var firstIndex: () -> Int
        compose.setContent { MaterialTheme {
            val list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            SideEffect {
                jump = { index -> scope.launch { list.scrollToItem(index) } }
                firstIndex = { list.firstVisibleItemIndex }
            }
            val progress = animateFloatAsState(if (collapsed) 1f else 0f, tween(440))
            val connection = remember { OfflineBannerScrollConnection({ true }, { !list.canScrollBackward }, { collapsed = it }) }
            Column(Modifier.width(700.dp).fillMaxHeight().nestedScroll(connection)) {
                OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, progress,
                    { collapsed = true }, {}, Modifier.testTag("banner"))
                LazyColumn(Modifier.weight(1f).testTag("content"), state = list) {
                    items(80) { Text("Channel content $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        } }
        compose.onNodeWithTag("content").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(collapsed); jump(30) }
        compose.waitUntil(3_000) { firstIndex() == 30 }
        compose.onNodeWithTag("content").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 80f), durationMillis = 700)
        }
        compose.runOnIdle {
            assertTrue("The direction-change gesture must stay in the middle of the page", firstIndex() > 0)
            assertTrue("Reversing before the top must not expand the banner", collapsed)
            jump(2)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("content").performTouchInput { swipeDown() }
        compose.runOnIdle { assertFalse("Returning to the top must expand the banner", collapsed) }
    }

    @Test fun repeatedBannerTapsStayCollapsedAndLabelClearsTheMinimizeButton() {
        var collapsed by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            val progress = animateFloatAsState(if (collapsed) 1f else 0f, tween(440))
            Box(Modifier.width(700.dp)) {
                OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, progress,
                    { collapsed = true }, {}, Modifier.testTag("banner"))
            }
        } }
        compose.onNodeWithTag("banner").performClick()
        compose.onNodeWithTag("banner").performClick()
        compose.runOnIdle { assertTrue(collapsed) }
        val button = compose.onNodeWithContentDescription(context.getString(R.string.offline_minimize)).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(context.getString(R.string.offline_channel_name, "tester"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Offline text must not overlap the minimize button", text.left > button.right)
    }
}
