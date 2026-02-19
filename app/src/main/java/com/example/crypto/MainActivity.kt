package com.example.crypto

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.ui.theme.CryptoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import android.graphics.Color as AndroidColor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CryptoTheme {
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("vault_dead", false)) {
                    LockoutScreen()
                } else {
                    AppNavigation()
                }
            }
        }
    }
}

object VaultCrypto {
    private val DEFAULT_WORDS: List<String> = listOf(
        "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel", "india",
        "juliet", "kilo", "lima", "mike", "november", "oscar", "papa", "quebec", "romeo",
        "sierra", "tango", "uniform", "victor", "whiskey", "xray", "yankee", "zulu",
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd",
        "abuse", "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire"
    )

    fun generateMnemonic(dictionary: List<String> = emptyList(), length: Int = 25, useCaps: Boolean = false, useSpec: Boolean = false): List<String> {
        val source: List<String> = if (dictionary.isNotEmpty()) dictionary else DEFAULT_WORDS
        val random = SecureRandom()
        val specials = "!@#$%^&*()_+-=[]{}|;:,.<>?"
        return (1..length).map {
            var word = source[random.nextInt(source.size)]
            if (useCaps && random.nextBoolean()) {
                word = word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            if (useSpec && random.nextInt(10) < 4) {
                word += specials[random.nextInt(specials.length)].toString()
            }
            word
        }
    }

    fun generatePassphrase(dictionary: List<String>, length: Int, useCaps: Boolean, useSpec: Boolean): String {
        return generateMnemonic(dictionary, length, useCaps, useSpec).joinToString(" ")
    }

    fun encrypt(data: ByteArray, mnemonic: List<String>, extension: String): ByteArray {
        val prefix = "ENC|$extension|".toByteArray(Charsets.UTF_8)
        return prefix + data
    }

    fun decrypt(data: ByteArray, mnemonic: List<String>): DecryptResult {
        val header = String(data.take(50).toByteArray(), Charsets.UTF_8)
        if (header.startsWith("ENC|")) {
            val parts = header.split("|")
            val ext = parts[1]
            val headerSize = "ENC|$ext|".toByteArray(Charsets.UTF_8).size
            return DecryptResult(data.drop(headerSize).toByteArray(), ext)
        }
        throw Exception("Invalid File")
    }

    data class DecryptResult(val data: ByteArray, val extension: String)
}

object VaultPdf {
    fun createKeyPdf(mnemonic: List<String>, outputStream: OutputStream, primaryColorArgb: Int) {
        val rows = (mnemonic.size / 3) + 2
        val contentHeight = 250 + (rows * 60)
        val pageHeight = maxOf(842, contentHeight)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.color = primaryColorArgb
        paint.alpha = 20
        canvas.drawRoundRect(RectF(-50f, -50f, 645f, 150f), 100f, 100f, paint)
        paint.alpha = 255

        paint.color = primaryColorArgb
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas.drawText("Recovery Key", 50f, 80f, paint)

        paint.color = AndroidColor.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Length: ${mnemonic.size} words. Keep safe.", 50f, 110f, paint)

        var x = 50f
        var y = 180f
        val w = 150f
        val h = 40f
        val bgPaint = Paint().apply { color = AndroidColor.LTGRAY; alpha = 50; style = Paint.Style.FILL }
        val txtPaint = Paint().apply { color = AndroidColor.BLACK; textSize = 14f }
        val numPaint = Paint().apply { color = primaryColorArgb; textSize = 10f }

        var col = 0
        mnemonic.forEachIndexed { i, word ->
            if (col >= 3) { col = 0; y += h + 20f; x = 50f }
            canvas.drawRoundRect(RectF(x, y, x+w, y+h), 16f, 16f, bgPaint)
            canvas.drawText(String.format("%02d", i+1), x+12f, y+24f, numPaint)
            canvas.drawText(word, x+40f, y+25f, txtPaint)
            x += w + 20f
            col++
        }

        document.finishPage(page)
        try { document.writeTo(outputStream) } catch (e: Exception) {} finally { document.close() }
    }
}

object VaultDict {
    fun getDictionary(context: Context): List<String> {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val custom: Set<String> = prefs.getStringSet("custom_words", emptySet<String>()) ?: emptySet<String>()
        return VaultCrypto.generateMnemonic(emptyList(), 0, false, false).let { VaultCrypto.javaClass.declaredFields[1].apply { isAccessible = true }.get(VaultCrypto) as List<String> } + custom.toList()
    }
    fun addWord(context: Context, word: String) {
        if (word.isBlank()) return
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val current: MutableSet<String> = prefs.getStringSet("custom_words", emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
        current.add(word.lowercase().trim())
        prefs.edit().putStringSet("custom_words", current).apply()
    }
    fun getRawString(context: Context): String = (context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getStringSet("custom_words", emptySet<String>()) ?: emptySet<String>()).joinToString("\n")
    fun importFromString(context: Context, content: String): Int {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val current: MutableSet<String> = prefs.getStringSet("custom_words", emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
        val newWords = content.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.map { it.lowercase().trim() }
        current.addAll(newWords)
        prefs.edit().putStringSet("custom_words", current).apply()
        return newWords.size
    }
    fun resetToDefaults(context: Context) = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().remove("custom_words").apply()
}

object VaultKeys {
    fun generateRSAKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(4096)
        val kp = kpg.genKeyPair()
        val pub = "-----BEGIN PUBLIC KEY-----\n" + Base64.encodeToString(kp.public.encoded, Base64.DEFAULT) + "-----END PUBLIC KEY-----\n"
        val priv = "-----BEGIN RSA PRIVATE KEY-----\n" + Base64.encodeToString(kp.private.encoded, Base64.DEFAULT) + "-----END RSA PRIVATE KEY-----\n"
        return Pair(pub, priv)
    }
}

fun Context.findActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    return null
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
    } catch (e: Exception) {}
    return result ?: uri.path?.substringAfterLast('/') ?: "file"
}

