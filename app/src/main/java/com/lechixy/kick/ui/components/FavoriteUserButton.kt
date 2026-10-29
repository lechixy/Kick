package com.lechixy.kick.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.local.FavoriteUsersStore

@Composable
fun FavoriteUserButton(userId: Long?, username: String, slug: String = "", modifier: Modifier = Modifier) {
    val favorites by FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
    val favorite = favorites.contains(userId, username, slug)
    FilledTonalIconButton(onClick = { FavoriteUsersStore.toggle(userId, username, slug) },
        enabled = userId != null || username.isNotBlank() || slug.isNotBlank(),
        modifier = modifier.semantics { selected = favorite }) {
        Icon(painterResource(if (favorite) R.drawable.star_24dp_e3e3e3_fill1_wght400_grad0_opsz24
            else R.drawable.star_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
            stringResource(if (favorite) R.string.favorite_user_remove else R.string.favorite_user_add),
            Modifier.size(24.dp), tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
