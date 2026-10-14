package com.teatr.stagetechnician

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val BgBlack = Color(0xFF050505)
val CardDark = Color(0xFF141414)
val TextAmber = Color(0xFFFFD60A)
val TextGreen = Color(0xFF30D158)
val TextRed = Color(0xFFFF453A)
val TextCyan = Color(0xFF64D2FF)

data class StageCrewMember(
    val id: String,
    val name: String,
    val baseStation: String,
    val assignedFunctions: List<String>,
    val requiredTools: String,
    val folderName: String
)

data class TurntableTask(
    val direction: String,
    val speed: String,
    val targetMark: String,
    val assignedTechId: String,
    val warning: String = ""
)

data class FurnitureTask(
    val item: String,
    val action: String,
    val assignedTechId: String,
    val spikeColor: Color,
    val spikeName: String,
    val details: String
)

data class FlyBarTask(
    val barNumber: Int,
    val name: String,
    val direction: String,
    val targetMark: String,
    val assignedTechId: String
)

data class StageCueChange(
    val number: Int,
    val actAndTitle: String,
    val triggerCue: String,
    val turntable: TurntableTask? = null,
    val furniture: List<FurnitureTask> = emptyList(),
    val flyBars: List<FlyBarTask> = emptyList()
)

class StageStorageManager(private val context: Context) {

