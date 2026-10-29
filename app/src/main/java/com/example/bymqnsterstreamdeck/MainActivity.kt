package com.example.bymqnsterstreamdeck

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

private enum class Action {
    START, STOP, TOGGLE, SCENE, MUTE, UNMUTE
}

private data class DeckButton(
    val label: String,
    val action: Action,
    val argument: String = ""
)

class MainActivity : ComponentActivity() {
    private var volumeHandler: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        setContent {
            MaterialTheme {
                DeckScreen { handler -> volumeHandler = handler }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            volumeHandler?.invoke(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckScreen(
    handlerReady: (((Int) -> Unit) -> Unit)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("deck", Context.MODE_PRIVATE)
    }

    var host by remember { mutableStateOf(prefs.getString("host", "") ?: "") }
    var port by remember { mutableStateOf(prefs.getString("port", "4455") ?: "4455") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var useUsb by remember { mutableStateOf(prefs.getBoolean("useUsb", false)) }

    var status by remember { mutableStateOf("OBS bağlantısı kurulmadı") }
    var profile by remember { mutableIntStateOf(0) }
    var control by remember { mutableIntStateOf(50) }
    var buttonSize by remember { mutableFloatStateOf(prefs.getFloat("buttonSize", 100f)) }

    var showSettings by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editIndex by remember { mutableIntStateOf(-1) }
    var label by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(Action.START) }
    var argument by remember { mutableStateOf("") }
    var sceneNames by remember { mutableStateOf(emptyList<String>()) }
    var audioInputNames by remember { mutableStateOf(emptyList<String>()) }
    var showObsItems by remember { mutableStateOf(false) }
    var listLoading by remember { mutableStateOf(false) }
    var buttons by remember { mutableStateOf(loadButtons(prefs)) }
    var client by remember { mutableStateOf<ObsClient?>(null) }

    DisposableEffect(Unit) {
        onDispose { client?.close() }
    }

    LaunchedEffect(Unit) {
        handlerReady { keyCode ->
            control = (
                    control + if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 5 else -5
                    ).coerceIn(0, 100)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "BYMQNSTER • Profil ${profile + 1}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = { profile = (profile + 1) % 3 }) {
                Text("Profil değiştir")
            }
            Button(onClick = { showSettings = true }) {
                Text("OBS ayarları")
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                modifier = Modifier.width(170.dp).height(58.dp),
                onClick = {
                    editIndex = -1
                    label = ""
                    argument = ""
                    action = Action.START
                    showEditor = true
                }
            ) {
                Text("+ TUŞ EKLE", style = MaterialTheme.typography.titleMedium)
            }
            Button(onClick = { showDeleteDialog = true }) {
                Text("Tuş sil")
            }
            Button(
                onClick = {
                    useUsb = true
                    prefs.edit().putBoolean("useUsb", true).apply()
                    client?.close()
                    status = "USB bağlantısı kuruluyor…"
                    client = ObsClient(
                        url = "ws://127.0.0.1:$port",
                        password = password
                    ) { status = it }
                }
            ) {
                Text("USB bağlan")
            }
            Text(
                text = status,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tuş boyutu: ${buttonSize.toInt()} dp")
            Slider(
                value = buttonSize,
                onValueChange = { size ->
                    buttonSize = size
                    prefs.edit().putFloat("buttonSize", size).apply()
                },
                valueRange = 72f..150f,
                modifier = Modifier.width(260.dp)
            )
        }



        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(buttons[profile]) { button ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .combinedClickable(
                            onClick = {
                                val currentClient = client
                                if (currentClient == null) {
                                    status = "Önce OBS ayarlarından bağlanın"
                                } else {
                                    currentClient.run(button) { status = it }
                                }
                            },
                            onLongClick = {
                                label = button.label
                                action = button.action
                                argument = button.argument
                                editIndex = buttons[profile].indexOf(button)
                                showEditor = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val currentClient = client
                            if (currentClient == null) {
                                status = "Önce OBS ayarlarından bağlanın"
                            } else {
                                currentClient.run(button) { status = it }
                            }
                        }
                    ) {
                        Text(button.label)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kontrol: $control%")
            Slider(
                value = control.toFloat(),
                onValueChange = { control = it.toInt() },
                valueRange = 0f..100f,
                modifier = Modifier.width(260.dp)
            )
        }

        Text(
            "Bir tuşu basılı tutarak düzenleyebilir veya silebilirsiniz.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("OBS WebSocket ayarları") },
            text = {
                Column {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Bilgisayarın IP adresi") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port (genelde 4455)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("WebSocket parolası") },
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("USB ile bağlan")
                        Spacer(Modifier.weight(1f))
                        Switch(checked = useUsb, onCheckedChange = { useUsb = it })
                    }
                    Text(
                        if (useUsb) "USB için bilgisayarda adb reverse tcp:4455 tcp:4455 komutu gerekir."
                        else "Wi-Fi için telefon ve bilgisayar aynı ağa bağlı olmalı.",
                        style = MaterialTheme.typography.bodySmall
                    )

                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit()
                            .putString("host", host)
                            .putString("port", port)
                            .putString("password", password)
                            .putBoolean("useUsb", useUsb)
                            .apply()

                        client?.close()
                        status = "OBS'ye bağlanılıyor…"
                        client = ObsClient(
                            url = "ws://${if (useUsb) "127.0.0.1" else host}:$port",
                            password = password
                        ) { status = it }

                        showSettings = false
                    }
                ) {
                    Text("Bağlan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Kapat")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Tuş sil") },
            text = {

                if (buttons.all { it.isEmpty() }) {
                    Text("Silinecek tuş yok.")
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        buttons.forEachIndexed { profileIndex, profileButtons ->
                            profileButtons.forEachIndexed { index, button ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Profil ${profileIndex + 1} • ${button.label}",
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            val updated = buttons.map { it.toMutableList() }.toMutableList()
                                            updated[profileIndex].removeAt(index)
                                            buttons = updated
                                            saveButtons(prefs, updated)
                                            showDeleteDialog = false
                                        }
                                    ) {
                                        Text("Sil")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Kapat") }
            }
        )
    }


    if (showEditor) {
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showEditor = false },
            title = {
                Text(if (editIndex < 0) "Yeni tuş oluştur" else "Tuşu düzenle")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Tuş adı") },
                        singleLine = true
                    )

                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("İşlem: ${action.displayName()}")
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            Action.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName()) },
                                    onClick = {
                                        action = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (action == Action.SCENE || action == Action.MUTE || action == Action.UNMUTE) {
                        Button(
                            onClick = {
                                val currentClient = client
                                if (currentClient == null) {
                                    status = "Önce OBS ayarlarından bağlanın"
                                } else {
                                    listLoading = true
                                    showObsItems = false
                                    if (action == Action.SCENE) {
                                        currentClient.getSceneNames { names ->
                                            sceneNames = names
                                            listLoading = false
                                            showObsItems = true
                                            status = if (names.isEmpty()) "OBS sahne listesi boş" else "Sahne seçin"
                                        }
                                    } else {
                                        currentClient.getAudioInputs { names ->
                                            audioInputNames = names
                                            listLoading = false
                                            showObsItems = true
                                            status = if (names.isEmpty()) "OBS ses kaynağı bulunamadı" else "Ses kaynağı seçin"
                                        }
                                    }
                                }
                            }
                        ) { Text(if (listLoading) "OBS listesi alınıyor…" else "OBS listesini göster") }

                        Box {
                            OutlinedTextField(
                                value = argument,
                                onValueChange = { argument = it },
                                label = { Text(if (action == Action.SCENE) "Sahne adı" else "Mikrofon / ses kaynağı") },
                                singleLine = true
                            )
                            val options = if (action == Action.SCENE) sceneNames else audioInputNames
                            DropdownMenu(expanded = showObsItems, onDismissRequest = { showObsItems = false }) {
                                options.forEach { itemName ->
                                    DropdownMenuItem(
                                        text = { Text(itemName) },
                                        onClick = { argument = itemName; showObsItems = false }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (label.isNotBlank()) {
                            val updated = buttons.map { it.toMutableList() }.toMutableList()
                            val newButton = DeckButton(label.trim(), action, argument.trim())

                            if (editIndex >= 0) {
                                updated[profile][editIndex] = newButton
                            } else {
                                updated[profile].add(newButton)
                            }

                            buttons = updated
                            saveButtons(prefs, updated)
                            showEditor = false
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                Row {
                    if (editIndex > 0) {
                        TextButton(onClick = {
                            val updated = buttons.map { it.toMutableList() }.toMutableList()
                            val list = updated[profile]
                            val moved = list.removeAt(editIndex)
                            val newIndex = editIndex - 1
                            list.add(newIndex, moved)
                            buttons = updated
                            saveButtons(prefs, updated)
                            editIndex = newIndex
                        }) { Text("↑") }
                    }
                    if (editIndex in 0 until buttons[profile].lastIndex) {
                        TextButton(onClick = {
                            val updated = buttons.map { it.toMutableList() }.toMutableList()
                            val list = updated[profile]
                            val moved = list.removeAt(editIndex)
                            val newIndex = editIndex + 1
                            list.add(newIndex, moved)
                            buttons = updated
                            saveButtons(prefs, updated)
                            editIndex = newIndex
                        }) { Text("↓") }
                    }

                    if (editIndex >= 0) {
                        TextButton(
                            onClick = {
                                val updated = buttons.map { it.toMutableList() }.toMutableList()
                                updated[profile].removeAt(editIndex)
                                buttons = updated
                                saveButtons(prefs, updated)
                                showEditor = false
                            }
                        ) {
                            Text("Sil")
                        }
                    }

                    TextButton(onClick = { showEditor = false }) {
                        Text("İptal")
                    }
                }
            }
        )
    }
}

private fun Action.displayName(): String = when (this) {
    Action.START -> "Kayıt başlat"
    Action.STOP -> "Kayıt durdur"
    Action.TOGGLE -> "Kaydı aç/kapat"
    Action.SCENE -> "Sahne değiştir"
    Action.MUTE -> "Mikrofonu sustur"
    Action.UNMUTE -> "Mikrofonun sesini aç"
}

private fun loadButtons(
    prefs: android.content.SharedPreferences
): List<List<DeckButton>> {
    val saved = prefs.getString("buttons", null)

    if (saved == null) {
        return listOf(
            listOf(
                DeckButton("Kayıt başlat", Action.START),
                DeckButton("Kayıt durdur", Action.STOP),
                DeckButton("Kayıt aç/kapat", Action.TOGGLE),
                DeckButton("Mikrofon sustur", Action.MUTE, "Mic/Aux")
            ),
            listOf(
                DeckButton("Sahne değiştir", Action.SCENE, "Scene")
            ),
            emptyList()
        )
    }

    return try {
        val root = JSONArray(saved)

        List(3) { profileIndex ->
            val array = root.optJSONArray(profileIndex) ?: JSONArray()

            List(array.length()) { buttonIndex ->
                val item = array.getJSONObject(buttonIndex)

                DeckButton(
                    label = item.getString("label"),
                    action = Action.valueOf(item.getString("action")),
                    argument = item.optString("argument")
                )
            }
        }
    } catch (_: Exception) {
        List(3) { emptyList() }
    }
}

private fun saveButtons(
    prefs: android.content.SharedPreferences,
    buttons: List<List<DeckButton>>
) {
    val root = JSONArray()

    buttons.forEach { profile ->
        val array = JSONArray()

        profile.forEach { button ->
            array.put(
                JSONObject()
                    .put("label", button.label)
                    .put("action", button.action.name)
                    .put("argument", button.argument)
            )
        }

        root.put(array)
    }

    prefs.edit().putString("buttons", root.toString()).apply()
}

private class ObsClient(
    url: String,
    private val password: String,
    private val report: (String) -> Unit
) {
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val pendingLists = mutableMapOf<String, (JSONObject) -> Unit>()
    private val socket: WebSocket = http.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                report("OBS bağlantısı açıldı")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)

                    when (message.optInt("op")) {
                        // OBS WebSocket Hello
                        0 -> {
                            val data = message.getJSONObject("d")
                            val identify = JSONObject()
                                .put("rpcVersion", data.optInt("rpcVersion", 1))
                                .put("eventSubscriptions", 0)

                            if (data.has("authentication")) {
                                val auth = data.getJSONObject("authentication")
                                val secret = base64(
                                    sha256(password + auth.getString("salt"))
                                )
                                val response = base64(
                                    sha256(secret + auth.getString("challenge"))
                                )
                                identify.put("authentication", response)
                            }

                            webSocket.send(
                                JSONObject()
                                    .put("op", 1)
                                    .put("d", identify)
                                    .toString()
                            )
                        }

                        // OBS WebSocket Identified
                        2 -> report("OBS'ye bağlandı")

                        // OBS WebSocket RequestResponse
                        7 -> {
                            val data = message.getJSONObject("d")
                            val requestStatus = data.optJSONObject("requestStatus")
                            val listCallback = synchronized(pendingLists) {
                                pendingLists.remove(data.optString("requestId"))
                            }
                            if (requestStatus?.optBoolean("result", false) == true) {
                                listCallback?.invoke(data.optJSONObject("responseData") ?: JSONObject())
                            }
                            val ok = requestStatus?.optBoolean("result", false) == true

                            report(
                                if (ok) {
                                    "OBS komutu tamamlandı"
                                } else {
                                    "OBS komut hatası: ${
                                        requestStatus?.optString("comment", "İstek reddedildi")
                                            ?: "İstek reddedildi"
                                    }"
                                }
                            )
                        }

                        9 -> report("OBS bağlantı veya parola hatası")
                    }
                } catch (error: Exception) {
                    report("OBS yanıtı okunamadı: ${error.message}")
                }
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                report("Bağlantı başarısız: ${t.message}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                report("OBS bağlantısı kapandı")
            }
        }
    )

    fun getSceneNames(done: (List<String>) -> Unit) {
        requestList("GetSceneList", done) { data ->
            val scenes = data.optJSONArray("scenes") ?: JSONArray()
            (0 until scenes.length()).mapNotNull { index ->
                scenes.optJSONObject(index)?.optString("sceneName")?.takeIf { it.isNotBlank() }
            }
        }
    }

    fun getAudioInputs(done: (List<String>) -> Unit) {
        requestList("GetInputList", done) { data ->
            val inputs = data.optJSONArray("inputs") ?: JSONArray()
            (0 until inputs.length()).mapNotNull { index ->
                val input = inputs.optJSONObject(index) ?: return@mapNotNull null
                val kind = input.optString("inputKind").lowercase()
                val audioKind = listOf("audio", "wasapi", "pulse", "coreaudio", "alsa", "jack", "pipewire")
                    .any { kind.contains(it) }
                input.optString("inputName").takeIf { audioKind && it.isNotBlank() }
            }
        }
    }

    private fun requestList(
        requestType: String,
        done: (List<String>) -> Unit,
        parse: (JSONObject) -> List<String>
    ) {
        val requestId = UUID.randomUUID().toString()
        synchronized(pendingLists) { pendingLists[requestId] = { data -> done(parse(data)) } }
        val sent = socket.send(
            JSONObject()
                .put("op", 6)
                .put(
                    "d",
                    JSONObject()
                        .put("requestType", requestType)
                        .put("requestId", requestId)
                        .put("requestData", JSONObject())
                )
                .toString()
        )
        if (!sent) {
            synchronized(pendingLists) { pendingLists.remove(requestId) }
            done(emptyList())
        }
    }

    fun run(button: DeckButton, done: (String) -> Unit) {
        val requestData = JSONObject()
        val requestType = when (button.action) {
            Action.START -> "StartRecord"
            Action.STOP -> "StopRecord"
            Action.TOGGLE -> "ToggleRecord"
            Action.SCENE -> {
                requestData.put("sceneName", button.argument)
                "SetCurrentProgramScene"
            }

            Action.MUTE -> {
                requestData.put("inputName", button.argument)
                    .put("inputMuted", true)
                "SetInputMute"
            }

            Action.UNMUTE -> {
                requestData.put("inputName", button.argument)
                    .put("inputMuted", false)
                "SetInputMute"
            }
        }

        val sent = socket.send(
            JSONObject()
                .put("op", 6)
                .put(
                    "d",
                    JSONObject()
                        .put("requestType", requestType)
                        .put("requestId", UUID.randomUUID().toString())
                        .put("requestData", requestData)
                )
                .toString()
        )

        done(
            if (sent) "Komut OBS'ye gönderildi" else "Komut gönderilemedi"
        )
    }

    fun close() {
        socket.close(1000, "Uygulama kapatıldı")
        http.dispatcher.executorService.shutdown()
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)
}