package com.wilm.zufallsgenerator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeColor by remember { mutableIntStateOf(loadThemeColor(context)) }
            AppTheme(seed = Color(themeColor)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RandomPickerScreen(
                        themeColor = themeColor,
                        onThemeColorChange = {
                            themeColor = it
                            saveThemeColor(context, it)
                        }
                    )
                }
            }
        }
    }
}

// ---------- Farben ----------

// Auswahl für die App-Farbe; der erste Eintrag ist die Standardfarbe #0D99BB
private val ColorOptions = listOf(
    Color(0xFF0D99BB),
    Color(0xFF2E7D32),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
    Color(0xFFD81B60),
    Color(0xFF3949AB),
    Color(0xFF455A64)
)

// Erzeugt aus einer Grundfarbe das komplette Farbschema (Helligkeitsstufen per Mischung)
@Composable
fun AppTheme(seed: Color, content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = lerp(seed, Color.White, 0.3f),
            onPrimary = Color(0xFF00222A),
            primaryContainer = lerp(seed, Color.Black, 0.5f),
            onPrimaryContainer = lerp(seed, Color.White, 0.8f),
            secondaryContainer = Color(0xFF344A51),
            onSecondaryContainer = Color(0xFFCEE7EE),
            background = Color(0xFF0F1416),
            onBackground = Color(0xFFDEE3E5),
            surface = Color(0xFF0F1416),
            onSurface = Color(0xFFDEE3E5),
            surfaceVariant = Color(0xFF3F484B),
            onSurfaceVariant = Color(0xFFBFC8CC)
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = lerp(seed, Color.White, 0.75f),
            onPrimaryContainer = lerp(seed, Color.Black, 0.6f),
            secondaryContainer = lerp(seed, Color.White, 0.85f),
            onSecondaryContainer = lerp(seed, Color.Black, 0.7f),
            background = Color(0xFFF5FAFC),
            onBackground = Color(0xFF171D1F),
            surface = Color(0xFFF5FAFC),
            onSurface = Color(0xFF171D1F),
            surfaceVariant = Color(0xFFDBE4E8),
            onSurfaceVariant = Color(0xFF3F484B)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Daten & Speicherung ----------

class NamedList(
    val name: String,
    val items: SnapshotStateList<String> = mutableStateListOf(),
    val trash: SnapshotStateList<String> = mutableStateListOf(),
    // Temporäre Liste ("keine"): wird nie gespeichert und verschwindet beim Wechsel
    val temporary: Boolean = false
)

private const val PREFS_NAME = "zufallsgenerator"
private const val PREFS_KEY = "data"
private const val KEY_COLOR = "theme_color"
private const val KEY_TRASH = "use_trash"
private const val KEY_SHOW_DRAWN_DELETE = "show_drawn_delete"

private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun loadThemeColor(context: Context): Int =
    prefs(context).getInt(KEY_COLOR, ColorOptions[0].toArgb())

private fun saveThemeColor(context: Context, color: Int) {
    prefs(context).edit().putInt(KEY_COLOR, color).apply()
}

private fun loadBool(context: Context, key: String, default: Boolean): Boolean =
    prefs(context).getBoolean(key, default)

private fun saveBool(context: Context, key: String, value: Boolean) {
    prefs(context).edit().putBoolean(key, value).apply()
}

private fun loadLists(context: Context): List<NamedList> {
    val json = prefs(context).getString(PREFS_KEY, null) ?: return emptyList()
    return try {
        val arr = JSONObject(json).getJSONArray("lists")
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val itemsJson = obj.getJSONArray("items")
            val trashJson = obj.optJSONArray("trash") ?: JSONArray()
            NamedList(
                name = obj.getString("name"),
                items = (0 until itemsJson.length()).map { itemsJson.getString(it) }.toMutableStateList(),
                trash = (0 until trashJson.length()).map { trashJson.getString(it) }.toMutableStateList()
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun saveLists(context: Context, lists: List<NamedList>) {
    val arr = JSONArray()
    lists.filter { !it.temporary }.forEach { list ->
        arr.put(
            JSONObject()
                .put("name", list.name)
                .put("items", JSONArray(list.items.toList()))
                .put("trash", JSONArray(list.trash.toList()))
        )
    }
    val root = JSONObject().put("lists", arr)
    prefs(context).edit().putString(PREFS_KEY, root.toString()).apply()
}

// ---------- Oberfläche ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDropdown(
    lists: List<NamedList>,
    selected: NamedList,
    enabled: Boolean,
    onSelect: (NamedList) -> Unit,
    onNewList: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.select_list)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        onSelect(list)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_list)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onNewList()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomPickerScreen(themeColor: Int, onThemeColorChange: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noneName = stringResource(R.string.none)

    // Am Anfang immer die temporäre Liste "keine", dazu die gespeicherten Listen
    val lists = remember {
        (listOf(NamedList(noneName, temporary = true)) + loadLists(context)).toMutableStateList()
    }
    var selectedList by remember { mutableStateOf(lists.first()) }
    val current = selectedList
    val items: List<String> = current.items

    var trashEnabled by remember { mutableStateOf(loadBool(context, KEY_TRASH, true)) }
    var showDrawnDelete by remember { mutableStateOf(loadBool(context, KEY_SHOW_DRAWN_DELETE, true)) }

    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var drawnIndex by rememberSaveable { mutableStateOf(-1) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var spinning by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun persist() = saveLists(context, lists)

    fun resetResult() {
        result = null
        drawnIndex = -1
        highlighted = -1
    }

    // Eintrag entfernen: landet im Papierkorb, sofern der Papierkorb aktiv ist
    fun removeItem(list: NamedList, index: Int) {
        val value = list.items.removeAt(index)
        if (trashEnabled) list.trash.add(value)
    }

    fun restoreItem(list: NamedList, index: Int) {
        list.items.add(list.trash.removeAt(index))
        resetResult()
        persist()
    }

    fun selectList(target: NamedList) {
        if (spinning) return
        // Die Liste "keine" bleibt bestehen, aber ihr Inhalt wird geleert, sobald man sie verlässt
        lists.filter { it.temporary && it !== target }.forEach {
            it.items.clear()
            it.trash.clear()
        }
        selectedList = target
        resetResult()
        persist()
    }

    fun addItem() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            current.items.add(text)
            input = ""
            resetResult()
            persist()
        }
    }

    // Glücksrad-Animation: läuft die Liste durch und wird zum Ende hin langsamer
    fun spin() {
        val list = current.items
        val n = list.size
        if (spinning || n < 1) return
        spinning = true
        resetResult()
        scope.launch {
            val winner = Random.nextInt(n)
            val start = Random.nextInt(n)
            val laps = if (n >= 20) 1 else maxOf(2, 20 / n + 1)
            val total = laps * n + (winner - start + n) % n
            for (step in 0..total) {
                highlighted = (start + step) % n
                val progress = step.toFloat() / total
                delay((40 + 300 * progress * progress * progress).toLong())
            }
            // Der Gewinner wird nie automatisch entfernt
            result = list.getOrNull(winner)
            drawnIndex = winner
            highlighted = -1
            spinning = false
        }
    }

    // Dialog: neue Liste anlegen
    if (showNewListDialog) {
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text(stringResource(R.string.new_list_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.list_name_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty()) {
                        val list = NamedList(name)
                        lists.add(list)
                        selectedList = list
                        resetResult()
                        persist()
                    }
                    newName = ""
                    showNewListDialog = false
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    newName = ""
                    showNewListDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Dialog: Papierkorb der aktuellen Liste
    if (showTrash && trashEnabled) {
        val trashList = current
        AlertDialog(
            onDismissRequest = { showTrash = false },
            title = { Text(stringResource(R.string.trash)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trashList.trash.isEmpty()) {
                        Text(stringResource(R.string.trash_empty))
                    }
                    trashList.trash.forEachIndexed { index, value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(value, modifier = Modifier.weight(1f))
                            TextButton(onClick = { restoreItem(trashList, index) }) {
                                Text(stringResource(R.string.restore))
                            }
                        }
                    }
                    if (trashList.trash.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                trashList.items.addAll(trashList.trash.toList())
                                trashList.trash.clear()
                                resetResult()
                                persist()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(stringResource(R.string.restore_all))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrash = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                if (trashList.trash.isNotEmpty()) {
                    TextButton(onClick = {
                        trashList.trash.clear()
                        persist()
                    }) { Text(stringResource(R.string.empty_trash)) }
                }
            }
        )
    }

    // Dialog: Einstellungen
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.app_color),
                        style = MaterialTheme.typography.titleSmall
                    )
                    ColorOptions.chunked(4).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowColors.forEach { color ->
                                val argb = color.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (argb == themeColor) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { onThemeColorChange(argb) }
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.app_color_hint),
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.show_drawn_delete),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.show_drawn_delete_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = showDrawnDelete,
                            onCheckedChange = {
                                showDrawnDelete = it
                                saveBool(context, KEY_SHOW_DRAWN_DELETE, it)
                            }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.use_trash),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                stringResource(R.string.use_trash_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = trashEnabled,
                            onCheckedChange = {
                                trashEnabled = it
                                saveBool(context, KEY_TRASH, it)
                                if (!it) {
                                    // Ohne Papierkorb gibt es nichts mehr wiederherzustellen
                                    lists.forEach { list -> list.trash.clear() }
                                    persist()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    val displayText = if (spinning) items.getOrNull(highlighted) else result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kopfzeile mit Titel und Einstellungen
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium)
            TextButton(
                onClick = { if (!spinning) showSettings = true },
                enabled = !spinning
            ) {
                Text(stringResource(R.string.settings))
            }
        }

        // Listenauswahl als Dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListDropdown(
                lists = lists,
                selected = current,
                enabled = !spinning,
                onSelect = { selectList(it) },
                onNewList = { showNewListDialog = true },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    lists.remove(current)
                    if (lists.isEmpty()) {
                        lists.add(NamedList(noneName, temporary = true))
                    }
                    selectedList = lists.last()
                    resetResult()
                    persist()
                },
                enabled = !spinning && !current.temporary
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_current_list)
                )
            }
        }

        // Eingabezeile
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.add_item_label)) },
                singleLine = true,
                enabled = !spinning,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addItem() })
            )
            FilledIconButton(onClick = { addItem() }, enabled = !spinning) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }

        // Einträge bzw. Hinweistext
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.empty_list),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (index == highlighted)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        removeItem(current, index)
                                        resetResult()
                                        persist()
                                    },
                                    enabled = !spinning
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ergebnis / laufende Animation
        displayText?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (spinning)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }
        }

        // Button zum Löschen des gezogenen Eintrags (nur wenn in den Einstellungen aktiv)
        if (showDrawnDelete && !spinning && result != null && drawnIndex in 0 until items.size) {
            OutlinedButton(
                onClick = {
                    removeItem(current, drawnIndex)
                    resetResult()
                    persist()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.delete_drawn))
            }
        }

        Button(
            onClick = { spin() },
            enabled = items.isNotEmpty() && !spinning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (spinning) R.string.spinning else R.string.spin))
        }

        // Unten: alle Einträge löschen und Papierkorb
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (items.isNotEmpty()) {
                TextButton(
                    onClick = {
                        if (trashEnabled) current.trash.addAll(items.toList())
                        current.items.clear()
                        resetResult()
                        persist()
                    },
                    enabled = !spinning
                ) {
                    Text(stringResource(R.string.delete_all))
                }
            }
            if (trashEnabled) {
                TextButton(
                    onClick = { showTrash = true },
                    enabled = !spinning
                ) {
                    Text(stringResource(R.string.trash_count, current.trash.size))
                }
            }
        }
    }
}