    private fun getRootShowsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Spektakle")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAvailableShows(): List<String> {
        val folders = getRootShowsDir().listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        return folders.ifEmpty { listOf("Wesele_Duza_Scena", "Dziady_Krakow") }
    }

    fun initFoldersForShow(showTitle: String, crewList: List<StageCrewMember>) {
        val showDir = File(getRootShowsDir(), showTitle)
        File(showDir, "Montaz").mkdirs()
        val crewRoot = File(showDir, "Obsluga_Zmiany_Pracownicy")
        crewRoot.mkdirs()
        crewList.forEach { member ->
            File(crewRoot, member.folderName).mkdirs()
        }
    }

    fun getStationFolderForTech(showTitle: String, folderName: String): File {
        val dir = File(getRootShowsDir(), "$showTitle/Obsluga_Zmiany_Pracownicy/$folderName")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPhotosForShowAndStation(showTitle: String, folderName: String): List<File> {
        val dir = getStationFolderForTech(showTitle, folderName)
        return dir.listFiles { file ->
            file.isFile && (file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun saveShowDataToJson(
        showTitle: String,
        crewList: List<StageCrewMember>,
        changesList: List<StageCueChange>
    ) {
        try {
            val showDir = File(getRootShowsDir(), showTitle)
            if (!showDir.exists()) showDir.mkdirs()
            val jsonFile = File(showDir, "spektakl_dane.json")

            val rootObj = JSONObject()
            rootObj.put("showTitle", showTitle)

            val crewArray = JSONArray()
            crewList.forEach { member ->
                val obj = JSONObject().apply {
                    put("id", member.id)
                    put("name", member.name)
                    put("baseStation", member.baseStation)
                    put("assignedFunctions", JSONArray(member.assignedFunctions))
                    put("requiredTools", member.requiredTools)
                    put("folderName", member.folderName)
                }
                crewArray.put(obj)
            }
            rootObj.put("crew", crewArray)

            val changesArray = JSONArray()
            changesList.forEach { cue ->
                val cueObj = JSONObject().apply {
                    put("number", cue.number)
                    put("actAndTitle", cue.actAndTitle)
                    put("triggerCue", cue.triggerCue)

                    cue.turntable?.let { tt ->
                        put("turntable", JSONObject().apply {
                            put("direction", tt.direction)
                            put("speed", tt.speed)
                            put("targetMark", tt.targetMark)
                            put("assignedTechId", tt.assignedTechId)
                            put("warning", tt.warning)
                        })
                    }

                    val furnArray = JSONArray()
                    cue.furniture.forEach { f ->
                        furnArray.put(JSONObject().apply {
                            put("item", f.item)
                            put("action", f.action)
                            put("assignedTechId", f.assignedTechId)
                            put("spikeColor", f.spikeColor.toArgb())
                            put("spikeName", f.spikeName)
                            put("details", f.details)
                        })
                    }
                    put("furniture", furnArray)

                    val flyArray = JSONArray()
                    cue.flyBars.forEach { fb ->
                        flyArray.put(JSONObject().apply {
                            put("barNumber", fb.barNumber)
                            put("name", fb.name)
                            put("direction", fb.direction)
                            put("targetMark", fb.targetMark)
                            put("assignedTechId", fb.assignedTechId)
                        })
                    }
                    put("flyBars", flyArray)
                }
                changesArray.put(cueObj)
            }
            rootObj.put("changes", changesArray)

            jsonFile.writeText(rootObj.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadShowDataFromJson(showTitle: String): Pair<List<StageCrewMember>, List<StageCueChange>>? {
        val jsonFile = File(getRootShowsDir(), "$showTitle/spektakl_dane.json")
        if (!jsonFile.exists()) return null

        return try {
            val rootObj = JSONObject(jsonFile.readText())

            val loadedCrew = mutableListOf<StageCrewMember>()
            val crewArray = rootObj.optJSONArray("crew") ?: JSONArray()
            for (i in 0 until crewArray.length()) {
                val obj = crewArray.getJSONObject(i)
                val funcsJson = obj.optJSONArray("assignedFunctions") ?: JSONArray()
                val funcs = mutableListOf<String>()
                for (j in 0 until funcsJson.length()) {
                    funcs.add(funcsJson.getString(j))
                }
                loadedCrew.add(
                    StageCrewMember(
                        id = obj.optString("id", "tech_${i + 1}"),
                        name = obj.optString("name", "Technik"),
                        baseStation = obj.optString("baseStation", "KULISA"),
                        assignedFunctions = funcs,
                        requiredTools = obj.optString("requiredTools", ""),
                        folderName = obj.optString("folderName", "Stanowisko_${i + 1}")
                    )
                )
            }

            val loadedChanges = mutableListOf<StageCueChange>()
            val changesArray = rootObj.optJSONArray("changes") ?: JSONArray()
            for (i in 0 until changesArray.length()) {
                val cueObj = changesArray.getJSONObject(i)

                val ttObj = cueObj.optJSONObject("turntable")
                val turntable = ttObj?.let {
                    TurntableTask(
                        direction = it.optString("direction", ""),
                        speed = it.optString("speed", ""),
                        targetMark = it.optString("targetMark", ""),
                        assignedTechId = it.optString("assignedTechId", "tech_3"),
                        warning = it.optString("warning", "")
                    )
                }

                val furnitureList = mutableListOf<FurnitureTask>()
                val furnArray = cueObj.optJSONArray("furniture") ?: JSONArray()
                for (j in 0 until furnArray.length()) {
                    val fObj = furnArray.getJSONObject(j)
                    furnitureList.add(
                        FurnitureTask(
                            item = fObj.optString("item", ""),
                            action = fObj.optString("action", ""),
                            assignedTechId = fObj.optString("assignedTechId", "tech_1"),
                            spikeColor = Color(fObj.optInt("spikeColor", TextAmber.toArgb())),
                            spikeName = fObj.optString("spikeName", "ŻÓŁTA SZPILKA"),
                            details = fObj.optString("details", "")
                        )
                    )
                }

                val flyBarsList = mutableListOf<FlyBarTask>()
                val flyArray = cueObj.optJSONArray("flyBars") ?: JSONArray()
                for (j in 0 until flyArray.length()) {
                    val fbObj = flyArray.getJSONObject(j)
                    flyBarsList.add(
                        FlyBarTask(
                            barNumber = fbObj.optInt("barNumber", 1),
                            name = fbObj.optString("name", ""),
                            direction = fbObj.optString("direction", "W DÓŁ"),
                            targetMark = fbObj.optString("targetMark", ""),
                            assignedTechId = fbObj.optString("assignedTechId", "tech_3")
                        )
                    )
                }

                loadedChanges.add(
                    StageCueChange(
                        number = cueObj.optInt("number", i + 1),
                        actAndTitle = cueObj.optString("actAndTitle", "ZMIANA ${i + 1}"),
                        triggerCue = cueObj.optString("triggerCue", ""),
                        turntable = turntable,
                        furniture = furnitureList,
                        flyBars = flyBarsList
                    )
                )
            }

            Pair(loadedCrew, loadedChanges)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportShowToZip(showTitle: String): File? {
        return try {
            val showDir = File(getRootShowsDir(), showTitle)
            if (!showDir.exists()) return null

            val exportDir = File(context.externalCacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val zipFile = File(exportDir, "${showTitle}_pelna_rozpiska.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                showDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(showDir.parentFile!!).path
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        file.inputStream().buffered().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareShowZip(showTitle: String) {
        val zipFile = exportShowToZip(showTitle)
        if (zipFile == null || !zipFile.exists()) {
            Toast.makeText(context, "Błąd podczas pakowania spektaklu!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                zipFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Rozpiska spektaklu: $showTitle")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "W załączniku pełna rozpiska zmian i zdjęcia szpilek dla: $showTitle."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Wyślij paczkę ZIP ze spektaklem"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Zapisano ZIP w: ${zipFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun importShowFromZip(uri: Uri): String? {
        return try {
            val rootSpektakleDir = getRootShowsDir()
            var importedShowName: String? = null

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val cleanPath = entry.name.replace("\\", "/").trimStart('/')
                        val parts = cleanPath.split("/").filter { it.isNotBlank() }

                        if (parts.isNotEmpty()) {
                            val relativeParts = if (parts[0] == "Spektakle" && parts.size > 1) {
                                parts.drop(1)
                            } else {
                                parts
                            }

                            if (relativeParts.isNotEmpty()) {
                                if (importedShowName == null) {
                                    importedShowName = relativeParts[0]
                                }

                                val relativePath = relativeParts.joinToString(File.separator)
                                val outFile = File(rootSpektakleDir, relativePath)

                                if (!outFile.canonicalPath.startsWith(rootSpektakleDir.canonicalPath)) {
                                    throw SecurityException("Nieprawidłowa ścieżka wewnątrz archiwum ZIP!")
                                }

                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).buffered().use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            importedShowName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteShowFolder(showTitle: String): Boolean {
        return try {
            val showDir = File(getRootShowsDir(), showTitle)
            if (showDir.exists()) showDir.deleteRecursively() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class MainActivity : ComponentActivity() {

    private var volumeNavigationHandler: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgBlack)) {
                StageTechnicianApp(
                    registerVolumeNavigation = { handler ->
                        volumeNavigationHandler = handler
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeNavigationHandler?.invoke(1)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeNavigationHandler?.invoke(-1)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StageTechnicianApp(registerVolumeNavigation: ((Int) -> Unit) -> Unit) {
    val context = LocalContext.current
    val storageManager = remember { StageStorageManager(context) }

    val defaultCrew = remember {
        listOf(
            StageCrewMember(
                id = "tech_1",
                name = "Marek",
                baseStation = "KULISA LEWA",
                assignedFunctions = listOf("Meble - Lewa Kulisa", "Wózki i Podesty (Ryglowanie)"),
                requiredTools = "Klucz grzechotka 13, żółty gaffer, czołówka czerwona",
                folderName = "Stanowisko_Marek_KULISA_LEW"
            ),
            StageCrewMember(
                id = "tech_2",
                name = "Tomek",
                baseStation = "KULISA PRAWA",
                assignedFunctions = listOf("Meble - Prawa Kulisa", "Wózki i Podesty (Ryglowanie)"),
                requiredTools = "Klin pod schody, różowy gaffer, interkom",
                folderName = "Stanowisko_Tomek_KULISA_PRA"
            ),
            StageCrewMember(
                id = "tech_3",
                name = "Piotrek",
                baseStation = "NAPĘDY / POMOST",
                assignedFunctions = listOf("Obrotówka (Sterowanie / Kliny)", "Sztankiety (Galeria / Pomost)"),
                requiredTools = "Interkom główny, rozpiska biegów falownika",
                folderName = "Stanowisko_Piotrek_NAPEDY"
            )
        )
    }

    val defaultInitialCue = remember {
        listOf(
            StageCueChange(
                number = 1,
                actAndTitle = "AKT I • ZMIANA 1 (Start spektaklu)",
                triggerCue = "Po słowach: „Ciemno wszędzie...” (Blackout 25 sek.)",
                turntable = TurntableTask(
                    direction = "OBRÓT W LEWO (CCW)",
                    speed = "PRĘDKOŚĆ: 45% (Bieg 2) • Łagodny start",
                    targetMark = "Jedzie o 180° -> STOP na naboju nr 6",
                    assignedTechId = "tech_3",
                    warning = "Sprawdź czysty obrys tarczy przed ruszeniem!"
                ),
                furniture = listOf(
                    FurnitureTask(
                        item = "Stół dębowy + 4 krzesła",
                        action = "WJAZD Z LEWEJ KULISY",
                        assignedTechId = "tech_1",
                        spikeColor = TextAmber,
                        spikeName = "ŻÓŁTA SZPILKA (Przód tarczy)",
                        details = "Zablokuj heble w wózku!"
                    )
                ),
                flyBars = listOf(
                    FlyBarTask(14, "Żyrandol dworski", "W DÓŁ (Wchodzi)", "Do czerwonej taśmy", "tech_3")
                )
            )
        )
    }

    var availableShows by remember { mutableStateOf(storageManager.getAvailableShows()) }
    var currentShowName by remember { mutableStateOf(availableShows.first()) }
    var showNewShowDialog by remember { mutableStateOf(false) }
    var showToDelete by remember { mutableStateOf<String?>(null) }

    var currentTab by remember { mutableIntStateOf(0) }

    var crewList by remember { mutableStateOf(defaultCrew) }
    var changesList by remember { mutableStateOf(defaultInitialCue) }
    var currentCueIndex by remember { mutableIntStateOf(0) }

    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val importedShow = storageManager.importShowFromZip(uri)
            if (importedShow != null) {
                availableShows = storageManager.getAvailableShows()
                currentShowName = importedShow
                showNewShowDialog = false
                Toast.makeText(context, "✅ Zaimportowano spektakl: $importedShow", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    context,
                    "❌ Błąd importu! Upewnij się, że to poprawny plik ZIP ze spektaklem.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(currentShowName) {
        currentCueIndex = 0
        val savedData = storageManager.loadShowDataFromJson(currentShowName)
        if (savedData != null) {
            val (loadedCrew, loadedChanges) = savedData
            crewList = loadedCrew.ifEmpty { defaultCrew }
            changesList = loadedChanges.ifEmpty { defaultInitialCue }
        } else {
            crewList = defaultCrew
            changesList = defaultInitialCue
            storageManager.initFoldersForShow(currentShowName, defaultCrew)
            storageManager.saveShowDataToJson(currentShowName, defaultCrew, defaultInitialCue)
        }
    }

    LaunchedEffect(changesList.size) {
        registerVolumeNavigation { delta ->
            if (changesList.isNotEmpty()) {
                val next = (currentCueIndex + delta).coerceIn(0, changesList.size - 1)
                currentCueIndex = next
            }
        }
    }

    Scaffold(
        containerColor = BgBlack,
        topBar = {
            Surface(
                color = Color(0xFF111111),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNewShowDialog = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎭 REPERTUAR (KLIKNIJ, ABY ZMIENIĆ)",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "▶ $currentShowName ▾",
                            color = TextAmber,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Button(
                        onClick = { showNewShowDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TextGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("+ Nowy / ZIP", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0D0D0D),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Text("🎭", fontSize = 20.sp) },
                    label = { Text("ZMIANY (${currentCueIndex + 1}/${changesList.size})", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = TextRed,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = TextRed.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Text("📷", fontSize = 20.sp) },
                    label = { Text("APARAT", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = TextAmber,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = TextAmber.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Text("👥", fontSize = 20.sp) },
                    label = { Text("BRYGADA", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = TextCyan,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = TextCyan.copy(alpha = 0.2f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                0 -> ShowCueScreen(
                    showName = currentShowName,
                    changes = changesList,
                    currentIndex = currentCueIndex,
                    onIndexChange = { currentCueIndex = it },
                    crewList = crewList,
                    onOpenCameraForSpike = { currentTab = 1 },
                    onAddNewChange = { newCue ->
                        val updated = changesList + newCue
                        changesList = updated
                        currentCueIndex = updated.lastIndex
                        storageManager.saveShowDataToJson(currentShowName, crewList, updated)
                    },
                    onUpdateCurrentChange = { updatedCue ->
                        val updated = changesList.mapIndexed { idx, item ->
                            if (idx == currentCueIndex) updatedCue else item
                        }
                        changesList = updated
                        storageManager.saveShowDataToJson(currentShowName, crewList, updated)
                    }
                )
                1 -> {
                    val safeChangeNumber = changesList.getOrNull(currentCueIndex)?.number ?: 1
                    SpikePhotoCameraTab(
                        showName = currentShowName,
                        changeNumber = "Zmiana_$safeChangeNumber",
                        crewMembers = crewList,
                        storageManager = storageManager
                    )
                }
                2 -> CrewAndReplacementTab(
                    showName = currentShowName,
                    crewList = crewList,
                    storageManager = storageManager,
                    onUpdateCrewList = { updatedCrew ->
                        crewList = updatedCrew
                        storageManager.saveShowDataToJson(currentShowName, updatedCrew, changesList)
                    }
                )
            }
        }
    }

    if (showNewShowDialog) {
        var newTitleInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewShowDialog = false },
            containerColor = Color(0xFF181818),
            title = { Text("🎬 Repertuar Teatru", color = TextAmber, fontWeight = FontWeight.Black) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            zipImportLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TextCyan)
                    ) {
                        Text(
                            text = "📥 IMPORTUJ SPEKTAKL Z PLIKU .ZIP",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Lub utwórz nowy spektakl od zera:", color = Color.White, fontSize = 14.sp)
                    OutlinedTextField(
                        value = newTitleInput,
                        onValueChange = { newTitleInput = it },
                        label = { Text("Tytuł (np. Hamlet, Balladyna)") },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Zapisane spektakle (Wybierz / 📦 Eksportuj / 🗑️ Usuń):",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    availableShows.forEach { show ->
                        val isCurrent = show == currentShowName
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentShowName = show
                                    Toast.makeText(context, "Przełączono na: $show", Toast.LENGTH_SHORT).show()
                                    showNewShowDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isCurrent) TextGreen else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isCurrent) "▶ $show" else show,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isCurrent) {
                                        storageManager.saveShowDataToJson(currentShowName, crewList, changesList)
                                    }
                                    storageManager.shareShowZip(show)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextCyan)
                            ) {
                                Text("📦 ZIP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (availableShows.size > 1) {
                                OutlinedButton(
                                    onClick = { showToDelete = show },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextRed)
                                ) {
                                    Text("🗑️", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitizedTitle = newTitleInput.trim()
                            .replace(Regex("[\\\\/:*?\"<>|]"), "")
                            .replace(Regex("\\s+"), "_")

                        if (sanitizedTitle.isNotBlank()) {
                            if (!availableShows.contains(sanitizedTitle)) {
                                availableShows = (availableShows + sanitizedTitle).sorted()
                            }
                            currentShowName = sanitizedTitle
                            Toast.makeText(context, "Utworzono spektakl: $sanitizedTitle", Toast.LENGTH_SHORT).show()
                            showNewShowDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextGreen)
                ) {
                    Text("UTWÓRZ I PRZEŁĄCZ", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewShowDialog = false }) {
                    Text("Zamknij", color = Color.Gray)
                }
            }
        )
    }

    showToDelete?.let { targetShow ->
        AlertDialog(
            onDismissRequest = { showToDelete = null },
            containerColor = Color(0xFF220808),
            title = { Text("⚠️ Usunąć spektakl?", color = TextRed, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    text = "Czy na pewno chcesz bezpowrotnie usunąć spektakl \"$targetShow\" wraz ze wszystkimi zmianami i zdjęciami szpilek?",
                    color = Color.White,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = storageManager.deleteShowFolder(targetShow)
                        if (deleted) {
                            val updatedList = availableShows.filterNot { it == targetShow }
                            availableShows = updatedList
                            if (currentShowName == targetShow && updatedList.isNotEmpty()) {
                                currentShowName = updatedList.first()
                            }
                            Toast.makeText(context, "Usunięto spektakl: $targetShow", Toast.LENGTH_SHORT).show()
                        }
                        showToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextRed)
                ) {
                    Text("TAK, USUŃ TRWALE", color = Color.White, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showToDelete = null }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ShowCueScreen(
    showName: String,
    changes: List<StageCueChange>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    crewList: List<StageCrewMember>,
    onOpenCameraForSpike: () -> Unit,
    onAddNewChange: (StageCueChange) -> Unit,
    onUpdateCurrentChange: (StageCueChange) -> Unit
) {
    val currentCue = changes.getOrNull(currentIndex) ?: return
    var showAddFurnitureDialog by remember { mutableStateOf(false) }
    var showAddFlyBarDialog by remember { mutableStateOf(false) }
    var showEditTurntableDialog by remember { mutableStateOf(false) }
    var showAddChangeDialog by remember { mutableStateOf(false) }

    fun resolveTechName(techId: String): String {
        val tech = crewList.find { it.id == techId }
        return if (tech != null) "${tech.name} (${tech.baseStation})" else "Nieprzypisany"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                        enabled = currentIndex > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("◀ POPRZEDNIA")
                    }

                    Text(
                        text = "ZMIANA ${currentCue.number} / ${changes.size}",
                        color = TextAmber,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedButton(
                        onClick = { if (currentIndex < changes.lastIndex) onIndexChange(currentIndex + 1) },
                        enabled = currentIndex < changes.lastIndex,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("NASTĘPNA ▶")
                    }
                }

                Text(
                    text = currentCue.actAndTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "🎯 HASŁO (CUE): ${currentCue.triggerCue}",
                    color = TextRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAddChangeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Nowa Zmiana", color = TextGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showEditTurntableDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚙️ Ustaw Obrotówkę", color = TextCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1405)),
            border = BorderStroke(1.5.dp, TextAmber),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔄 TARCZA OBROTOWA (OBROTÓWKA)", color = TextAmber, fontSize = 14.sp, fontWeight = FontWeight.Black)
                if (currentCue.turntable != null) {
                    val tt = currentCue.turntable
                    Text(tt.direction, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(tt.speed, color = TextGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("🎯 Punkt docelowy: ${tt.targetMark}", color = TextCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("👷 Obsługuje: ${resolveTechName(tt.assignedTechId)}", color = Color.LightGray, fontSize = 13.sp)
                    if (tt.warning.isNotBlank()) {
                        Text("⚠️ UWAGA: ${tt.warning}", color = TextRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Brak ruchu obrotówki w tej zmianie (Tarcza zaryglowana).", color = Color.Gray, fontSize = 13.sp)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪑 WÓZKI, MEBLE I SZPILKI", color = TextGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = { showAddFurnitureDialog = true }) {
                        Text("+ Dodaj element", color = TextGreen, fontWeight = FontWeight.Bold)
                    }
                }

                if (currentCue.furniture.isEmpty()) {
                    Text("Brak zadań meblowych w tej zmianie.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    currentCue.furniture.forEach { task ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(task.item, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                Text("▶ ${task.action}", color = TextAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(task.spikeColor)
                                    )
                                    Text("Szpilka: ${task.spikeName}", color = task.spikeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text("👷 Wykonuje: ${resolveTechName(task.assignedTechId)}", color = TextCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (task.details.isNotBlank()) {
                                    Text("📌 Uwagi: ${task.details}", color = Color.LightGray, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onOpenCameraForSpike,
                                    modifier = Modifier.padding(top = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("📷 Zrób / Zobacz zdjęcie szpilki", color = TextAmber, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏗️ SZTANKIETY (GALERIA / POMOST)", color = TextCyan, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    TextButton(onClick = { showAddFlyBarDialog = true }) {
                        Text("+ Dodaj sztankiet", color = TextCyan, fontWeight = FontWeight.Bold)
                    }
                }

                if (currentCue.flyBars.isEmpty()) {
                    Text("Brak jazdy sztankietów w tej zmianie.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    currentCue.flyBars.forEach { fb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SZTANKIET NR ${fb.barNumber}: ${fb.name}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text("Kierunek: ${fb.direction} • Znacznik: ${fb.targetMark}", color = TextAmber, fontSize = 13.sp)
                                Text("👷 Obsługuje: ${resolveTechName(fb.assignedTechId)}", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddChangeDialog) {
        var title by remember { mutableStateOf("AKT I • ZMIANA ${changes.size + 1}") }
        var cue by remember { mutableStateOf("Po kwestii...") }

        AlertDialog(
            onDismissRequest = { showAddChangeDialog = false },
            containerColor = Color(0xFF181818),
            title = { Text("➕ Nowa Zmiana Sceniczna", color = TextGreen, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Tytuł i Akt") })
                    OutlinedTextField(value = cue, onValueChange = { cue = it }, label = { Text("Hasło (Cue) wywołujące zmianę") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddNewChange(
                            StageCueChange(
                                number = changes.size + 1,
                                actAndTitle = title.ifBlank { "ZMIANA ${changes.size + 1}" },
                                triggerCue = cue
                            )
                        )
                        showAddChangeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextGreen)
                ) {
                    Text("DODAJ ZMIANĘ", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChangeDialog = false }) { Text("Anuluj", color = Color.Gray) }
            }
        )
    }

    if (showEditTurntableDialog) {
        var direction by remember { mutableStateOf(currentCue.turntable?.direction ?: "OBRÓT W LEWO (CCW)") }
        var speed by remember { mutableStateOf(currentCue.turntable?.speed ?: "Bieg 2 (50%)") }
        var targetMark by remember { mutableStateOf(currentCue.turntable?.targetMark ?: "Nabój nr 1") }
        var warning by remember { mutableStateOf(currentCue.turntable?.warning ?: "") }
        val selectedTechId = remember { mutableStateOf(currentCue.turntable?.assignedTechId ?: crewList.last().id) }

        AlertDialog(
            onDismissRequest = { showEditTurntableDialog = false },
            containerColor = Color(0xFF181818),
            title = { Text("🔄 Konfiguracja Obrotówki", color = TextAmber, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = direction, onValueChange = { direction = it }, label = { Text("Kierunek (W LEWO / W PRAWO)") })
                    OutlinedTextField(value = speed, onValueChange = { speed = it }, label = { Text("Prędkość / Bieg falownika") })
                    OutlinedTextField(value = targetMark, onValueChange = { targetMark = it }, label = { Text("Punkt docelowy (Nabój / Szpilka)") })
                    OutlinedTextField(value = warning, onValueChange = { warning = it }, label = { Text("Ostrzeżenie BHP") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCurrentChange(
                            currentCue.copy(
                                turntable = TurntableTask(
                                    direction = direction,
                                    speed = speed,
                                    targetMark = targetMark,
                                    assignedTechId = selectedTechId.value,
                                    warning = warning
                                )
                            )
                        )
                        showEditTurntableDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextAmber)
                ) {
                    Text("ZAPISZ", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onUpdateCurrentChange(currentCue.copy(turntable = null))
                        showEditTurntableDialog = false
                    }
                ) {
                    Text("Wyłącz obrotówkę", color = TextRed)
                }
            }
        )
    }

    if (showAddFurnitureDialog) {
        var item by remember { mutableStateOf("") }
        var action by remember { mutableStateOf("WJAZD Z LEWEJ KULISY") }
        var spikeName by remember { mutableStateOf("ŻÓŁTA SZPILKA") }
        var details by remember { mutableStateOf("Zaryglować hamulce") }
        var selectedTechId by remember { mutableStateOf(crewList.firstOrNull()?.id ?: "tech_1") }

        AlertDialog(
            onDismissRequest = { showAddFurnitureDialog = false },
            containerColor = Color(0xFF181818),
            title = { Text("🪑 Dodaj Wózek / Mebel", color = TextGreen, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = item, onValueChange = { item = it }, label = { Text("Nazwa (np. Kanapa, Podest)") })
                    OutlinedTextField(value = action, onValueChange = { action = it }, label = { Text("Akcja (Wjazd / Wyjazd)") })
                    OutlinedTextField(value = spikeName, onValueChange = { spikeName = it }, label = { Text("Kolor i nazwa szpilki") })
                    OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Szczegóły / Ryglowanie") })

                    Text("Przypisz maszynistę:", color = Color.Gray, fontSize = 12.sp)
                    crewList.forEach { member ->
                        OutlinedButton(
                            onClick = { selectedTechId = member.id },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selectedTechId == member.id) TextGreen else Color.White
                            )
                        ) {
                            Text("${member.name} (${member.baseStation})")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (item.isNotBlank()) {
                            val newTask = FurnitureTask(
                                item = item,
                                action = action,
                                assignedTechId = selectedTechId,
                                spikeColor = TextAmber,
                                spikeName = spikeName,
                                details = details
                            )
                            onUpdateCurrentChange(currentCue.copy(furniture = currentCue.furniture + newTask))
                            showAddFurnitureDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextGreen)
                ) {
                    Text("DODAJ", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFurnitureDialog = false }) { Text("Anuluj", color = Color.Gray) }
            }
        )
    }

    if (showAddFlyBarDialog) {
        var barNumber by remember { mutableStateOf("10") }
        var name by remember { mutableStateOf("") }
        var direction by remember { mutableStateOf("W DÓŁ (Wchodzi)") }
        var targetMark by remember { mutableStateOf("Do zielonej taśmy") }

        AlertDialog(
            onDismissRequest = { showAddFlyBarDialog = false },
            containerColor = Color(0xFF181818),
            title = { Text("🏗️ Dodaj Sztankiet", color = TextCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barNumber,
                        onValueChange = { barNumber = it },
                        label = { Text("Numer sztankietu") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Co wisi (np. Horyzont, Las)") })
                    OutlinedTextField(value = direction, onValueChange = { direction = it }, label = { Text("Kierunek (W DÓŁ / DO GÓRY)") })
                    OutlinedTextField(value = targetMark, onValueChange = { targetMark = it }, label = { Text("Znacznik na linie") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = barNumber.toIntOrNull() ?: 1
                        val newFb = FlyBarTask(
                            barNumber = num,
                            name = name.ifBlank { "Dekoracja" },
                            direction = direction,
                            targetMark = targetMark,
                            assignedTechId = crewList.lastOrNull()?.id ?: "tech_3"
                        )
                        onUpdateCurrentChange(currentCue.copy(flyBars = currentCue.flyBars + newFb))
                        showAddFlyBarDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextCyan)
                ) {
                    Text("DODAJ", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFlyBarDialog = false }) { Text("Anuluj", color = Color.Gray) }
            }
        )
    }
}

@Composable
fun SpikePhotoCameraTab(
    showName: String,
    changeNumber: String,
    crewMembers: List<StageCrewMember>,
    storageManager: StageStorageManager
) {
    val context = LocalContext.current
    var selectedTech by remember(crewMembers) { mutableStateOf(crewMembers.first()) }
    var spikeLabel by remember { mutableStateOf("Zolta_Szpilka_Stol") }
    var pendingPhotoFile by remember { mutableStateOf<File?>(null) }
    var savedPhotos by remember(showName, selectedTech) {
        mutableStateOf(storageManager.getPhotosForShowAndStation(showName, selectedTech.folderName))
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoFile != null) {
            Toast.makeText(
                context,
                "✅ Zapisano zdjęcie szpilki: ${pendingPhotoFile?.name}",
                Toast.LENGTH_SHORT
            ).show()
            savedPhotos = storageManager.getPhotosForShowAndStation(showName, selectedTech.folderName)
        }
    }

    fun launchCamera() {
        val stationDir = storageManager.getStationFolderForTech(showName, selectedTech.folderName)
        val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val cleanLabel = spikeLabel.trim().replace(Regex("\\s+"), "_").ifBlank { "Szpilka" }
        val photoFile = File(stationDir, "${changeNumber}_${cleanLabel}_$timestamp.jpg")
        pendingPhotoFile = photoFile

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                photoFile
            )
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Błąd FileProvider: Sprawdź konfigurację",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Brak uprawnień do aparatu!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📷 DOKUMENTACJA SZPILEK ($changeNumber)",
            color = TextAmber,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )

        Text("1. Wybierz stanowisko (folder docelowy):", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            crewMembers.forEach { member ->
                val isSelected = member.id == selectedTech.id
                OutlinedButton(
                    onClick = { selectedTech = member },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSelected) TextAmber else Color.White
                    ),
                    border = BorderStroke(1.dp, if (isSelected) TextAmber else Color.DarkGray)
                ) {
                    Text(member.name, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        OutlinedTextField(
            value = spikeLabel,
            onValueChange = { spikeLabel = it },
            label = { Text("Opis szpilki / rekwizytu (do nazwy pliku)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    launchCamera()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TextAmber)
        ) {
            Text("📸 ZRÓB ZDJĘCIE SZPILKI NA SCENIE", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }

        Text(
            text = "📁 Folder: Spektakle/$showName/Obsluga_Zmiany_Pracownicy/${selectedTech.folderName}",
            color = TextCyan,
            fontSize = 11.sp
        )

        HorizontalDivider(color = Color.DarkGray)

        Text(
            text = "Zapisane zdjęcia dla stanowiska ${selectedTech.name} (${savedPhotos.size}):",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (savedPhotos.isEmpty()) {
            Text("Brak zdjęć w tym folderze.", color = Color.Gray, fontSize = 13.sp)
        } else {
            savedPhotos.forEach { file ->
                val bitmap = remember(file.absolutePath) {
                    BitmapFactory.decodeFile(file.absolutePath)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(file.name, color = TextAmber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = file.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrewAndReplacementTab(
    showName: String,
    crewList: List<StageCrewMember>,
    storageManager: StageStorageManager,
    onUpdateCrewList: (List<StageCrewMember>) -> Unit
) {
    var memberToReplace by remember { mutableStateOf<StageCrewMember?>(null) }
    var replacementName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "👥 OBSADA TECHNICZNA I ZASTĘPSTWA",
            color = TextCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "W razie choroby lub zmiany obsady wpisz zastępcę – aplikacja automatycznie przepisze wszystkie jego zadania w spektaklu $showName.",
            color = Color.Gray,
            fontSize = 12.sp
        )

        crewList.forEach { member ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                border = BorderStroke(1.dp, Color(0xFF2C2C2E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "👷 ${member.name}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "BAZA: ${member.baseStation}",
                                color = TextAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                memberToReplace = member
                                replacementName = ""
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextCyan)
                        ) {
                            Text("🔄 Zastępstwo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Zakres obowiązków: ${member.assignedFunctions.joinToString(", ")}", color = Color.LightGray, fontSize = 13.sp)
                    Text("🧰 Narzędzia: ${member.requiredTools}", color = TextGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("📁 Katalog zdjęć: ${member.folderName}", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }

    memberToReplace?.let { targetMember ->
        AlertDialog(
            onDismissRequest = { memberToReplace = null },
            containerColor = Color(0xFF181818),
            title = { Text("🔄 Zastępstwo na stanowisku", color = TextCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kto wchodzi dzisiaj na stanowisko ${targetMember.baseStation} za pracownika ${targetMember.name}?",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = replacementName,
                        onValueChange = { replacementName = it },
                        label = { Text("Imię zastępcy (np. Krzysiek)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replacementName.isNotBlank()) {
                            val updatedCrew = crewList.map {
                                if (it.id == targetMember.id) it.copy(name = replacementName.trim()) else it
                            }
                            onUpdateCrewList(updatedCrew)
                            memberToReplace = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TextCyan)
                ) {
                    Text("PRZEPISZ ZADANIA", color = Color.Black, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToReplace = null }) {
                    Text("Anuluj", color = Color.Gray)
                }
            }
        )
    }
}
