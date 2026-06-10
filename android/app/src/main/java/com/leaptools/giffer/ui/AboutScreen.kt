package com.leaptools.giffer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import android.content.Intent
import com.leaptools.giffer.BuildConfig

@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Giffer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Giffer converts Android Motion Photos into animated GIFs. " +
                        "Everything is processed on-device; no data is collected or transmitted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Author: Peter Fajner", style = MaterialTheme.typography.bodySmall)
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, "https://github.com/peterfajner/giffer".toUri())
                        )
                    },
                    modifier = Modifier.padding(0.dp),
                ) {
                    Text("GitHub Repository", fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}
