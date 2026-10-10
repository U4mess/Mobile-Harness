package com.jarves.mh.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.nio.file.Files
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OutputApk(val path: String, val size: Long, val modified: Long) {
    val name: String get() = path.substringAfterLast('/')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildOutputsDialog(root: File?, busy: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apks by remember(root?.absolutePath) { mutableStateOf(emptyList<OutputApk>()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSize by rememberSaveable { mutableStateOf(0L) }
    var pendingTime by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(root?.absolutePath, refresh) {
        loading = true
        message = null
        try {
            val result = withContext(Dispatchers.IO) {
                val base = root?.canonicalFile ?: error("No project is open")
                val basePath = base.toPath()
                var visited = 0
                val found = base.walkTopDown().onEnter { dir ->
                    dir == base || (dir.name !in setOf(".git", ".gradle", ".cxx", "node_modules", ".venv", ".cache") &&
                        !Files.isSymbolicLink(dir.toPath()) && dir.canonicalFile.toPath().startsWith(basePath))
                }.take(100_001).mapNotNull { file ->
                    visited++
                    if (visited > 100_000 || !file.isFile || !file.extension.equals("apk", true) ||
                        Files.isSymbolicLink(file.toPath()) || !file.canonicalFile.toPath().startsWith(basePath)) null
                    else OutputApk(file.relativeTo(base).invariantSeparatorsPath, file.length(), file.lastModified())
                }.toList().sortedByDescending { it.modified }
                found to (visited > 100_000)
            }
            apks = result.first
            if (result.second) message = "Search limit reached. Some APKs may not be listed."
        } catch (error: Exception) {
            message = "Search failed: ${error.message}"
        } finally { loading = false }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        val path = pendingPath
        val expectedSize = pendingSize
        val expectedTime = pendingTime
        pendingPath = null
        if (uri != null && path != null) {
            saving = true
            progress = 0f
            message = "Saving APK..."
            scope.launch {
                try {
                    check(!busy) { "Wait for running tasks and builds to finish" }
                    withContext(Dispatchers.IO) {
                        val base = root?.canonicalFile ?: error("Project is unavailable")
                        val source = File(base, path).canonicalFile
                        require(source.toPath().startsWith(base.toPath()) && source.isFile &&
                            source.extension.equals("apk", true)) { "APK is unavailable. Refresh the list." }
                        require(source.length() == expectedSize && source.lastModified() == expectedTime) {
                            "APK changed. Refresh the list and select it again."
                        }
                        var copied = 0L
                        var lastUpdate = 0L
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            source.inputStream().buffered().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    copied += count
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastUpdate >= 150) {
                                        lastUpdate = now
                                        withContext(Dispatchers.Main) {
                                            progress = (copied.toFloat() / expectedSize.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                                output.flush()
                            }
                        } ?: error("Destination could not be opened")
                        require(copied == expectedSize && source.length() == expectedSize && source.lastModified() == expectedTime) {
                            "APK changed during saving. Retry after the build finishes."
                        }
                    }
                    progress = 1f
                    message = "Saved ${path.substringAfterLast('/')}"
                } catch (error: Exception) {
                    message = "Save failed: ${error.message}. A partial file may remain at the destination; remove it before retrying."
                } finally { saving = false }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
            Text("Build outputs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Save an APK without exporting the whole project", fontSize = 12.sp)
            TextButton(onClick = { refresh++ }, enabled = !loading && !saving) { Text("Refresh APK list") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (saving) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text("Saving: ${(progress * 100).toInt()}%", fontSize = 12.sp)
            }
            message?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            if (busy) Text("Wait for running tasks and builds to finish before saving.", fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!loading && apks.isEmpty()) item { Text("No APKs found. Build your project, then tap Refresh.") }
                items(apks, key = { it.path }) { apk ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(apk.name, fontWeight = FontWeight.SemiBold)
                            Text("%.1f MB".format(apk.size / 1048576.0) + " | Modified " +
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(apk.modified)), fontSize = 11.sp)
                            SelectionContainer { Text(apk.path, fontSize = 11.sp) }
                            Button(enabled = !busy && !saving && pendingPath == null,
                                modifier = Modifier.fillMaxWidth(), onClick = {
                                    pendingPath = apk.path
                                    pendingSize = apk.size
                                    pendingTime = apk.modified
                                    launcher.launch(apk.name)
                                }) { Text("Save APK") }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
