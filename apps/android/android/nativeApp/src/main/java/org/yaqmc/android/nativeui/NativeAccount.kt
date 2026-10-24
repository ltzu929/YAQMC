package org.yaqmc.android.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import org.json.JSONObject
import org.yaqmc.android.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AccountMembership(account: AccountUi) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (account.identity.isNotBlank()) Text(account.identity, style = MaterialTheme.typography.bodySmall)
        account.entitlement?.let { membership ->
            Text(stringResource(R.string.native_membership), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(membershipTier(membership.text("tier"))) + " · " + stringResource(membershipState(membership.text("membership"))))
            val expiry = if (membership.isNull("expiresAtMs")) stringResource(R.string.native_no_expiry)
                else DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(membership.optLong("expiresAtMs")))
            Text(stringResource(R.string.native_expires, expiry))
            val secondary = membership.optJSONArray("secondaryEntitlements")
            val labels = if (secondary == null) emptyList() else (0 until secondary.length()).mapNotNull { secondaryEntitlement(secondary.optString(it)) }.map { stringResource(it) }
            if (labels.isNotEmpty()) Text(stringResource(R.string.native_secondary, labels.joinToString(" · ")))
        }
        if (account.state in setOf("rejected", "cancelled", "expired", "network-error", "protocol-error")) Text(accountLabel(account.state, account.upstreamCode), color = MaterialTheme.colorScheme.error)
    }
}

@UnstableApi
@Composable
internal fun AccountLibraryNotice(account: AccountUi, model: NativeViewModel) {
    if (account.authenticated) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("library-account-notice")) {
        Text(accountLabel(account.state, account.upstreamCode))
        Text(stringResource(if (account.state in setOf("session-expired", "reauthentication-required")) R.string.native_library_reauth else R.string.native_library_login))
        if (account.state == "restoring-session" || account.loginPending) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { model.navigate(NativeRoute("settings")) }) { Text(stringResource(R.string.native_account_settings)) }
    }
}

@UnstableApi
@Composable
internal fun PlaylistActions(model: NativeViewModel, route: NativeRoute, page: CatalogPage) {
    val account by model.account.collectAsStateWithLifecycle()
    val summaries by model.playlistSummaries.collectAsStateWithLifecycle()
    val pending by model.pendingPlaylists.collectAsStateWithLifecycle()
    val errors by model.playlistErrors.collectAsStateWithLifecycle()
    val player by model.player.collectAsStateWithLifecycle()
    val original = page.raw.optJSONObject("summary") ?: if (route.kind == "account-playlist") runCatching { JSONObject(route.playlist) }.getOrNull() else page.raw
    val summary = summaries[route.id] ?: original ?: return
    val busy = route.id in pending
    val write = account.authenticated && account.capabilities.optBoolean("playlistWrite")
    var renaming by rememberSaveable(route.id) { mutableStateOf(false) }
    var draft by rememberSaveable(route.id) { mutableStateOf("") }
    var confirmDelete by rememberSaveable(route.id) { mutableStateOf(false) }
    var confirmRemove by remember(route.id) { mutableStateOf<Track?>(null) }
    LaunchedEffect(summary.text("title"), busy) { if (!busy && renaming && summary.text("title") == draft.trim()) renaming = false }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (route.kind == "playlist" && summaries[route.id] != null && summary.text("ownership").isNotBlank()) Text(stringResource(NativeModels.ownershipLabel(summary.text("ownership"))))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (summary.canEdit("canRename")) OutlinedButton(enabled = write && !busy, onClick = { draft = summary.text("title"); renaming = true }) { Text(stringResource(R.string.native_rename_playlist)) }
            if (summary.canEdit("canAddTracks")) OutlinedButton(enabled = write && !busy && player.current?.writable == true, onClick = { player.current?.let { model.mutatePlaylist("add", summary, track = it) } }) { Text(stringResource(R.string.native_add_current)) }
            if (summary.canEdit("canRemoveTracks")) OutlinedButton(enabled = write && !busy && player.current?.writable == true, onClick = { confirmRemove = player.current }) { Text(stringResource(R.string.native_remove_current)) }
            if (summary.canEdit("canDelete")) OutlinedButton(enabled = write && !busy, onClick = { confirmDelete = true }) { Text(stringResource(R.string.native_delete_playlist)) }
            if (route.id.startsWith("qqmusic:playlist:") && summary.text("ownership") != "owned") {
                val collected = summary.text("ownership") == "collected"
                OutlinedButton(enabled = !busy && (!account.authenticated || write), onClick = { model.mutatePlaylist("collect", summary, collected = !collected) }) {
                    Text(stringResource(if (collected) R.string.native_uncollect_playlist else R.string.native_collect_playlist))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        errors[route.id]?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
    }
    if (renaming) NativeAlertDialog(onDismissRequest = { if (!busy) renaming = false }, title = { Text(stringResource(R.string.native_rename_playlist)) },
        text = { Column { OutlinedTextField(draft, { draft = it }, singleLine = true, enabled = !busy, isError = !validPlaylistTitle(draft)); Text(stringResource(R.string.native_title_rules)); errors[route.id]?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = !busy && validPlaylistTitle(draft), onClick = { model.mutatePlaylist("rename", summary, title = draft) }) { Text(stringResource(R.string.native_confirm)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { renaming = false }) { Text(stringResource(R.string.native_cancel)) } })
    if (confirmDelete) Confirm(stringResource(R.string.native_confirm_delete_playlist, summary.text("title")), { confirmDelete = false }) { model.mutatePlaylist("delete", summary) }
    confirmRemove?.let { track -> Confirm(stringResource(R.string.native_confirm_remove_current, track.title), { confirmRemove = null }) { model.mutatePlaylist("remove", summary, track = track) } }
}

@UnstableApi
@Composable
internal fun AddToPlaylistDialog(model: NativeViewModel) {
    val picker by model.picker.collectAsStateWithLifecycle()
    val pending by model.pendingPlaylists.collectAsStateWithLifecycle()
    val errors by model.playlistErrors.collectAsStateWithLifecycle()
    val state = picker ?: return
    NativeAlertDialog(onDismissRequest = { model.closePicker() }, title = { Text(stringResource(R.string.native_add_playlist)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(state.track.title) }
                items(state.playlists, key = { it.id }) { playlist ->
                    TextButton(enabled = playlist.id !in pending, onClick = { model.mutatePlaylist("add", playlist.json, track = state.track) }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.title, Modifier.weight(1f))
                        if (playlist.id in pending) CircularProgressIndicator(Modifier.size(20.dp))
                    }
                    errors[playlist.id]?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item { ErrorRetry(error, { model.loadPicker(reset = !state.loaded) }) } }
                if (state.stale) item { Text(stringResource(R.string.native_stale)); TextButton(onClick = { model.loadPicker(reset = true) }) { Text(stringResource(R.string.native_refresh)) } }
                if (!state.loading && state.error == null && state.playlists.isEmpty() && state.cursor == null) item { Text(stringResource(R.string.native_no_writable_playlists)) }
                if (state.cursor != null && !state.loading && state.error == null) item {
                    LaunchedEffect(state.cursor) { model.loadPicker() }
                    TextButton(onClick = { model.loadPicker() }) { Text(stringResource(R.string.native_load_more)) }
                }
            }
        }, confirmButton = { TextButton(onClick = { model.closePicker() }) { Text(stringResource(R.string.native_cancel)) } })
}
