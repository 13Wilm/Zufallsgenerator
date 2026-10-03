package com.wilm.zufallsgenerator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RandomPickerScreen()
                }
            }
        }
    }
}

// ---------- Farben (Hauptfarbe #0D99BB) ----------

private val BrandColor = Color(0xFF0D99BB)

private val LightColors = lightColorScheme(
    primary = BrandColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFEAF5),
    onPrimaryContainer = Color(0xFF00363F),
    secondary = Color(0xFF4B6269),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE7EE),
    onSecondaryContainer = Color(0xFF071E24),
    tertiary = Color(0xFF456179),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E31),
    background = Color(0xFFF5FAFC),
    onBackground = Color(0xFF171D1F),
    surface = Color(0xFFF5FAFC),
    onSurface = Color(0xFF171D1F),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF3F484B),
    outline = Color(0xFF6F797C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F7),
    surfaceContainer = Color(0xFFE9EFF1),
    surfaceContainerHigh = Color(0xFFE3E9EB),
    surfaceContainerHighest = Color(0xFFDEE3E5)
)

private val DarkColors = darkColorScheme(
    primary = BrandColor,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF004E5F),
    onPrimaryContainer = Color(0xFFB6EBFF),
    secondary = Color(0xFFB3CBD2),
    onSecondary = Color(0xFF1D343A),
    secondaryContainer = Color(0xFF344A51),
    onSecondaryContainer = Color(0xFFCEE7EE),
    tertiary = Color(0xFFADC9E5),
    onTertiary = Color(0xFF133349),
    tertiaryContainer = Color(0xFF2C4A61),
    onTertiaryContainer = Color(0xFFCCE5FF),
    background = Color(0xFF0F1416),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0F1416),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484B),
    onSurfaceVariant = Color(0xFFBFC8CC),
    outline = Color(0xFF899295),
    surfaceContainerLowest = Color(0xFF0A0F11),
    surfaceContainerLow = Color(0xFF171D1F),
    surfaceContainer = Color(0xFF1B2123),
    surfaceContainerHigh = Color(0xFF252B2D),
    surfaceContainerHighest = Color(0xFF303638)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

// ---------- Daten & Speicherung ----------

class NamedList(
    val name: String,
    val items: SnapshotStateList<String> = mutableStateListOf()
)

private const val PREFS_NAME = "zufallsgenerator"
private const val PREFS_KEY = "data"

private fun loadData(context: Context): Pair<List<NamedList>, Int> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(PREFS_KEY, null) ?: return Pair(emptyList(), 0)
    return try {
        val root = JSONObject(json)
        val arr = root.getJSONArray("lists")
        val lists = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val itemsJson = obj.getJSONArray("items")
            NamedList(
                obj.getString("name"),
                (0 until itemsJson.length()).map { itemsJson.getString(it) }.toMutableStateList()
            )
        }
        Pair(lists, root.optInt("selected", 0))
    } catch (e: Exception) {
        Pair(emptyList(), 0)
    }
}

private fun saveData(context: Context, lists: List<NamedList>, selected: Int) {
    val arr = JSONArray()
    lists.forEach { list ->
        arr.put(
            JSONObject()
                .put("name", list.name)
                .put("items", JSONArray(list.items.toList()))
        )
    }
    val root = JSONObject().put("selected", selected).put("lists", arr)
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(PREFS_KEY, root.toString()).apply()
}

// ---------- Oberfläche ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultListName = stringResource(R.string.default_list_name)

    val initial = remember { loadData(context) }
    val lists = remember {
        initial.first.ifEmpty { listOf(NamedList(defaultListName)) }.toMutableStateList()
    }
    var selected by remember { mutableIntStateOf(initial.second.coerceIn(0, lists.lastIndex)) }

    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var highlighted by remember { mutableIntStateOf(-1) }
    var spinning by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val current = lists[selected]
    val items = current.items

    fun persist() = saveData(context, lists, selected)

    fun resetResult() {
        result = null
        highlighted = -1
    }

    fun addItem() {
        val text = input.trim()
        if (text.isNotEmpty()) {
            items.add(text)
            input = ""
            resetResult()
            persist()
        }
    }

    // Glücksrad-Animation: läuft die Liste durch und wird zum Ende hin langsamer
    fun spin() {
        val n = items.size
        if (spinning || n < 2) return
        spinning = true
        result = null
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
            result = items.getOrNull(winner)
            spinning = false
        }
    }

    // Dialog: neue Liste anlegen
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
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
                        lists.add(NamedList(name))
                        selected = lists.lastIndex
                        resetResult()
                        persist()
                    }
                    newName = ""
                    showDialog = false
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { newName = ""; showDialog = false }) {
                    Text(stringResource(R.string.cancel))
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
        Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium)

        // Listen-Auswahl
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(lists) { index, list ->
                    FilterChip(
                        selected = index == selected,
                        onClick = {
                            if (!spinning) {
                                selected = index
                                resetResult()
                                persist()
                            }
                        },
                        label = { Text(list.name) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { if (!spinning) showDialog = true },
                        label = { Text(stringResource(R.string.new_list)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                }
            }
            IconButton(
                onClick = {
                    lists.removeAt(selected)
                    selected = selected.coerceAtMost(lists.lastIndex)
                    resetResult()
                    persist()
                },
                enabled = lists.size > 1 && !spinning
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

        // Einträge der aktuellen Liste
        LazyColumn(
            modifier = Modifier.weight(1f),
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
                                items.removeAt(index)
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

        Button(
            onClick = { spin() },
            enabled = items.size >= 2 && !spinning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (spinning) R.string.spinning else R.string.spin))
        }

        if (items.isNotEmpty()) {
            TextButton(
                onClick = {
                    items.clear()
                    resetResult()
                    persist()
                },
                enabled = !spinning,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.delete_all))
            }
        }
    }
}
