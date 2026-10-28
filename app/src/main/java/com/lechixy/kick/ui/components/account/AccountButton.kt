package com.lechixy.kick.ui.components.account

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
internal fun AccountAvatar(avatar: String?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AccountButton(onChannelClick: (String) -> Unit = {}) {
    val session by AccountGraph.auth.session.collectAsStateWithLifecycle()
    val dashboard by AccountGraph.dashboard.state.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val name = if (session.signedIn) dashboard.channel?.user?.username ?: session.profile?.name
        ?: stringResource(R.string.account_my_account) else stringResource(R.string.account_sign_in)
    Surface(onClick = { open = true }, shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 210.dp), tonalElevation = 2.dp) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.signedIn) AccountAvatar(dashboard.channel?.user?.avatar ?: session.profile?.avatar, name, Modifier.size(34.dp))
            else Icon(painterResource(R.drawable.person_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null, modifier = Modifier.padding(5.dp).size(24.dp))
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AdaptiveAccountSheet(onDismiss = { open = false }, onChannelClick = onChannelClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptiveAccountSheet(onDismiss: () -> Unit, onChannelClick: (String) -> Unit) {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    if (width < 600.dp) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AccountDashboard(onChannelClick, onDismiss)
        }
    } else {
        val transition = remember { MutableTransitionState(false).apply { targetState = true } }
        val close = { transition.targetState = false }
        LaunchedEffect(transition) {
            snapshotFlow { transition.isIdle && !transition.currentState && !transition.targetState }
                .filter { it }.first()
            onDismiss()
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable(onClick = close))
                AnimatedVisibility(visibleState = transition, modifier = Modifier.align(Alignment.CenterEnd),
                    enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it } + fadeIn(),
                    exit = slideOutHorizontally(spring(stiffness = 600f)) { it } + fadeOut()) {
                    Surface(modifier = Modifier.width(420.dp).fillMaxHeight()
                        .pointerInput(Unit) { detectTapGestures {} },
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                            AccountDashboard(onChannelClick, close)
                        }
                    }
                }
            }
        }
    }
}