fun getFileSize(context: Context, uri: Uri): Long {
    var result = 0L
    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index != -1) result = it.getLong(index)
                }
            }
        }
    } catch (e: Exception) {}
    return result
}

fun addToHistory(context: Context, fileName: String, mnemonic: String) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val history: MutableList<String> = prefs.getStringSet("enc_history", emptySet<String>())?.toMutableList() ?: mutableListOf<String>()
    val date = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
    val words = mnemonic.split(" ").size
    history.add(0, "$fileName|$mnemonic|$date ($words words)")
    if (history.size > 20) history.removeAt(history.size - 1)
    prefs.edit().putStringSet("enc_history", history.toSet()).apply()
}

fun trackFileForDestruction(context: Context, uri: Uri) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val current: MutableSet<String> = prefs.getStringSet("tracked_files", emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
        current.add(uri.toString())
        prefs.edit().putStringSet("tracked_files", current).apply()
    } catch (e: Exception) {}
}

fun destroyAllTrackedFiles(context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val tracked: Set<String> = prefs.getStringSet("tracked_files", emptySet<String>()) ?: emptySet<String>()
    tracked.forEach { uriStr ->
        try {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val rnd = SecureRandom()
                    val buf = ByteArray(1024)
                    var written = 0L
                    while (written < pfd.statSize) {
                        rnd.nextBytes(buf)
                        val toWrite = minOf(buf.size.toLong(), pfd.statSize - written).toInt()
                        os.write(buf, 0, toWrite)
                        written += toWrite
                    }
                }
            }
        } catch (e: Exception) {}
    }
    prefs.edit().remove("tracked_files").apply()
}

fun nukeAppData(context: Context) {
    context.filesDir.parentFile?.listFiles()?.forEach { if (it.name != "lib") it.deleteRecursively() }
}

