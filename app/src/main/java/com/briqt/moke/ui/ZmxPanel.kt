/* Added for Resume Terminal, a personal Moke fork, 2026-09-30 to 2026-10-01.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.briqt.moke.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.briqt.moke.R
import com.briqt.moke.terminal.Zmx
import com.briqt.moke.terminal.ZmxPhase
import com.briqt.moke.terminal.ZmxSession
import com.briqt.moke.terminal.ZmxUiState

/** Phone-side tabs for real remote PTYs, including sessions created from the desktop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZmxPanel(
    state: ZmxUiState,
    currentName: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onKill: (String) -> Unit,
    onInstall: () -> Unit,
    canInstall: Boolean = true,
) {
    var showNew by remember { mutableStateOf(false) }
    var killTarget by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 12.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.zmx_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !state.busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.tmux_retry))
                }
                TextButton(onClick = { showNew = true }, enabled = state.phase == ZmxPhase.READY && !state.busy) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.zmx_new))
                }
            }
            HorizontalDivider()
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 8.dp))
            }
            state.notice?.let {
                Text(it, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            }
            when (state.phase) {
                ZmxPhase.IDLE, ZmxPhase.CHECKING -> Row(
                    Modifier.padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.zmx_checking))
                }
                ZmxPhase.INSTALLING -> Row(
                    Modifier.padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.zmx_installing))
                }
                ZmxPhase.NOT_INSTALLED, ZmxPhase.ERROR -> Column(
                    Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.zmx_not_installed))
                    Text(stringResource(R.string.zmx_install_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onInstall, enabled = !state.busy && canInstall) {
                        Text(stringResource(R.string.zmx_install))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.zmx_keep_plain_shell))
                    }
                }
                ZmxPhase.READY -> {
                    if (state.sessions.isEmpty()) {
                        Text(stringResource(R.string.zmx_empty), modifier = Modifier.padding(vertical = 20.dp))
                    }
                    Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        state.sessions.sortedWith(
                            compareByDescending<ZmxSession> { it.name == currentName }
                                .thenByDescending { it.created }
                        ).forEach { session ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f).clickable(enabled = !state.busy) {
                                        onOpen(session.name)
                                        onDismiss()
                                    }.padding(vertical = 8.dp),
                                ) {
                                    Text(session.name, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium)
                                    Text(
                                        listOfNotNull(
                                            session.cwd.takeIf { it.isNotBlank() }?.let(::displayPath),
                                            stringResource(R.string.zmx_client_count, session.clients),
                                            stringResource(R.string.zmx_current).takeIf { session.name == currentName },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                TextButton(onClick = { killTarget = session.name }, enabled = !state.busy) {
                                    Text(stringResource(R.string.zmx_stop), color = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    if (showNew) ZmxNameDialog(
        onDismiss = { showNew = false },
        onSubmit = { showNew = false; onOpen(it); onDismiss() },
    )
    killTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { killTarget = null },
            title = { Text(stringResource(R.string.zmx_stop)) },
            text = { Text(stringResource(R.string.zmx_kill_confirm, name)) },
            confirmButton = {
                TextButton(onClick = { killTarget = null; onKill(name) }) {
                    Text(stringResource(R.string.zmx_stop), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { killTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun ZmxPickerDialog(
    sessions: List<ZmxSession>,
    onPick: (String) -> Unit,
    onPlainShell: () -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onPlainShell,
        title = { Text(stringResource(R.string.zmx_title)) },
        text = {
            Column {
                Text(stringResource(R.string.zmx_picker_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    if (sessions.isEmpty()) Text(stringResource(R.string.zmx_empty),
                        modifier = Modifier.padding(vertical = 16.dp))
                    sessions.sortedByDescending { it.created }.forEach { session ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(session.name) }
                            .padding(vertical = 12.dp)) {
                            Text(session.name, style = MaterialTheme.typography.bodyLarge)
                            Text(displayPath(session.cwd), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showNew = true }) { Text(stringResource(R.string.zmx_new)) } },
        dismissButton = { TextButton(onClick = onPlainShell) { Text(stringResource(R.string.zmx_plain_shell)) } },
    )
    if (showNew) ZmxNameDialog(onDismiss = { showNew = false }, onSubmit = onPick)
}

@Composable
private fun ZmxNameDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zmx_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.zmx_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(trimmed) }, enabled = Zmx.validName(trimmed)) {
                Text(stringResource(R.string.zmx_open_session))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun displayPath(cwd: String): String =
    runCatching { java.net.URI(cwd).path?.takeIf { it.isNotBlank() } ?: cwd }.getOrDefault(cwd)
