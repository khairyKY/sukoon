package com.sukoon.app.ui.logbook

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.sukoon.app.ai.CarbEstimate
import com.sukoon.app.food.Dishes
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.sukoon.app.R
import com.sukoon.app.food.Food
import com.sukoon.app.food.MyFoods
import com.sukoon.app.food.OpenFoodFacts
import com.sukoon.app.food.PlateItem
import com.sukoon.app.food.portions
import com.sukoon.app.ui.theme.CaptionMuted
import com.sukoon.app.ui.theme.HeadlineSerifFontFamily
import com.sukoon.app.ui.theme.Sage
import com.sukoon.app.ui.theme.SageDeep
import com.sukoon.app.ui.theme.SageMist
import com.sukoon.app.ui.theme.StateLow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FoodFilter { ALL, YOURS, EGYPT }

/**
 * Adding a food to the plate (design "Meal · search", "how much?"): yours first and instantly, then
 * Open Food Facts as you type; a barcode straight to its amount; anything missing made yours by hand.
 */
@Composable
internal fun FoodPicker(
    scanFirst: Boolean,
    onAdd: (PlateItem) -> Unit,
    onDismiss: () -> Unit,
    /** The AI's carb estimate, for anything not found (null without an AI key). */
    estimate: (suspend (String, List<ByteArray>) -> CarbEstimate)? = null,
) {
    val context = LocalContext.current
    val mine = remember { MyFoods.get(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FoodFilter.ALL) }
    var found by remember { mutableStateOf<List<Food>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Food?>(null) }
    var making by remember { mutableStateOf<Pair<String, String?>?>(null) } // a food of yours: its name, and the barcode it was scanned with
    var scanning by remember { mutableStateOf(scanFirst) }
    var notFound by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var askError by remember { mutableStateOf<String?>(null) }
    val arabic = java.util.Locale.getDefault().language == "ar"

    /** "Ask the AI": its estimate becomes one of yours, a portion as it described. */
    fun ask(text: String) {
        val run = estimate ?: return
        asking = true
        askError = null
        scope.launch {
            runCatching { run(text, emptyList()) }
                .onSuccess { r ->
                    picked = Food(
                        name = r.title.ifBlank { text },
                        brand = listOf(r.items.joinToString(", ") { it.first }, r.confidence).filter { it.isNotBlank() }.joinToString(" · "),
                        per100 = listOf(r.carbsGrams.toDouble(), 0.0, 0.0, 0.0, 0.0, 0.0),
                        own = true,
                    )
                }
                .onFailure { askError = it.message }
            asking = false
        }
    }

    fun add(item: PlateItem) {
        mine.remember(item)
        onAdd(item)
    }

    fun lookUp(code: String) {
        scanning = false
        mine.byCode(code)?.let { picked = it; return }
        loading = true
        scope.launch {
            val food = runCatching { OpenFoodFacts.barcode(code) }.getOrNull()
            loading = false
            if (food != null) picked = food else notFound = code
        }
    }

    // Search as you type, after a short pause (no request per letter).
    LaunchedEffect(query, filter) {
        failed = false
        if (query.trim().length < 2 || filter == FoodFilter.YOURS) {
            found = emptyList()
            loading = false
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        found = runCatching { OpenFoodFacts.search(query, filter == FoodFilter.EGYPT) }.onFailure { failed = true }.getOrDefault(emptyList())
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val food = picked
            val own = making
            when {
                scanning -> BarcodeScanner(onCode = ::lookUp, onClose = { scanning = false })
                food != null -> {
                    BackHandler { picked = null }
                    AmountView(food, onBack = { picked = null }, onAdd = ::add)
                }
                own != null -> {
                    BackHandler { making = null }
                    OwnFoodView(own.first, onBack = { making = null }) { name, carbs ->
                        picked = Food(name = name, code = own.second, per100 = listOf(carbs, 0.0, 0.0, 0.0, 0.0, 0.0), own = true)
                        making = null
                    }
                }
                else -> Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp).padding(top = 10.dp)) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundIconButton(R.drawable.ic_close, stringResource(R.string.entry_close), onDismiss)
                        OutlinedTextField(
                            query,
                            { query = it; notFound = null },
                            placeholder = { Text(stringResource(R.string.food_search_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            trailingIcon = {
                                Box(
                                    Modifier.padding(end = 4.dp).size(44.dp).clip(RoundedCornerShape(12.dp)).background(SageMist).clickable { scanning = true },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(painterResource(R.drawable.ic_barcode), stringResource(R.string.food_scan), tint = SageDeep, modifier = Modifier.size(22.dp)) }
                            },
                        )
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(FoodFilter.ALL to R.string.food_filter_all, FoodFilter.YOURS to R.string.food_filter_yours, FoodFilter.EGYPT to R.string.food_filter_egypt).forEach { (f, label) ->
                            val on = filter == f
                            Text(
                                stringResource(label),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                                    .clickable { filter = f }.padding(horizontal = 14.dp, vertical = 9.dp),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = Sage) else Spacer(Modifier.padding(top = 14.dp))
                    val yours = if (query.isBlank()) mine.all().take(30) else mine.search(query)
                    val dishes = remember(query) { Dishes.search(context, query, arabic) }.filter { d -> yours.none { it.key == d.key } }
                    val theirs = found.filter { f -> yours.none { it.key == f.key } }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        notFound?.let { code ->
                            item { Text(stringResource(R.string.food_barcode_missing, code), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 10.dp)) }
                            item { LinkRow(stringResource(R.string.food_make_own_scanned)) { making = "" to code } }
                        }
                        if (yours.isNotEmpty()) item { Eyebrow(stringResource(if (query.isBlank()) R.string.food_your_usual else R.string.food_filter_yours), Modifier.padding(vertical = 8.dp)) }
                        items(yours, key = { "y" + it.key }) { f -> FoodRow(f, yours = true) { picked = f } }
                        if (filter != FoodFilter.YOURS && dishes.isNotEmpty()) item { Eyebrow(stringResource(R.string.food_home_dishes), Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                        if (filter != FoodFilter.YOURS) items(dishes, key = { "d" + it.key }) { f -> FoodRow(f, yours = false) { picked = f } }
                        if (filter != FoodFilter.YOURS && theirs.isNotEmpty()) item { Eyebrow(stringResource(R.string.food_from_off), Modifier.padding(top = 14.dp, bottom = 8.dp)) }
                        if (filter != FoodFilter.YOURS) items(theirs, key = { "o" + it.key }) { f -> FoodRow(f, yours = false) { picked = f } }
                        if (failed) item { Text(stringResource(R.string.food_offline), fontSize = 13.5.sp, color = StateLow, modifier = Modifier.padding(vertical = 10.dp)) }
                        if (query.isNotBlank() && estimate != null) item {
                            Row(Modifier.fillMaxWidth().clickable(enabled = !asking) { ask(query.trim()) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = SageDeep, modifier = Modifier.size(20.dp))
                                Text(
                                    stringResource(if (asking) R.string.carb_ai_busy else R.string.food_ask_ai, query.trim()),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SageDeep,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                        askError?.let { item { Text(it, fontSize = 13.sp, color = StateLow) } }
                        if (query.isNotBlank()) item { LinkRow(stringResource(R.string.food_make_own, query.trim())) { making = query.trim() to null } }
                        item { Text(stringResource(R.string.food_off_note), fontSize = 12.sp, color = CaptionMuted, modifier = Modifier.padding(vertical = 14.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: Food, yours: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FoodImage(food, 48.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(listOfNotNull(food.name, food.brand).joinToString(", "), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
            Text(
                if (yours && food.lastAmount != null) amountText(food, food.lastAmount) + " · " + gramsText(food.of(food.lastAmount)[Food.CARBS]) + " " + stringResource(R.string.food_carbs_word)
                else if (food.servingLabel != null && food.servingGrams != null) "1 " + food.servingLabel + " · " + gramsText(food.of(food.servingGrams)[Food.CARBS]) + " " + stringResource(R.string.food_carbs_word)
                else if (food.own) stringResource(R.string.food_per_portion, gramsText(food.per100[Food.CARBS]))
                else stringResource(R.string.food_per_100, gramsText(food.per100[Food.CARBS])),
                fontSize = 13.sp,
                color = CaptionMuted,
            )
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(SageMist), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = SageDeep, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(text, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = SageDeep)
}

/** "How much?": its package parts or serving, a −/+ for the rest, and what that holds. */
@Composable
private fun AmountView(food: Food, onBack: () -> Unit, onAdd: (PlateItem) -> Unit) {
    val options = remember(food) { portions(food) }
    var amount by remember(food) { mutableDoubleStateOf(food.lastAmount ?: options.getOrNull(if (food.own) 1 else 0)?.second ?: 100.0) }
    val sums = food.of(amount)
    val step = if (food.own) 0.5 else if (amount <= 50) 5.0 else 10.0
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(R.drawable.ic_back, stringResource(R.string.entry_close), onBack)
            FoodImage(food, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(food.name, fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2)
                Text(
                    listOfNotNull(food.brand, food.packageGrams?.let { gramsText(it) }, if (food.own) stringResource(R.string.food_per_portion, gramsText(food.per100[Food.CARBS])) else stringResource(R.string.food_per_100, gramsText(food.per100[Food.CARBS]))).joinToString(" · "),
                    fontSize = 13.5.sp,
                    color = CaptionMuted,
                )
            }
        }
        Eyebrow(stringResource(R.string.food_how_much))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                val on = kotlin.math.abs(amount - value) < 0.01
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Sage.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface)
                        .border(if (on) 2.dp else 1.dp, if (on) Sage else outline(), RoundedCornerShape(12.dp))
                        .clickable { amount = value },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(portionLabel(food, label, value), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (on) SageDeep else MaterialTheme.colorScheme.onBackground, maxLines = 2)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Stepper(R.drawable.ic_minus, stringResource(R.string.food_less)) { amount = (amount - step).coerceAtLeast(step) }
            Text(amountText(food, amount), fontFamily = HeadlineSerifFontFamily, fontWeight = FontWeight.Light, fontSize = 44.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 22.dp))
            Stepper(R.drawable.ic_plus, stringResource(R.string.food_more)) { amount += step }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, outline(), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(gramsText(sums[Food.CARBS]) + " " + stringResource(R.string.food_carbs_word), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            if (!food.own) {
                Text(
                    stringResource(R.string.food_rest, gramsText(sums[1]), gramsText(sums[3]), Math.round(sums[Food.KCAL]).toString()),
                    fontSize = 13.sp,
                    color = CaptionMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(Sage).clickable { onAdd(PlateItem(food, amount)) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.food_add_to_plate, gramsText(sums[Food.CARBS])), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Stepper(icon: Int, description: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = description, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
    }
}

/** One of yours by hand: its name and the carbs in one portion (a bowl, a loaf: however you count it). */
@Composable
private fun OwnFoodView(name: String, onBack: () -> Unit, onDone: (String, Double) -> Unit) {
    var title by remember { mutableStateOf(name) }
    var carbs by remember { mutableStateOf("") }
    val grams = carbs.toDoubleOrNull()?.takeIf { it >= 0 }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(R.drawable.ic_back, stringResource(R.string.entry_close), onBack)
            Text(stringResource(R.string.food_own_title), fontFamily = HeadlineSerifFontFamily, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.food_own_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            carbs,
            { carbs = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
            label = { Text(stringResource(R.string.food_own_carbs)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.food_own_hint), fontSize = 13.sp, color = CaptionMuted)
        Spacer(Modifier.weight(1f))
        val ready = title.isNotBlank() && grams != null
        Box(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(if (ready) Sage else Sage.copy(alpha = 0.4f))
                .clickable(enabled = ready) { onDone(title.trim(), grams!!) },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.food_own_next), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * Sukoon's own camera on the barcode (CameraX + ML Kit's model inside the app): it opens at once
 * and reads offline, where Google's scanner opens another app and may download its model first.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeScanner(onCode: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (!ok) onClose() }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val done = remember { AtomicBoolean(false) }
            val worker = remember { Executors.newSingleThreadExecutor() }
            val scanner = remember {
                BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E).build(),
                )
            }
            DisposableEffect(Unit) {
                onDispose {
                    ProcessCameraProvider.getInstance(context).let { f -> f.addListener({ f.get().unbindAll() }, ContextCompat.getMainExecutor(context)) }
                    scanner.close()
                    worker.shutdown()
                }
            }
            AndroidView(
                factory = { ctx ->
                    val view = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(worker) { frame ->
                            val image = frame.image
                            if (image == null || done.get()) {
                                frame.close()
                                return@setAnalyzer
                            }
                            scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let { code -> if (done.compareAndSet(false, true)) onCode(code) } }
                                .addOnCompleteListener { frame.close() }
                        }
                        runCatching {
                            future.get().unbindAll()
                            future.get().bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.align(Alignment.Center).size(width = 280.dp, height = 170.dp).border(3.dp, Color.White, RoundedCornerShape(20.dp)))
        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(R.drawable.ic_close, stringResource(R.string.entry_close), onClose)
        }
        Text(
            stringResource(R.string.food_scan_hint),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun portionLabel(food: Food, label: String?, value: Double): String = when {
    food.own -> amountText(food, value)
    label == "dish" -> formatAmountLocalized(value / (food.servingGrams ?: value)) + " " + food.servingLabel.orEmpty()
    label == "serving" -> stringResource(R.string.food_serving)
    label != null -> stringResource(R.string.food_pack, label)
    else -> gramsText(value) + " " + stringResource(R.string.food_grams_unit)
}

/** "200 g", or "1.5 portions" for one of yours. */
@Composable
internal fun amountText(food: Food, amount: Double): String =
    if (food.own) stringResource(R.string.food_portions, formatAmountLocalized(amount)) else gramsText(amount) + " " + stringResource(R.string.food_grams_unit)

internal fun gramsText(x: Double): String = formatAmountLocalized(if (x >= 10) Math.round(x).toDouble() else Math.round(x * 10) / 10.0)

private val foodImages = android.util.LruCache<String, ImageBitmap>(120)

/** The product's photo (fetched once, then kept for the session), else a plate. */
@Composable
internal fun FoodImage(food: Food, size: androidx.compose.ui.unit.Dp) {
    val url = food.imageUrl
    val image by androidx.compose.runtime.produceState<ImageBitmap?>(url?.let { foodImages.get(it) }, url) {
        if (url != null && value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
            }?.also { foodImages.put(url, it) }
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            androidx.compose.foundation.Image(shown, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Icon(painterResource(R.drawable.ic_food), contentDescription = null, tint = CaptionMuted, modifier = Modifier.size(size / 2))
        }
    }
}
