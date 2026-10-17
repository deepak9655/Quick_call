package com.quickcall.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.quickcall.app.data.Contact
import com.quickcall.app.ui.theme.QuickCallTheme
import com.quickcall.app.util.Parser
import com.quickcall.app.viewmodel.ContactViewModel
import java.io.File

val STATUS_LABEL = mapOf("new" to "New", "called" to "Called", "interested" to "Interested", "callback" to "Callback", "notinterested" to "Not interested", "voicemail" to "Voicemail")
val STATUS_COLOR = mapOf("new" to Color(0xFF1D4ED8), "called" to Color(0xFF15803D), "interested" to Color(0xFFB45309), "callback" to Color(0xFF4338CA), "notinterested" to Color(0xFFB91C1C), "voicemail" to Color(0xFF7E22CE))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf("light") }
            QuickCallTheme(dark = themeMode != "light", amoled = themeMode == "amoled") {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    QuickCallApp(themeMode = themeMode, onToggleTheme = { themeMode = when (themeMode) { "light" -> "dark"; "dark" -> "amoled"; else -> "light" } })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCallApp(themeMode: String, onToggleTheme: () -> Unit, vm: ContactViewModel = viewModel()) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    val status by vm.status.collectAsState()
    val processing by vm.processing.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("date") }
    var showImport by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf<Contact?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }
    var manualNotes by remember { mutableStateOf("") }
    var settingsCountry by remember { mutableStateOf("+91") }
    var settingsTemplate by remember { mutableStateOf("Hi {name}, this is a quick call regarding...") }
    var settingsDefaultStatus by remember { mutableStateOf("new") }
    var cardStyle by remember { mutableStateOf("comfortable") }
    var hideWhatsApp by remember { mutableStateOf(false) }
    var tapToCall by remember { mutableStateOf(true) }
    var showNotes by remember { mutableStateOf(true) }
    var showStatus by remember { mutableStateOf(true) }
    var showCallCount by remember { mutableStateOf(true) }
    var showLastCalled by remember { mutableStateOf(true) }
    var showSms by remember { mutableStateOf(false) }

    val filtered = remember(contacts, query, filter, sort) {
        var list = contacts.filter { c ->
            val q = query.lowercase()
            val matchQ = c.name.lowercase().contains(q) || c.phone.contains(q) || c.notes.lowercase().contains(q)
            val matchF = when (filter) {
                "all" -> true
                "favorites" -> c.favorite
                "called" -> c.status == "called"
                else -> c.status == filter
            }
            matchQ && matchF
        }
        list = when (sort) {
            "name" -> list.sortedBy { it.name }
            "status" -> list.sortedBy { it.status }
            else -> list.sortedByDescending { it.createdAt }
        }
        list
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.importFromUri(context, it) }
    }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = photoUri
        if (success && uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    val img = InputImage.fromBitmap(bmp, 0)
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(img)
                        .addOnSuccessListener { res -> vm.importText(res.text) }
                        .addOnFailureListener { vm.importText("") }
                } else vm.importText("")
            } catch (e: Exception) { vm.importText("") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Quick Call", fontWeight = FontWeight.Bold)
                    Text("${contacts.size} contacts", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                } },
                actions = {
                    IconButton(onClick = onToggleTheme) { Icon(when (themeMode) { "light" -> Icons.Default.DarkMode; "dark" -> Icons.Default.Brightness2; else -> Icons.Default.LightMode }, contentDescription = "Theme", tint = Color.White) }
                    IconButton(onClick = { showStats = true }) { Icon(Icons.Default.BarChart, contentDescription = "Stats", tint = Color.White) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
                    IconButton(onClick = { vm.removeMany(contacts.map { it.id }.toSet()) }) { Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2563EB), titleContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showManual = true }, containerColor = Color(0xFF2563EB)) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Stats row
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Total", contacts.size, Color(0xFF2563EB), Modifier.weight(1f))
                StatCard("Called", contacts.count { it.status == "called" }, Color(0xFF16A34A), Modifier.weight(1f))
                StatCard("Interested", contacts.count { it.status == "interested" }, Color(0xFFD97706), Modifier.weight(1f))
                StatCard("Pending", contacts.count { it.status == "new" }, Color(0xFF7C3AED), Modifier.weight(1f))
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search name, number or notes...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            // Filter chips
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("all","new","called","interested","callback","notinterested","voicemail","favorites")) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(if (f == "all") "All" else if (f == "favorites") "★ Fav" else STATUS_LABEL[f] ?: f, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Import card
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📥 Import Contacts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ImportBtn("Camera", Color(0xFF7C3AED), Icons.Default.CameraAlt) {
                            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES); dir?.mkdirs()
                            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            photoUri = uri; cameraLauncher.launch(uri)
                        }
                        ImportBtn("Image", Color(0xFF2563EB), Icons.Default.Image) { filePicker.launch("image/*") }
                        ImportBtn("PDF", Color(0xFFDC2626), Icons.Default.PictureAsPdf) { filePicker.launch("application/pdf") }
                        ImportBtn("Excel", Color(0xFF16A34A), Icons.Default.TableChart) { filePicker.launch("*/*") }
                        ImportBtn("Word", Color(0xFF0D9488), Icons.Default.Description) { filePicker.launch("*/*") }
                        ImportBtn("File", Color(0xFF64748B), Icons.Default.UploadFile) { filePicker.launch("*/*") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Batch bar
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { batchMode = !batchMode }) { Text(if (batchMode) "Exit" else "Select") }
                if (batchMode && selected.isNotEmpty()) {
                    Button(onClick = { vm.removeMany(selected.toSet()); selected.clear(); batchMode = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("Delete (${selected.size})") }
                }
                OutlinedButton(onClick = {
                    // export CSV via share? just toast
                }) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(4.dp)); Text("Export") }
                OutlinedButton(onClick = { showManual = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add") }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No contacts", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Import a list above or add manually", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { c ->
                        ContactCard(
                            contact = c,
                            batchMode = batchMode,
                            selected = selected.contains(c.id),
                            compact = cardStyle == "compact",
                            hideWhatsApp = hideWhatsApp,
                            tapToCall = tapToCall,
                            showNotes = showNotes,
                            showStatus = showStatus,
                            showCallCount = showCallCount,
                            showLastCalled = showLastCalled,
                            showSms = showSms,
                            onSelect = { if (selected.contains(c.id)) selected.remove(c.id) else selected.add(c.id) },
                            onCall = { vm.markCalled(c.id); try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Parser.cleanPhone(c.phone)}"))) } catch (e: Exception) {} },
                            onWhatsApp = { try { val n = Parser.cleanPhone(c.phone).removePrefix("+"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n"))) } catch (e: Exception) {} },
                            onSms = { try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Parser.cleanPhone(c.phone)}"))) } catch (e: Exception) {} },
                            onFav = { vm.toggleFavorite(c.id) },
                            onStatus = { vm.setStatus(c.id, it) },
                            onEdit = { showEdit = c },
                            onDelete = { vm.remove(c.id) }
                        )
                    }
                }
            }
        }
    }

    if (processing) {
        AlertDialog(onDismissRequest = {}, title = { Text("Processing…") }, text = { Text("Please wait") }, confirmButton = {})
    }
    if (showManual) {
        AlertDialog(
            onDismissRequest = { showManual = false },
            title = { Text("Add Contact") },
            text = {
                Column {
                    OutlinedTextField(value = manualName, onValueChange = { manualName = it }, label = { Text("Name") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = manualPhone, onValueChange = { manualPhone = it }, label = { Text("Phone number") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = manualNotes, onValueChange = { manualNotes = it }, label = { Text("Notes") })
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.addManual(manualName, manualPhone, manualNotes); manualName = ""; manualPhone = ""; manualNotes = ""; showManual = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showManual = false }) { Text("Cancel") } }
        )
    }
    showEdit?.let { c ->
        AlertDialog(
            onDismissRequest = { showEdit = null },
            title = { Text("Edit Contact") },
            text = {
                Column {
                    OutlinedTextField(value = c.name, onValueChange = { showEdit = c.copy(name = it) }, label = { Text("Name") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = c.phone, onValueChange = { showEdit = c.copy(phone = it) }, label = { Text("Phone") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = c.notes, onValueChange = { showEdit = c.copy(notes = it) }, label = { Text("Notes") })
                }
            },
            confirmButton = { TextButton(onClick = { showEdit?.let { vm.update(it) } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showEdit = null }) { Text("Cancel") } }
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    OutlinedTextField(value = settingsCountry, onValueChange = { settingsCountry = it }, label = { Text("Default country code") })
                    OutlinedTextField(value = settingsTemplate, onValueChange = { settingsTemplate = it }, label = { Text("WhatsApp template ({name})") })
                    OutlinedTextField(value = settingsDefaultStatus, onValueChange = { settingsDefaultStatus = it }, label = { Text("Default status for imports") })
                    Spacer(Modifier.height(8.dp))
                    Text("Card appearance", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Compact cards", modifier = Modifier.weight(1f))
                        Switch(checked = cardStyle == "compact", onCheckedChange = { cardStyle = if (it) "compact" else "comfortable" })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tap card to call", modifier = Modifier.weight(1f))
                        Switch(checked = tapToCall, onCheckedChange = { tapToCall = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide WhatsApp", modifier = Modifier.weight(1f))
                        Switch(checked = hideWhatsApp, onCheckedChange = { hideWhatsApp = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show SMS", modifier = Modifier.weight(1f))
                        Switch(checked = showSms, onCheckedChange = { showSms = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show notes", modifier = Modifier.weight(1f))
                        Switch(checked = showNotes, onCheckedChange = { showNotes = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show status", modifier = Modifier.weight(1f))
                        Switch(checked = showStatus, onCheckedChange = { showStatus = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show call count", modifier = Modifier.weight(1f))
                        Switch(checked = showCallCount, onCheckedChange = { showCallCount = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show last called", modifier = Modifier.weight(1f))
                        Switch(checked = showLastCalled, onCheckedChange = { showLastCalled = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Close") } }
        )
    }
    if (showStats) {
        AlertDialog(
            onDismissRequest = { showStats = false },
            title = { Text("Statistics") },
            text = {
                Column {
                    STATUS_LABEL.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(v); Text("${contacts.count { it.status == k }}", fontWeight = FontWeight.Bold)
                        }
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total calls"); Text("${contacts.sumOf { it.callCount }}", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Favorites"); Text("${contacts.count { it.favorite }}", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStats = false }) { Text("Close") } }
        )
    }
    if (status.isNotEmpty()) {
        LaunchedEffect(status) { }
    }
}

@Composable
fun StatCard(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ImportBtn(label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
        Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = color, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Text(label, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun ContactCard(contact: Contact, batchMode: Boolean, selected: Boolean, compact: Boolean, hideWhatsApp: Boolean, tapToCall: Boolean, showNotes: Boolean, showStatus: Boolean, showCallCount: Boolean, showLastCalled: Boolean, showSms: Boolean, onSelect: () -> Unit, onCall: () -> Unit, onWhatsApp: () -> Unit, onSms: () -> Unit, onFav: () -> Unit, onStatus: (String) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(if (tapToCall && !batchMode) Modifier.clickable { onCall() } else Modifier),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (batchMode) {
                    Checkbox(checked = selected, onCheckedChange = { onSelect() })
                    Spacer(Modifier.width(4.dp))
                }
                Text(contact.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = onFav) { Icon(if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = "Favorite", tint = if (contact.favorite) Color(0xFFD97706) else Color.Gray) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray) }
            }
            Text(contact.phone, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (showStatus) {
                    Surface(color = STATUS_COLOR[contact.status]?.copy(alpha = 0.15f) ?: Color.Gray.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(STATUS_LABEL[contact.status] ?: contact.status, color = STATUS_COLOR[contact.status] ?: Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (showCallCount && contact.callCount > 0) {
                    Surface(color = Color.Gray.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("📞 ${contact.callCount}", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                if (showLastCalled && contact.calledAt != null) {
                    Surface(color = Color.Gray.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text(java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(contact.calledAt)), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            if (showNotes && contact.notes.isNotBlank()) {
                Text("\"${contact.notes}\"", fontSize = 13.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCall, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Call")
                }
                if (!hideWhatsApp) {
                    Button(onClick = onWhatsApp, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))) {
                        Icon(Icons.Default.Message, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp")
                    }
                }
                if (showSms) {
                    Button(onClick = onSms, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))) {
                        Icon(Icons.Default.Sms, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("SMS")
                    }
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Status", fontSize = 12.sp) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        STATUS_LABEL.forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { onStatus(k); expanded = false })
                        }
                    }
                }
            }
        }
    }
}
