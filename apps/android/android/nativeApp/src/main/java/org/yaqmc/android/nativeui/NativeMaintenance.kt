package org.yaqmc.android.nativeui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.yaqmc.android.BuildConfig
import org.yaqmc.android.R

internal data class NativeDiagnosticsState(
    val logLevel: String = "info", val recentErrors: List<String> = emptyList(),
    val loading: Boolean = false, val busy: Boolean = false, val error: Boolean = false,
)
internal data class NativeUpdateState(
    val loading: Boolean = false, val checked: Boolean = false,
    val availableVersion: String = "", val releaseUrl: String = "",
    val notes: String = "", val error: Boolean = false,
)

/** Only fixed HTTPS project links and the validated update-checker URL may be opened. */
internal fun openProjectUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun NativeDiagnostics(model: NativeViewModel) {
    val state by model.diagnostics.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.loadDiagnostics() }
    LazyColumn(Modifier.fillMaxSize().testTag("diagnostics-list"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.native_diag_title),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error) item { Column {
            Text(stringResource(R.string.native_load_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = model::loadDiagnostics) { Text(stringResource(R.string.native_refresh)) }
        } }
        item { Text(stringResource(R.string.native_diag_log_level), style = MaterialTheme.typography.titleMedium) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("info" to R.string.native_diag_info, "debug" to R.string.native_diag_debug,
                "trace" to R.string.native_diag_trace).forEach { (level, label) ->
                FilterChip(state.logLevel == level, onClick = { model.setDiagnosticsLevel(level) },
                    label = { Text(stringResource(label)) }, enabled = !state.busy,
                    modifier = Modifier.testTag("diagnostics-level-" + level))
            }
        } }
        item { OutlinedButton(onClick = { model.shareDiagnosticBundle(context) }, enabled = !state.busy) {
            Text(stringResource(R.string.native_diag_export))
        } }
        item { Text(stringResource(R.string.native_diag_privacy), style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = { confirmClear = true }, enabled = !state.busy) {
            Text(stringResource(R.string.native_diag_clear_logs))
        } }
        item { Text(stringResource(R.string.native_diag_recent_errors), style = MaterialTheme.typography.titleMedium) }
        if (state.recentErrors.isEmpty()) item { Text(stringResource(R.string.native_diag_no_errors)) }
        items(state.recentErrors.size) { index ->
            Text(state.recentErrors[index], style = MaterialTheme.typography.bodySmall)
        }
        item { TextButton(onClick = { model.navigate(NativeRoute("issue-report")) }) {
            Text(stringResource(R.string.native_diag_report_issue))
        } }
    }
    if (confirmClear) Confirm(stringResource(R.string.native_diag_confirm_clear_logs),
        close = { confirmClear = false }) { model.clearDiagnosticLogs() }
}

@Composable
internal fun NativeAbout(model: NativeViewModel) {
    val update by model.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { model.checkForUpdates() }
    LazyColumn(Modifier.fillMaxSize().testTag("about-list"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.native_about_title),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("YAQMC · " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.native_about_build, BuildConfig.YAQMC_BUILD_COMMIT)) }
        item { Text(stringResource(R.string.native_about_platform, Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.firstOrNull() ?: "-")) }
        if (update.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (update.checked) item {
            val result = when {
                update.error -> stringResource(R.string.native_update_failed)
                update.availableVersion.isNotBlank() -> stringResource(R.string.native_update_available, update.availableVersion)
                else -> stringResource(R.string.native_update_latest)
            }
            Text(result, modifier = Modifier.testTag("update-status"))
        }
        item { OutlinedButton(onClick = model::checkForUpdates, enabled = !update.loading) {
            Text(stringResource(R.string.native_update_check))
        } }
        if (update.releaseUrl.isNotBlank()) item {
            Button(onClick = { openProjectUrl(context, update.releaseUrl) }) {
                Text(stringResource(R.string.native_update_open_release))
            }
            if (update.notes.isNotBlank()) Text(update.notes, style = MaterialTheme.typography.bodySmall)
        }
        item { TextButton(onClick = {
            openProjectUrl(context, "https://github.com/YAQMC/YAQMC")
        }) { Text(stringResource(R.string.native_about_repository)) } }
    }
}


internal data class NativeIssuePreview(
    val title: String, val body: String, val url: String, val tooLong: Boolean,
)
internal data class NativeIssueState(
    val draftKey: String = "", val preview: NativeIssuePreview? = null,
    val busy: Boolean = false, val error: Boolean = false,
)

@Composable
internal fun NativeIssueReport(model: NativeViewModel) {
    val state by model.issueState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var category by remember { mutableStateOf("bug") }
    var summary by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val key = listOf(category, summary, description).joinToString("\u0000")
    val preview = state.preview?.takeIf { state.draftKey == key }
    val categoryLabels = listOf("bug" to R.string.native_issue_bug,
        "playback" to R.string.native_issue_playback, "provider" to R.string.native_issue_provider,
        "lyrics" to R.string.native_issue_lyrics, "ui" to R.string.native_issue_ui,
        "other" to R.string.native_issue_other)
    LazyColumn(Modifier.fillMaxSize().testTag("issue-report-list"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.native_diag_report_issue),
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.native_issue_intro)) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categoryLabels.forEach { (value, label) ->
                FilterChip(selected = category == value, onClick = { category = value },
                    label = { Text(stringResource(label)) })
            }
        } }
        item { OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(),
            singleLine = true, label = { Text(stringResource(R.string.native_issue_summary)) }) }
        item { OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(),
            minLines = 4, maxLines = 8, label = { Text(stringResource(R.string.native_issue_description)) }) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.error) item { Text(stringResource(R.string.native_issue_failed),
            color = MaterialTheme.colorScheme.error) }
        item { OutlinedButton(
            onClick = { model.previewIssue(category, summary, description) },
            enabled = summary.isNotBlank() && !state.busy) {
            Text(stringResource(R.string.native_issue_preview))
        } }
        if (preview != null) {
            item { Text(preview.title, style = MaterialTheme.typography.titleMedium) }
            item { Text(preview.body, style = MaterialTheme.typography.bodySmall) }
            if (preview.tooLong) item { Text(stringResource(R.string.native_issue_too_long),
                color = MaterialTheme.colorScheme.error) }
            item { Button(onClick = { model.openIssuePreview(context) },
                enabled = !preview.tooLong && !state.busy) {
                Text(stringResource(R.string.native_issue_open))
            } }
        }
    }
}
