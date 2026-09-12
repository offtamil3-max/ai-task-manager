package com.offtamil.aitaskmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Bg = Color(0xFF070A12)
private val Card = Color(0xFF101725)
private val Card2 = Color(0xFF141D2E)
private val Cyan = Color(0xFF26D9FF)
private val Purple = Color(0xFF8B5CFF)
private val Green = Color(0xFF31E59A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
        if (savedInstanceState == null) handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (text.isNotBlank() || uri != null) {
            val name = "Shared AI Chat " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
            Storage.createProject(this, name, text, listOfNotNull(uri))
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Project?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { Storage.importZip(context, uri); refresh++ }
    }

    val projects = remember(refresh) { Storage.listProjects(context) }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Card, primary = Cyan)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            when {
                selected != null -> ProjectScreen(selected!!, onBack = { selected = null }, onRefresh = { refresh++ })
                showNew -> NewProjectScreen(onBack = { showNew = false }, onCreated = { showNew = false; refresh++ })
                showImport -> ImportScreen(onBack = { showImport = false }, onPick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) })
                else -> Scaffold(
                    containerColor = Bg,
                    bottomBar = { BottomNav(tab) { tab = it } }
                ) { pad ->
                    Column(Modifier.fillMaxSize().padding(pad)) {
                        Header()
                        when (tab) {
                            0 -> HomeScreen(projects, onNew = { showNew = true }, onImport = { showImport = true }, onOpen = { selected = it }, onRefresh = { refresh++ })
                            1 -> ProjectsScreen(projects, onOpen = { selected = it }, onNew = { showNew = true })
                            2 -> TransferScreen(projects, onOpen = { selected = it })
                            else -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Header() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Purple, Cyan)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column { Text("AI Task Manager", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Save • Transfer • Continue", color = Color(0xFF8D9AAF), fontSize = 12.sp) }
    }
}

@Composable private fun HomeScreen(projects: List<Project>, onNew: () -> Unit, onImport: () -> Unit, onOpen: (Project) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.linearGradient(listOf(Color(0xFF151C35), Color(0xFF0C2735))), RoundedCornerShape(26.dp)).padding(22.dp)) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Your AI work,", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text("always portable.", color = Cyan, fontSize = 27.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); Text("Capture a chat from any AI using Android Share.", color = Color(0xFF9AA8BB)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SmallPill("NO API", Cyan); SmallPill("ONE TAP", Green); SmallPill("LOCAL", Purple) }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard("Capture", "Share from AI", Icons.Default.Share, Cyan, Modifier.weight(1f)) { onNew() }
            ActionCard("Import", "Backup ZIP", Icons.Default.FileDownload, Green, Modifier.weight(1f)) { onImport() }
        } }
        item { Text("Recent projects", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }
        if (projects.isEmpty()) item { EmptyState(onNew) }
        items(projects.take(8), key = { it.id }) { ProjectRow(it, onOpen) }
    }
}

@Composable private fun ProjectsScreen(projects: List<Project>, onOpen: (Project) -> Unit, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("My Projects", fontSize = 25.sp, fontWeight = FontWeight.Bold); FilledTonalButton(onClick = onNew) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("New") } }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(projects, key = { it.id }) { ProjectRow(it, onOpen) } }
    }
}

@Composable private fun TransferScreen(projects: List<Project>, onOpen: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Transfer", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Export a portable checkpoint, then share it to another AI app.", color = Color(0xFF91A0B4), modifier = Modifier.padding(top = 4.dp)) }
        item { InfoCard("How it works", listOf("1  Capture the chat with Android Share", "2  Keep text + files in a local project", "3  Export one ZIP checkpoint", "4  Share/open it in another AI app")) }
        item { Text("Ready to transfer", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
        items(projects, key = { it.id }) { ProjectRow(it, onOpen) }
    }
}

@Composable private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SettingRow(Icons.Default.Security, "Privacy", "Projects stay in app storage")
        SettingRow(Icons.Default.Folder, "Storage", "Local workspace + exported ZIPs")
        SettingRow(Icons.Default.Share, "Sharing", "Uses the Android Sharesheet")
        SettingRow(Icons.Default.Info, "About", "AI Task Manager • v1.0")
    }
}

