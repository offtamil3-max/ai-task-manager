package com.offtamil.aitaskmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private val BG = Color(0xFF070A12)
private val CARD = Color(0xFF101725)
private val CYAN = Color(0xFF26D9FF)
private val PURPLE = Color(0xFF8B5CFF)
private val GREEN = Color(0xFF31E59A)

data class Project(
    val id: String,
    val name: String,
    val date: String,
    val text: String,
    val files: Int,
    val dir: File
)

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiTaskManagerApp() }
        if (savedInstanceState == null) captureIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureIntent(intent)
    }

    private fun captureIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (text.isNotBlank() || stream != null) {
            Store.create(this, "Shared AI Chat", text, listOfNotNull(stream))
        }
    }
}

@Composable
private fun AiTaskManagerApp() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Project?>(null) }
    var newProject by remember { mutableStateOf(false) }
    var importProject by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            Store.importZip(context, uri)
            refresh++
        }
    }
    val projects = remember(refresh) { Store.list(context) }

    MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = CARD, primary = CYAN)) {
        Surface(Modifier.fillMaxSize(), color = BG) {
            when {
                selected != null -> ProjectDetail(selected!!, { selected = null })
                newProject -> CreateProject({ newProject = false }) { name, text ->
                    Store.create(context, name, text, emptyList())
                    newProject = false
                    refresh++
                }
                importProject -> ImportProject({ importProject = false }) {
                    picker.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                else -> Scaffold(
                    containerColor = BG,
                    bottomBar = { BottomNav(tab) { tab = it } }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        Header()
                        when (tab) {
                            0 -> Home(projects, { newProject = true }, { importProject = true }) { selected = it }
                            1 -> Projects(projects) { selected = it }
                            2 -> Transfer(projects) { selected = it }
                            else -> Settings()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Header() {
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(Brush.linearGradient(listOf(PURPLE, CYAN)), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("AI Task Manager", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Save • Transfer • Continue", color = Color(0xFF8997AA), fontSize = 12.sp)
        }
    }
}

@Composable private fun Home(ps: List<Project>, onNew: () -> Unit, onImport: () -> Unit, onOpen: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF171D38), Color(0xFF0B2835))), RoundedCornerShape(25.dp)).padding(22.dp)) {
                Text("Your AI work,", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("always portable.", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = CYAN)
                Spacer(Modifier.height(8.dp))
                Text("AI → Share → Save → Export → Another AI", color = Color(0xFF9AA8BB))
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("NO API", CYAN); Pill("ONE TAP", GREEN); Pill("LOCAL", PURPLE) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("Capture", "Share from any AI", Icons.Default.Share, CYAN, Modifier.weight(1f), onNew)
                Action("Import", "Checkpoint ZIP", Icons.Default.FileDownload, GREEN, Modifier.weight(1f), onImport)
            }
        }
        item { Text("Recent projects", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (ps.isEmpty()) item { Empty(onNew) }
        items(ps.take(8), key = { it.id }) { RowItem(it, onOpen) }
    }
}

@Composable private fun Projects(ps: List<Project>, onOpen: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("My Projects", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Portable AI checkpoints stored locally.", color = Color(0xFF8997AA), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) }
        items(ps, key = { it.id }) { RowItem(it, onOpen) }
    }
}

@Composable private fun Transfer(ps: List<Project>, onOpen: (Project) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Transfer", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Move a checkpoint between AI apps with Android Share.", color = Color(0xFF8997AA)) }
        item { Info("WORKFLOW", listOf("Capture a chat from Claude, ChatGPT, Gemini or any AI", "Keep chat and shared files locally", "Export one ZIP checkpoint", "Share/open the checkpoint in another AI")) }
        items(ps, key = { it.id }) { RowItem(it, onOpen) }
    }
}

@Composable private fun Settings() {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Info("LOCAL-FIRST", listOf("No provider API keys required", "No AI passwords stored", "Projects stay in app storage", "Android Sharesheet handles transfer"))
    }
}

@Composable private fun CreateProject(onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Title("New Checkpoint", onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(190.dp), label = { Text("Chat / context") })
        Spacer(Modifier.height(16.dp))
        Button({ onSave(name.ifBlank { "AI Project" }, text) }, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = PURPLE)) {
            Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("Save Checkpoint")
        }
    }
}

@Composable private fun ImportProject(onBack: () -> Unit, onPick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Title("Import Checkpoint", onBack)
        Spacer(Modifier.height(25.dp))
        Box(Modifier.fillMaxWidth().height(250.dp).background(CARD, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderZip, null, tint = CYAN, modifier = Modifier.size(55.dp))
                Spacer(Modifier.height(10.dp)); Text("Restore portable ZIP", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Chat + files + metadata", color = Color(0xFF8997AA)); Spacer(Modifier.height(18.dp))
                Button(onPick) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Choose ZIP") }
            }
        }
    }
}

@Composable private fun ProjectDetail(p: Project, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Title(p.name, onBack)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("CHECKPOINT", GREEN); Pill("LOCAL", PURPLE) }
        Spacer(Modifier.height(12.dp))
        Info("PROJECT", listOf("Captured ${p.date}", "${p.files} attachment(s)", "${p.text.length} characters", "Ready to continue"))
        Spacer(Modifier.height(12.dp))
        Text("Chat / Context", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().weight(1f).background(CARD, RoundedCornerShape(18.dp)).padding(15.dp)) {
            LazyColumn { item { Text(p.text.ifBlank { "No text captured." }, color = Color(0xFFD4DCEA), fontSize = 14.sp) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ Store.share(context, p) }, Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") }
            Button({ Store.export(context, p) }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = PURPLE)) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(5.dp)); Text("Export ZIP") }
        }
    }
}

