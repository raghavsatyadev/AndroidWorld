package io.github.raghavsatyadev.androidworld.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmationQueue

/** Shows the head of the [ConfirmationQueue]. Placed once, at the root of the window. */
@Composable
fun ConfirmDialogHost(queue: ConfirmationQueue) {
    val request by queue.current.collectAsState()
    val r = request ?: return
    AlertDialog(
        onDismissRequest = { queue.answer(r, false) },
        icon = { if (r.destructive) Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(r.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.message)
                r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = {
            Button(
                onClick = { queue.answer(r, true) },
                colors = if (r.destructive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                } else ButtonDefaults.buttonColors(),
            ) { Text(r.confirmLabel) }
        },
        dismissButton = { TextButton(onClick = { queue.answer(r, false) }) { Text("Cancel") } },
    )
}