@Composable private fun NewProjectScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }; var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        BackTitle("New Project", onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(180.dp), label = { Text("Paste / capture chat") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { Storage.createProject(LocalContext.current, name.ifBlank { "AI Project" }, text, emptyList()); onCreated() }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Create Checkpoint") }
        Spacer(Modifier.height(10.dp)); Text("Tip: the easiest workflow is AI → Share → AI Task Manager.", color = Color(0xFF7F8CA0), fontSize = 12.sp)
    }
}

@Composable private fun ImportScreen(onBack: () -> Unit, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        BackTitle("Import Checkpoint", onBack); Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().height(230.dp).background(Card, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.FolderZip, null, tint = Cyan, modifier = Modifier.size(58.dp)); Spacer(Modifier.height(12.dp)); Text("Restore a ZIP checkpoint", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Project files + chat + metadata", color = Color(0xFF8795AA)); Spacer(Modifier.height(18.dp)); Button(onClick = onPick) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(7.dp)); Text("Choose ZIP") } }
        }
    }
}

@Composable private fun ProjectScreen(project: Project, onBack: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        BackTitle(project.name, onBack)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallPill(project.ai, Cyan); SmallPill("CHECKPOINT", Green); SmallPill("LOCAL", Purple) }
        Spacer(Modifier.height(14.dp))
        InfoCard("Progress", listOf("Captured: ${project.date}", "Messages/text: ${project.text.length} characters", "Attachments: ${project.attachments} file(s)", "Status: Ready to continue"))
        Spacer(Modifier.height(12.dp))
        Text("Chat / Context", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(Card, RoundedCornerShape(18.dp)).padding(15.dp)) { LazyColumn { item { Text(project.text.ifBlank { "No chat text captured. You can add context by creating another checkpoint." }, color = Color(0xFFD5DCEA), fontSize = 14.sp) } } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { Storage.shareProject(context, project) }, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
            Button(onClick = { exporting = true; Storage.exportProject(context, project); exporting = false }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(if (exporting) "Exporting…" else "Export ZIP") }
        }
        Spacer(Modifier.height(6.dp)); Text("Share sends the checkpoint through Android to any compatible AI app.", color = Color(0xFF77869B), fontSize = 11.sp)
    }
}

@Composable private fun ProjectRow(p: Project, onOpen: (Project) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(17.dp)).clickable { onOpen(p) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Purple, Cyan)), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("AI", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.SemiBold); Text("${p.ai} • ${p.attachments} files • ${p.date}", color = Color(0xFF8290A5), fontSize = 11.sp); Text(if (p.text.isBlank()) "No text captured" else p.text.replace("\n", " ").take(72), color = Color(0xFFB2BDCC), fontSize = 12.sp, maxLines = 1) }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748A))
    }
}

@Composable private fun ActionCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(Card, RoundedCornerShape(19.dp)).clickable(onClick = onClick).padding(16.dp)) { Icon(icon, null, tint = tint, modifier = Modifier.size(27.dp)); Spacer(Modifier.height(10.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(sub, color = Color(0xFF8190A4), fontSize = 11.sp) }
}
@Composable private fun SmallPill(text: String, tint: Color) { Surface(color = tint.copy(alpha = .13f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = .35f))) { Text(text, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) } }
@Composable private fun InfoCard(title: String, lines: List<String>) { Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(20.dp)).padding(17.dp)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(10.dp)); lines.forEach { Row(Modifier.padding(vertical = 4.dp)) { Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(it, color = Color(0xFFB8C3D3), fontSize = 12.sp) } } } }
@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) { Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Cyan); Spacer(Modifier.width(14.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(sub, color = Color(0xFF7F8CA0), fontSize = 12.sp) } } }
@Composable private fun EmptyState(onNew: () -> Unit) { Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Inventory2, null, tint = Color(0xFF66758A), modifier = Modifier.size(45.dp)); Spacer(Modifier.height(8.dp)); Text("No projects yet", fontWeight = FontWeight.Bold); Text("Capture your first AI chat with Share.", color = Color(0xFF7F8CA0)); Spacer(Modifier.height(12.dp)); Button(onClick = onNew) { Text("Create Project") } } }
@Composable private fun BackTitle(title: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) { NavigationBar(containerColor = Color(0xFF090D17)) { listOf("Home" to Icons.Default.Home, "Projects" to Icons.Default.Folder, "Transfer" to Icons.Default.SwapHoriz, "Settings" to Icons.Default.Settings).forEachIndexed { i, pair -> NavigationBarItem(selected = selected == i, onClick = { onSelect(i) }, icon = { Icon(pair.second, null) }, label = { Text(pair.first, fontSize = 10.sp) }) } } }

