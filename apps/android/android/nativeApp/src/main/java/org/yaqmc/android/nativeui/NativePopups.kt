package org.yaqmc.android.nativeui

import android.content.Context
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

internal val LocalNativeContext = staticCompositionLocalOf<Context?> { null }

/** Popup windows install their own Android locals; restore the selected in-app locale inside them. */
@Composable
private fun NativePopupContent(content: @Composable () -> Unit) {
    val context = LocalNativeContext.current ?: LocalContext.current
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides context.resources.configuration, content = content)
}

@Composable
internal fun NativeDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded, onDismissRequest) { NativePopupContent { content() } }
}

@Composable
internal fun NativeAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(onDismissRequest = onDismissRequest,
        title = { NativePopupContent(title) }, confirmButton = { NativePopupContent(confirmButton) },
        dismissButton = dismissButton?.let { { NativePopupContent(it) } }, text = text?.let { { NativePopupContent(it) } })
}