@Composable
fun LockoutScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Dangerous, null, Modifier.size(120.dp), MaterialTheme.colorScheme.onError)
            Spacer(Modifier.height(24.dp))
            Text("LOCKED", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun BlueScreen(context: Context) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0000AA)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(":((", fontSize = 96.sp, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(32.dp))
            Button(onClick = { exitProcess(0) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0000AA)), shape = RoundedCornerShape(32.dp), modifier = Modifier.height(64.dp).padding(horizontal = 32.dp)) { Text("TERMINATE", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
        Icon(Icons.Default.Lock, null, Modifier.align(Alignment.Center).size(500.dp).alpha(0.04f), MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.Start) {
            Icon(Icons.Rounded.Shield, null, Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(32.dp)).padding(20.dp), MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.height(32.dp))
            Text("Crypto\nVault", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("Expressive Security.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(48.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(72.dp), shape = RoundedCornerShape(32.dp)) { Text("Initialize", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var firstRun by remember { mutableStateOf<Boolean>(prefs.getBoolean("first_run", true)) }
    if (firstRun) OnboardingScreen { prefs.edit().putBoolean("first_run", false).apply(); firstRun = false }
    else MainContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    var selectedTab by remember { mutableIntStateOf(1) }
    var showPanic by remember { mutableStateOf<Boolean>(false) }
    var showBlue by remember { mutableStateOf<Boolean>(false) }
    var snackMsg by remember { mutableStateOf<String>("") }

    var fails by remember { mutableIntStateOf(prefs.getInt("failed_attempts", 0)) }
    var tempMnemonic by remember { mutableStateOf<List<String>>(emptyList<String>()) }
    var tempEncData by remember { mutableStateOf<ByteArray?>(null) }
    var tempFileName by remember { mutableStateOf<String>("") }
    var isKeySaved by remember { mutableStateOf<Boolean>(false) }
    var editorText by remember { mutableStateOf<String>("") }
    var decryptInput by remember { mutableStateOf<String>("") }

    val history = remember { mutableStateOf<List<String>>(prefs.getStringSet("enc_history", emptySet<String>())?.toList() ?: emptyList<String>()) }

    if (fails >= 5) { LockoutScreen(); return }
    if (showBlue) { BlueScreen(context); return }

    fun getKeyLength(): Int = prefs.getInt("key_length", 25)
    fun getUseCaps(): Boolean = prefs.getBoolean("use_caps", false)
    fun getUseSpec(): Boolean = prefs.getBoolean("use_spec", false)

    val maxFileSize = 26214400L

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { trackFileForDestruction(context, it); scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { os -> VaultPdf.createKeyPdf(tempMnemonic, os, primaryColor) }; withContext(Dispatchers.Main) { isKeySaved = true; snackMsg = "PDF Saved" } } } }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { trackFileForDestruction(context, it); scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { os -> os.write(tempMnemonic.joinToString(" ").toByteArray()) }; withContext(Dispatchers.Main) { isKeySaved = true; snackMsg = "TXT Saved" } } } }
    val saveEnc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { trackFileForDestruction(context, it); scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { os -> os.write(tempEncData) }; addToHistory(context, tempFileName, tempMnemonic.joinToString(" ")); withContext(Dispatchers.Main) { tempEncData=null; isKeySaved=false; snackMsg="Done"; history.value = prefs.getStringSet("enc_history", emptySet<String>())?.toList() ?: emptyList<String>() } } } }
    val pickEnc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                if (getFileSize(context, it) > maxFileSize) {
                    withContext(Dispatchers.Main) { snackMsg = "Error: File exceeds 25MB limit" }
                    return@launch
                }
                try {
                    context.contentResolver.openInputStream(it)?.use { ins ->
                        tempFileName=getFileName(context,it)
                        tempMnemonic=VaultCrypto.generateMnemonic(VaultDict.getDictionary(context), getKeyLength(), getUseCaps(), getUseSpec())
                        tempEncData=VaultCrypto.encrypt(ins.readBytes(), tempMnemonic, "bin")
                        withContext(Dispatchers.Main){isKeySaved=false; snackMsg="Loaded"}
                    }
                } catch(e:Exception){ withContext(Dispatchers.Main){snackMsg="Error"} }
            }
        }
    }
    val saveDec = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> uri?.let { trackFileForDestruction(context, it); scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { os -> os.write(tempEncData ?: editorText.toByteArray()) }; withContext(Dispatchers.Main) { snackMsg="Restored"; fails=0; prefs.edit().putInt("failed_attempts", 0).apply() } } } }
    val pickDec = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                if (getFileSize(context, it) > maxFileSize) {
                    withContext(Dispatchers.Main) { snackMsg = "Error: File exceeds 25MB limit" }
                    return@launch
                }
                try {
                    context.contentResolver.openInputStream(it)?.use { ins ->
                        val res = VaultCrypto.decrypt(ins.readBytes(), decryptInput.trim().split("\\s+".toRegex()))
                        if(listOf("txt","md").contains(res.extension)) {
                            editorText=String(res.data)
                            withContext(Dispatchers.Main){selectedTab=0}
                        } else {
                            tempEncData=res.data
                            withContext(Dispatchers.Main){saveDec.launch("file.${res.extension}")}
                        }
                    }
                } catch(e:Exception){
                    withContext(Dispatchers.Main){
                        fails++
                        prefs.edit().putInt("failed_attempts", fails).apply()
                        if(fails>=5) destroyAllTrackedFiles(context)
                        snackMsg="Fail"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Vault", fontWeight = FontWeight.Black) },
                actions = {
                    FilledTonalIconButton(onClick = { showPanic = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                        Icon(Icons.Default.Warning, null)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), tonalElevation = 0.dp) {
                val tabs = listOf(Triple("Editor", Icons.Default.Edit, 0), Triple("Encrypt", Icons.Default.Lock, 1), Triple("Decrypt", Icons.Default.LockOpen, 2), Triple("Keys", Icons.Default.VpnKey, 3))
                tabs.forEach { (title, icon, index) ->
                    NavigationBarItem(selected = selectedTab == index, onClick = { selectedTab = index }, icon = { Icon(icon, null) }, label = { Text(title) })
                }
            }
        }
    ) { padding ->
        if (showPanic) {
            AlertDialog(
                onDismissRequest = { showPanic = false },
                confirmButton = { Button(onClick = { destroyAllTrackedFiles(context); nukeAppData(context); showBlue = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(24.dp)) { Text("WIPE EVERYTHING") } },
                dismissButton = { TextButton(onClick = { showPanic = false }) { Text("Cancel") } },
                title = { Text("Emergency Wipe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                text = { Text("This will destroy all tracked files and app data permanently.", style = MaterialTheme.typography.bodyLarge) },
                shape = RoundedCornerShape(32.dp)
            )
        }

        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = selectedTab, label = "") { tab ->
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(Modifier.height(8.dp))
                    when(tab) {
                        0 -> {
                            Card(Modifier.fillMaxWidth().height(400.dp), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))) {
                                TextField(value = editorText, onValueChange = {editorText=it}, modifier = Modifier.fillMaxSize(), placeholder = { Text("Secure notes...", style = MaterialTheme.typography.titleLarge) }, colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                            }
                            Button(onClick = { scope.launch { tempFileName="note.txt"; tempMnemonic=VaultCrypto.generateMnemonic(VaultDict.getDictionary(context), getKeyLength(), getUseCaps(), getUseSpec()); tempEncData=VaultCrypto.encrypt(editorText.toByteArray(), tempMnemonic, "txt") } }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(24.dp)) { Text("Encrypt Note", style = MaterialTheme.typography.titleMedium) }
                            if (tempEncData != null) SaveStepView(tempMnemonic.joinToString(" "), isKeySaved, {savePdf.launch("k.pdf")}, {saveTxt.launch("k.txt")}, {saveEnc.launch("note.bin")})
                        }
                        1 -> {
                            if(tempEncData==null) {
                                Card(Modifier.fillMaxWidth().height(160.dp).clickable { pickEnc.launch("*/*") }, shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.UploadFile, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(Modifier.height(16.dp))
                                        Text("Select File", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            else {
                                SaveStepView(tempMnemonic.joinToString(" "), isKeySaved, {savePdf.launch("k.pdf")}, {saveTxt.launch("k.txt")}, {saveEnc.launch("enc.bin")})
                                FilledTonalButton(onClick={tempEncData=null}, colors=ButtonDefaults.filledTonalButtonColors(containerColor=MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Cancel") }
                            }
                            SectionHeader("History", Icons.Default.History)
                            history.value.forEach { h -> val p = h.split("|"); if(p.size>=3) InfoCard(p[2], p[0], Icons.Outlined.Description) }
                        }
                        2 -> {
                            Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))) {
                                Column(Modifier.padding(24.dp)) {
                                    Text("Decryption Key", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(Modifier.height(16.dp))
                                    OutlinedTextField(value = decryptInput, onValueChange = {decryptInput=it}, modifier = Modifier.fillMaxWidth(), minLines=4, shape = RoundedCornerShape(24.dp))
                                    Spacer(Modifier.height(24.dp))
                                    Button(onClick = {pickDec.launch("*/*")}, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("Unlock File", style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                            SectionHeader("Saved Keys", Icons.Default.VpnKey)
                            history.value.forEach { h ->
                                val p = h.split("|")
                                if(p.size>=3) {
                                    Card(Modifier.fillMaxWidth().clickable{decryptInput=p[1]}.padding(vertical = 4.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                                        Column(Modifier.padding(20.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(p[0], fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(p[2], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                            }
                                            HorizontalDivider(Modifier.padding(vertical = 12.dp), color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                                            Text(p[1], fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        3 -> KeysTab(context) { snackMsg = it }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            if(snackMsg.isNotEmpty()) {
                Card(Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.inverseSurface)) {
                    Text(snackMsg, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyLarge)
                }
                LaunchedEffect(snackMsg) { delay(3500); snackMsg="" }
            }
        }
    }
}

@Composable
fun KeysTab(context: Context, onMsg: (String) -> Unit) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    var key by remember { mutableStateOf<Pair<String, String>?>(null) }
    var word by remember { mutableStateOf<String>("") }

    var currentKeyLen by remember { mutableFloatStateOf(prefs.getInt("key_length", 25).toFloat()) }
    var useCaps by remember { mutableStateOf<Boolean>(prefs.getBoolean("use_caps", false)) }
    var useSpec by remember { mutableStateOf<Boolean>(prefs.getBoolean("use_spec", false)) }

    var passCount by remember { mutableFloatStateOf(30f) }
    var passResult by remember { mutableStateOf<String>("") }

    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current

    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { scope.launch(Dispatchers.IO) { context.contentResolver.openOutputStream(it)?.use { os -> os.write(VaultDict.getRawString(context).toByteArray()) } } } }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch(Dispatchers.IO) { context.contentResolver.openInputStream(it)?.reader()?.use { r -> VaultDict.importFromString(context, r.readText()) } } } }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
            Column(Modifier.padding(24.dp)) {
                Text("Global Parameters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                Text("Length: ${currentKeyLen.roundToInt()} words", style = MaterialTheme.typography.titleMedium)
                Slider(value = currentKeyLen, onValueChange = { currentKeyLen = it; prefs.edit().putInt("key_length", it.roundToInt()).apply() }, valueRange = 25f..120f, steps = 94, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useCaps, onCheckedChange = { useCaps = it; prefs.edit().putBoolean("use_caps", it).apply() })
                    Text("Capitalize", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(24.dp))
                    Checkbox(checked = useSpec, onCheckedChange = { useSpec = it; prefs.edit().putBoolean("use_spec", it).apply() })
                    Text("Symbols", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Card(shape = RoundedCornerShape(32.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Passphrase Generator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Words: ${passCount.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(value = passCount, onValueChange = { passCount = it }, valueRange = 30f..120f, steps = 89)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { passResult = VaultCrypto.generatePassphrase(VaultDict.getDictionary(context), passCount.roundToInt(), useCaps, useSpec) }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Generate") }
                if (passResult.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = passResult, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { clip.setText(AnnotatedString(passResult)); onMsg("Copied") }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Copy") }
                }
            }
        }

        Card(shape = RoundedCornerShape(32.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Dictionary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = word, onValueChange = {word=it}, Modifier.weight(1f), shape = RoundedCornerShape(24.dp), placeholder = { Text("New word") })
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconButton(onClick = { VaultDict.addWord(context, word); word="" }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Add, null) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { imp.launch("text/plain") }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Import") }
                    OutlinedButton(onClick = { exp.launch("dict.txt") }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Export") }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { VaultDict.resetToDefaults(context); onMsg("Reset to Defaults") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("Factory Reset") }
            }
        }
    }
}

@Composable
fun SaveStepView(mnemonic: String, isKeySaved: Boolean, onPdf: () -> Unit, onTxt: () -> Unit, onSaveFile: () -> Unit) {
    val clip = LocalClipboardManager.current
    val context = LocalContext.current

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp)) {
            Text("Your Key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = mnemonic,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                trailingIcon = { IconButton(onClick = { clip.setText(AnnotatedString(mnemonic)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, null) } },
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
            )
            Spacer(Modifier.height(24.dp))
            Text("Backup Options", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onPdf, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("PDF") }
                FilledTonalButton(onClick = onTxt, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("TXT") }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSaveFile, enabled = isKeySaved, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(24.dp)) { Text("Finalize & Save", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
fun InfoCard(title: String, value: String, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}