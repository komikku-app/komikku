package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun WhatsNewDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = { Text(text = stringResource(MR.strings.updated_version, BuildConfig.VERSION_NAME)) },
        // KMK -->
        dismissButton = {
            TextButton(onClick = { context.openInBrowser(RELEASE_URL) }) {
                Text(text = stringResource(KMR.strings.changelogs))
            }
        },
        // KMK <--
        text = {
            Column {
                val changelog by produceState<List<DisplayChangelog>?>(initialValue = null) {
                    value = withIOContext {
                        XML.decodeFromReader<Changelog>(
                            AndroidXmlReader(
                                context.resources.openRawResource(
                                    if (isPreviewBuildType) R.raw.changelog_preview else R.raw.changelog_release,
                                ).bufferedReader(),
                            ),
                        ).toDisplayChangelog()
                    }
                }
                if (changelog != null) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(changelog.orEmpty()) { changelog ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(SYMR.strings.changelog_version, changelog.version),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                                ) {
                                    changelog.changelog.forEach {
                                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

data class DisplayChangelog(
    val version: String,
    val changelog: List<AnnotatedString>,
)

@Serializable
@XmlSerialName("changelog", "", "")
data class Changelog(
    val bulletedList: Boolean,
    val changelogs: List<ChangelogVersion>,
)

@Serializable
@XmlSerialName("changelogversion", "", "")
data class ChangelogVersion(
    val versionName: String,
    val changeDate: String,
    val text: List<ChangelogText>,
)

@Serializable
@XmlSerialName("changelogtext", "", "")
data class ChangelogText(
    @XmlValue(true) val value: String,
)

private const val bullet = "\u2022"

fun Changelog.toDisplayChangelog(): List<DisplayChangelog> {
    val prefix = if (bulletedList) bullet + "\t\t" else ""
    return changelogs.map { version ->
        DisplayChangelog(
            version = version.versionName,
            changelog = version.text.mapIndexed { index, changelogText ->
                buildAnnotatedString {
                    append(prefix)
                    var inBBCode = false
                    var isEscape = false
                    changelogText.value.forEachIndexed { charIndex, c ->
                        if (!inBBCode && c == '[') {
                            inBBCode = true
                        } else if (inBBCode && c == ']') {
                            inBBCode = false
                            isEscape = false
                        } else if (inBBCode && c == '/') {
                            isEscape = true
                        } else if (inBBCode && c == 'b') {
                            if (isEscape) {
                                try {
                                    pop()
                                } catch (e: IllegalStateException) {
                                    throw Exception("Exception on ${version.versionName}:$index:$charIndex", e)
                                }
                            } else {
                                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            }
                        } else {
                            append(c)
                        }
                    }
                }
            },
        )
    }
}
