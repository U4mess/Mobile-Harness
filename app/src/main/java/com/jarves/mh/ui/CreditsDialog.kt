package com.jarves.mh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credits and licence notices.
 *
 * Branding for this fork is presented separately from the upstream project's
 * attribution, and the licence texts are read verbatim from the bundled assets
 * so the shipped wording cannot drift from the repository copies.
 */
@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selections by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Resolved during composition: remember must not invoke composable functions.
    val appName = stringResource(R.string.app_name)
    val notices = listOf(
        stringResource(R.string.credits_open_license) to stringResource(R.string.credits_license_asset),
        stringResource(R.string.credits_notice_proot) to stringResource(R.string.credits_asset_proot),
        stringResource(R.string.credits_notice_talloc) to stringResource(R.string.credits_asset_talloc),
        stringResource(R.string.credits_notice_shmem) to stringResource(R.string.credits_asset_shmem),
        stringResource(R.string.credits_notice_claude) to stringResource(R.string.credits_asset_claude),
    )

    var loaded by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            notices.associate { (_, asset) ->
                asset to runCatching {
                    context.assets.open(asset).bufferedReader().use { it.readText() }
                }.getOrElse { error ->
                    context.getString(
                        R.string.credits_load_failed,
                        asset,
                    ) + "\n\n" + (error.message ?: error::class.java.simpleName)
                }
            }
        }
        loaded = result
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.credits_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    Text(appName, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.app_branded_by),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                }

                Text(
                    stringResource(R.string.credits_based_on),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )

                SourceLinkRow(
                    label = stringResource(R.string.credits_fork_source),
                    url = stringResource(R.string.credits_fork_url),
                )
                SourceLinkRow(
                    label = stringResource(R.string.credits_original_source),
                    url = stringResource(R.string.credits_original_url),
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.credits_third_party).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    notices.forEach { (label, asset) ->
                        val expanded = asset in selections
                        NoticeRow(
                            label = label,
                            expanded = expanded,
                            onClick = {
                                selections = if (expanded) selections - asset else selections + asset
                            },
                        )
                        if (expanded) {
                            val text = loaded[asset]
                            if (text == null) {
                                Text(
                                    stringResource(R.string.credits_loading),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = text,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.credits_close)) }
        },
    )
}

@Composable
private fun SourceLinkRow(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(url) } }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                url,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeRow(label: String, expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "−" else "+",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Welcome-screen entry point to the same credits dialog used by Settings. */
@Composable
fun CreditsFooterButton() {
    var showCredits by remember { mutableStateOf(false) }
    TextButton(onClick = { showCredits = true }) {
        Text(stringResource(R.string.credits_title), fontSize = 12.sp)
    }
    if (showCredits) {
        CreditsDialog(onDismiss = { showCredits = false })
    }
}