private data class Project(val id: String, val name: String, val ai: String, val date: String, val text: String, val attachments: Int, val dir: File)

private object Storage {
    private fun root(c: android.content.Context) = File(c.filesDir, "projects").apply { mkdirs() }
    fun listProjects(c: android.content.Context): List<Project> = root(c).listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }?.sortedByDescending { it.dir.lastModified() } ?: emptyList()
    private fun read(dir: File): Project? = try { val j = JSONObject(File(dir, "metadata.json").readText()); Project(j.getString("id"), j.getString("name"), j.optString("ai", "Shared AI"), j.optString("date", ""), File(dir, "chat.txt").takeIf { it.exists() }?.readText().orEmpty(), dir.resolve("attachments").listFiles()?.size ?: 0, dir) } catch (_: Exception) { null }
    fun createProject(c: android.content.Context, name: String, text: String, uris: List<Uri>) {
        val id = "p_" + System.currentTimeMillis(); val d = File(root(c), id); val a = File(d, "attachments"); a.mkdirs(); File(d, "chat.txt").writeText(text)
        val copied = uris.mapIndexed { i, u -> try { c.contentResolver.openInputStream(u)?.use { input -> FileOutputStream(File(a, "attachment_$i")).use { input.copyTo(it) } }; true } catch (_: Exception) { false } }.count { it }
        val j = JSONObject().put("id", id).put("name", name).put("ai", "Shared AI").put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())).put("attachments", copied)
        File(d, "metadata.json").writeText(j.toString(2)); File(d, "instructions.md").writeText("Continue this task from the supplied checkpoint. Inspect the restored files and chat context before continuing. Do not restart completed work.")
    }
    fun exportProject(c: android.content.Context, p: Project) { try { val out = File(c.cacheDir, "${p.id}.zip"); ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z -> p.dir.walkTopDown().filter { it.isFile }.forEach { f -> z.putNextEntry(ZipEntry(p.dir.toPath().relativize(f.toPath()).toString())); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }; shareFile(c, out, "application/zip") } catch (_: Exception) {} }
    fun shareProject(c: android.content.Context, p: Project) { val out = File(c.cacheDir, "${p.id}_checkpoint.zip"); try { ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z -> p.dir.walkTopDown().filter { it.isFile }.forEach { f -> z.putNextEntry(ZipEntry(p.dir.toPath().relativize(f.toPath()).toString())); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }; shareFile(c, out, "application/zip") } catch (_: Exception) {} }
    private fun shareFile(c: android.content.Context, f: File, mime: String) { val uri = FileProvider.getUriForFile(c, "com.offtamil.aitaskmanager.files", f); val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, "AI Task checkpoint. Restore/open the ZIP, inspect the included chat and files, then continue from the checkpoint."); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; c.startActivity(Intent.createChooser(i, "Share checkpoint to AI")) }
    fun importZip(c: android.content.Context, uri: Uri) { try { val id = "import_" + System.currentTimeMillis(); val d = File(root(c), id); d.mkdirs(); ZipInputStream(BufferedInputStream(c.contentResolver.openInputStream(uri)!!)).use { z -> var e = z.nextEntry; while (e != null) { val out = File(d, e.name).canonicalFile; if (!out.path.startsWith(d.canonicalPath + File.separator)) { e = z.nextEntry; continue }; if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); FileOutputStream(out).use { z.copyTo(it) } }; z.closeEntry(); e = z.nextEntry } }; if (!File(d, "metadata.json").exists()) File(d, "metadata.json").writeText(JSONObject().put("id", id).put("name", "Imported Checkpoint").put("ai", "Imported").put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())).toString()) } catch (_: Exception) {} }
}