@Composable private fun RowItem(p: Project, open: (Project) -> Unit) {
    Row(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(17.dp)).clickable { open(p) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(43.dp).background(Brush.linearGradient(listOf(PURPLE, CYAN)), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("AI", color = Color.White, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold)
            Text("${p.date} • ${p.files} files", color = Color(0xFF7F8DA2), fontSize = 11.sp)
            Text(p.text.replace("\n", " ").take(70), color = Color(0xFFB2BDCC), fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF65748A))
    }
}

@Composable private fun Action(title: String, sub: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(CARD, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(15.dp)) { Icon(icon, null, tint=tint, modifier=Modifier.size(27.dp)); Spacer(Modifier.height(9.dp)); Text(title,fontWeight=FontWeight.Bold); Text(sub,color=Color(0xFF8190A4),fontSize=11.sp) }
}
@Composable private fun Info(title: String, lines: List<String>) { Column(Modifier.fillMaxWidth().background(CARD,RoundedCornerShape(20.dp)).padding(16.dp)){Text(title,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));lines.forEach{Row(Modifier.padding(vertical=4.dp)){Icon(Icons.Default.CheckCircle,null,tint=GREEN,modifier=Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text(it,color=Color(0xFFB8C3D3),fontSize=12.sp)}}} }
@Composable private fun Pill(text:String,tint:Color){Surface(color=tint.copy(alpha=.12f),shape=RoundedCornerShape(50),border=BorderStroke(1.dp,tint.copy(alpha=.3f))){Text(text,color=tint,fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp))}}
@Composable private fun Empty(onNew:()->Unit){Column(Modifier.fillMaxWidth().padding(25.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Inventory2,null,tint=Color(0xFF66758A),modifier=Modifier.size(45.dp));Spacer(Modifier.height(8.dp));Text("No projects yet",fontWeight=FontWeight.Bold);Text("Capture your first AI chat with Share.",color=Color(0xFF7F8CA0));Spacer(Modifier.height(12.dp));Button(onNew){Text("Create Project")}}}
@Composable private fun Title(text:String,back:()->Unit){Row(verticalAlignment=Alignment.CenterVertically){IconButton(back){Icon(Icons.Default.ArrowBack,"Back")};Text(text,fontSize=23.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun BottomNav(selected:Int,onSelect:(Int)->Unit){NavigationBar(containerColor=Color(0xFF090D17)){val entries=listOf("Home" to Icons.Default.Home,"Projects" to Icons.Default.Folder,"Transfer" to Icons.Default.SwapHoriz,"Settings" to Icons.Default.Settings);entries.forEachIndexed{i,(label,icon)->NavigationBarItem(selected=selected==i,onClick={onSelect(i)},icon={Icon(icon,null)},label={Text(label,fontSize=10.sp)})}}}

private object Store {
    private fun root(c: Context) = File(c.filesDir, "projects").apply { mkdirs() }
    fun list(c: Context): List<Project> = root(c).listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }?.sortedByDescending { it.dir.lastModified() } ?: emptyList()
    private fun read(d: File): Project? = try { val j=JSONObject(File(d,"metadata.json").readText()); Project(j.getString("id"),j.getString("name"),j.optString("date",""),File(d,"chat.txt").takeIf{it.exists()}?.readText().orEmpty(),d.resolve("attachments").listFiles()?.size ?: 0,d) } catch(_:Exception){null}
    fun create(c: Context,name:String,text:String,uris:List<Uri>){val id="p_"+System.currentTimeMillis();val d=File(root(c),id);val a=File(d,"attachments");a.mkdirs();File(d,"chat.txt").writeText(text);uris.forEachIndexed{i,u->try{c.contentResolver.openInputStream(u)?.use{input->FileOutputStream(File(a,"attachment_$i")).use{out->input.copyTo(out)}}}catch(_:Exception){}};File(d,"metadata.json").writeText(JSONObject().put("id",id).put("name",name).put("date",SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date())).toString(2));File(d,"instructions.md").writeText("Continue from this checkpoint. Inspect the supplied chat/context and files. Do not redo completed work.")}
    private fun zip(c:Context,p:Project):File{val f=File(c.cacheDir,p.id+".zip");ZipOutputStream(BufferedOutputStream(FileOutputStream(f))).use{z->p.dir.walkTopDown().filter{it.isFile}.forEach{x->z.putNextEntry(ZipEntry(p.dir.toPath().relativize(x.toPath()).toString()));x.inputStream().use{it.copyTo(z)};z.closeEntry()}};return f}
    fun export(c:Context,p:Project)=shareFile(c,zip(c,p)); fun share(c:Context,p:Project)=shareFile(c,zip(c,p))
    private fun shareFile(c:Context,f:File){val u=FileProvider.getUriForFile(c,"com.offtamil.aitaskmanager.files",f);val i=Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,u);putExtra(Intent.EXTRA_TEXT,"AI Task checkpoint. Open this ZIP, inspect the included chat/context and files, then continue the task from the checkpoint.");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};c.startActivity(Intent.createChooser(i,"Share checkpoint to AI"))}
    fun importZip(c:Context,u:Uri){try{val id="import_"+System.currentTimeMillis();val d=File(root(c),id);d.mkdirs();ZipInputStream(BufferedInputStream(c.contentResolver.openInputStream(u)!!)).use{z->var e=z.nextEntry;while(e!=null){val out=File(d,e.name).canonicalFile;if(out.path.startsWith(d.canonicalPath+File.separator)){if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();FileOutputStream(out).use{z.copyTo(it)}}};z.closeEntry();e=z.nextEntry}};if(!File(d,"metadata.json").exists())File(d,"metadata.json").writeText(JSONObject().put("id",id).put("name","Imported Checkpoint").put("date",SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date())).toString(2))}catch(_:Exception){}}
}
