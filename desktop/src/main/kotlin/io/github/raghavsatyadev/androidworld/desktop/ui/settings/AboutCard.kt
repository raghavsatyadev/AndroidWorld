package io.github.raghavsatyadev.androidworld.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard

const val PRIVACY_POLICY_URL = "https://raghavsatyadev.github.io/projects/androidworld/privacy/"
const val SOURCE_CODE_URL = "https://github.com/raghavsatyadev/AndroidWorld"
private const val ISSUES_URL = "$SOURCE_CODE_URL/issues"

/** Version, privacy policy and source links (Settings › About). */
@Composable
internal fun AboutCard() {
    // Set by jpackage in the installed app (MSI or MSIX); absent under `gradlew :desktop:run`.
    val version = System.getProperty("jpackage.app-version") ?: "development build"
    SectionCard(title = "About") {
        Text("AndroidWorld $version", style = MaterialTheme.typography.bodyLarge)
        Text(
            "No account, no analytics, nothing sent to us. Settings stay on this computer.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { openInBrowser(PRIVACY_POLICY_URL) }) { Text("Privacy policy") }
            TextButton(onClick = { openInBrowser(SOURCE_CODE_URL) }) { Text("Source code") }
            TextButton(onClick = { openInBrowser(ISSUES_URL) }) { Text("Report a problem") }
        }
    }
}
