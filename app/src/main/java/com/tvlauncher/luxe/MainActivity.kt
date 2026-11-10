package com.tvlauncher.luxe

import android.content.*
import android.net.*
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.*
import kotlin.math.*
import kotlin.random.Random

object Flags { var introShown = false }

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent { Root() }
    }
}

// ---------- Datos ----------
class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val intent: Intent)

fun loadApps(c: Context): List<AppItem> {
    val pm = c.packageManager
    val seen = HashSet<String>()
    val out = ArrayList<AppItem>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == c.packageName || !seen.add(pkg)) continue
            val li = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
            out.add(AppItem(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap(), li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        }
    }
    return out.sortedBy { it.label.lowercase() }
}

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("luxe", Context.MODE_PRIVATE)
    var accent by mutableIntStateOf(sp.getInt("accent", 0))
        private set
    var wall by mutableIntStateOf(sp.getInt("wall", 0))
        private set
    var is24 by mutableStateOf(sp.getBoolean("is24", true))
        private set
    val favs = mutableStateListOf<String>().also { it.addAll(sp.getStringSet("favs", emptySet())!!) }
    fun setAccent(v: Int) { accent = v; sp.edit().putInt("accent", v).apply() }
    fun setWall(v: Int) { wall = v; sp.edit().putInt("wall", v).apply() }
    fun set24(v: Boolean) { is24 = v; sp.edit().putBoolean("is24", v).apply() }
    fun toggleFav(p: String) { if (!favs.remove(p)) favs.add(p); sp.edit().putStringSet("favs", favs.toSet()).apply() }
}

val ACCENTS = listOf(0xFFD4AF37, 0xFF00E5FF, 0xFFFF2E93, 0xFF8B5CF6, 0xFF10D98C, 0xFFFF4D4D, 0xFFFF9F1C).map { Color(it) }
val NEON = listOf(Color(0xFF00E5FF), Color(0xFFFF2E93), Color(0xFFB6FF00), Color(0xFFFFD400), Color(0xFF8B5CF6))

// ---------- Fondos ----------
sealed class Wall {
    class Grad(val c: List<Color>) : Wall()
    class Nature(val k: Int) : Wall()
}
fun g(a: Long, b: Long) = Wall.Grad(listOf(Color(a), Color(b)))
val WALLS: List<Wall> = listOf(
    Wall.Nature(0), Wall.Nature(1), Wall.Nature(2), Wall.Nature(3),
    g(0xFF0F2027, 0xFF2C5364), g(0xFF42275A, 0xFF734B6D), g(0xFFFF512F, 0xFFDD2476),
    g(0xFF1A2980, 0xFF26D0CE), g(0xFF000000, 0xFF434343), g(0xFF11998E, 0xFF38EF7D), g(0xFF8E2DE2, 0xFF4A00E0)
)

fun DrawScope.sky(vararg c: Long) = drawRect(Brush.verticalGradient(c.map { Color(it) }))
fun DrawScope.stars(n: Int, maxY: Float) {
    val r = Random(9)
    repeat(n) { drawCircle(Color.White.copy(0.3f + r.nextFloat() * 0.6f), 1f + r.nextFloat() * 2f, Offset(r.nextFloat() * size.width, r.nextFloat() * maxY)) }
}
fun DrawScope.ridge(base: Float, amp: Float, cycles: Float, phase: Float, color: Color) {
    val f = cycles * 6.2832f / size.width
    val p = Path().apply { moveTo(0f, size.height) }
    var x = 0f
    while (x <= size.width) { p.lineTo(x, base - amp * (0.6f * sin(x * f + phase) + 0.4f * sin(x * f * 2.3f + phase * 1.7f))); x += 8f }
    p.lineTo(size.width, size.height); p.close()
    drawPath(p, color)
}
fun DrawScope.drawWall(w: Wall) {
    val W = size.width; val H = size.height
    when (w) {
        is Wall.Grad -> {
            drawRect(Brush.linearGradient(w.c, Offset.Zero, Offset(W, H)))
            val r = Random(3)
            repeat(260) { drawCircle(Color.White.copy(0.035f), 1f + r.nextFloat() * 2.5f, Offset(r.nextFloat() * W, r.nextFloat() * H)) }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(0.14f), Color.Transparent), Offset(W * .8f, H * .15f), W * .5f), W * .5f, Offset(W * .8f, H * .15f))
        }
        is Wall.Nature -> when (w.k) {
            0 -> { sky(0xFF1B1B4B, 0xFFD9467A, 0xFFFFB45A); drawCircle(Color(0xFFFFE29A), H * .12f, Offset(W * .5f, H * .58f))
                ridge(H * .64f, H * .09f, 2.2f, .5f, Color(0xFF5B3A7A)); ridge(H * .74f, H * .10f, 1.6f, 2f, Color(0xFF3A2766)); ridge(H * .86f, H * .08f, 2.8f, 4f, Color(0xFF1C1440)) }
            1 -> { sky(0xFF050A1F, 0xFF14284B); stars(120, H * .6f); drawCircle(Color(0xFFE8EEF9), H * .06f, Offset(W * .78f, H * .2f))
                ridge(H * .78f, H * .06f, 1.5f, 1f, Color(0xFF0B1A2E))
                for (i in 0 until 22) { val x = W * (i + .5f) / 22f; val h = H * (.18f + .1f * ((i * 7) % 5) / 5f); val b = H * .97f
                    val p = Path().apply { moveTo(x, b - h); lineTo(x + h * .22f, b); lineTo(x - h * .22f, b); close() }; drawPath(p, Color(0xFF040B14)) } }
            2 -> { sky(0xFFFFC27A, 0xFFFF7E6B, 0xFF2B4B7A); drawCircle(Color(0xFFFFE6B0), H * .1f, Offset(W * .5f, H * .6f))
                drawRect(Color(0xFF1B3A63), Offset(0f, H * .62f), androidx.compose.ui.geometry.Size(W, H * .38f))
                repeat(6) { ridge(H * (.66f + it * .06f), H * .012f, 6f + it, it * 1.3f, Color(0xFF0E2A52).copy(0.35f + it * 0.1f)) } }
            else -> { sky(0xFF020812, 0xFF0B2A3A); stars(100, H * .6f)
                val cols = listOf(0xFF3DFFB0, 0xFF5AA8FF, 0xFFB46BFF)
                repeat(3) { i -> val p = Path(); val y0 = H * (.3f + i * .08f); p.moveTo(0f, y0); var x = 0f
                    while (x <= W) { p.lineTo(x, y0 + H * .08f * sin(x / W * 6.28f * (1.2f + i * .4f) + i)); x += 10f }
                    drawPath(p, Color(cols[i]).copy(0.18f), style = Stroke(H * .10f, cap = StrokeCap.Round)) }
                ridge(H * .9f, H * .04f, 2f, 1f, Color(0xFF02060C)) }
        }
    }
}

// ---------- Raíz e intro ----------
@Composable fun Root() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var intro by remember { mutableStateOf(!Flags.introShown) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (intro) Intro { Flags.introShown = true; intro = false } else Main(prefs)
    }
}

@Composable fun Intro(onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(4200, easing = LinearEasing)); onDone() }
    val splats = remember { val r = Random(5); List(28) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(Modifier.fillMaxSize().background(Color(0xFF08080F))) {
        val p = t.value
        val cx = size.width / 2; val cy = size.height * .46f; val ew = size.width * .24f
        val o = ((p - .05f) / .3f).coerceIn(0f, 1f); val open = o * o * (3 - 2 * o)
        val eh = ew * .55f * open
        splats.forEachIndexed { i, s ->
            val a = ((p - (.2f + .5f * i / splats.size)) / .08f).coerceIn(0f, 1f)
            if (a > 0f) { val c = NEON[i % NEON.size]; val rad = (12f + s[2] * 46f) * a
                drawCircle(c.copy(.5f * a), rad, Offset(s[0] * size.width, s[1] * size.height)); drawCircle(c.copy(.9f * a), rad * .45f, Offset(s[0] * size.width, s[1] * size.height)) }
        }
        val eye = Path().apply { moveTo(cx - ew, cy); quadraticBezierTo(cx, cy - eh * 2, cx + ew, cy); quadraticBezierTo(cx, cy + eh * 2, cx - ew, cy); close() }
        clipPath(eye) {
            drawRect(Color(0xFFF4F4F8))
            val ir = ew * .30f; val ix = cx + sin(p * 9f) * ew * .28f * open
            drawCircle(Brush.radialGradient(listOf(Color(0xFF00E5FF), Color(0xFF8B5CF6), Color(0xFFFF2E93)), Offset(ix, cy), ir), ir, Offset(ix, cy))
            drawCircle(Color.Black, ir * .45f, Offset(ix, cy)); drawCircle(Color.White, ir * .14f, Offset(ix - ir * .3f, cy - ir * .3f))
        }
        val br = Brush.linearGradient(NEON, Offset(cx - ew, cy), Offset(cx + ew, cy))
        drawPath(eye, br, alpha = .25f, style = Stroke(34f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(eye, br, style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val ra = ((p - .3f) / .25f).coerceIn(0f, 1f)
        for (k in 0..8) { val ang = PI.toFloat() * (1.12f + .76f * k / 8f); val d0 = ew * .62f; val d1 = d0 + ew * .28f * ra
            drawLine(NEON[k % NEON.size], Offset(cx + cos(ang) * d0, cy + sin(ang) * d0 * .8f), Offset(cx + cos(ang) * d1, cy + sin(ang) * d1 * .8f), 10f, StrokeCap.Round) }
        for (k in 0..4) { val dl = ((p - .45f - k * .03f) / .35f).coerceIn(0f, 1f); val x = cx - ew * .6f + k * ew * .3f; val y0 = cy + eh * .9f; val y1 = y0 + (60f + k * 34f) * dl
            drawLine(NEON[(k + 1) % NEON.size], Offset(x, y0), Offset(x, y1), 12f, StrokeCap.Round); drawCircle(NEON[(k + 1) % NEON.size], 12f * dl, Offset(x, y1)) }
        drawRect(Color.Black.copy(((p - .88f) / .12f).coerceIn(0f, 1f)))
    }
}

// ---------- Componentes ----------
@OptIn(ExperimentalFoundationApi::class)
@Composable fun FocusBox(accent: Color, shape: Shape, modifier: Modifier = Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable BoxScope.(Boolean) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (f) 1.08f else 1f, label = "s")
    Box(modifier.graphicsLayer(scaleX = s, scaleY = s).onFocusChanged { f = it.isFocused }.clip(shape)
        .background(Color.White.copy(if (f) .20f else .08f)).border(if (f) 3.dp else 1.dp, if (f) accent else Color.White.copy(.12f), shape)
        .combinedClickable(onLongClick = onLong, onClick = onClick)) { content(f) }
}

@Composable fun CornerBtn(icon: String, label: String, accent: Color, onClick: () -> Unit) {
    FocusBox(accent, RoundedCornerShape(20.dp), onClick = onClick) { f ->
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 26.sp, color = if (f) accent else Color.White)
            Text(label, fontSize = 12.sp, color = Color.White.copy(.85f))
        }
    }
}

@Composable fun Chip(label: String, sel: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusBox(accent, RoundedCornerShape(50), modifier, onClick = onClick) {
        Text(label, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = if (sel) accent else Color.White, fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable fun rememberNet(): String {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf("Sin red") }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        fun eval(): String { val c = cm.getNetworkCapabilities(cm.activeNetwork)
            return when { c == null -> "Sin red"; c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi conectado"; c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"; else -> "Conectado" } }
        st = eval()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { st = eval() }
            override fun onLost(n: Network) { st = "Sin red" }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { st = eval() }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return st
}

// ---------- Reloj estilo calculadora ----------
val SEG = arrayOf("abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg")
fun DrawScope.digit(x: Float, w: Float, h: Float, ch: Char, col: Color) {
    val t = w * .2f; val m = h / 2
    val segs = if (ch.isDigit()) SEG[ch - '0'] else ""
    val r = mapOf('a' to floatArrayOf(t, 0f, w - t, t), 'd' to floatArrayOf(t, h - t, w - t, h), 'g' to floatArrayOf(t, m - t / 2, w - t, m + t / 2),
        'f' to floatArrayOf(0f, t * 1.1f, t, m - t * .1f), 'e' to floatArrayOf(0f, m + t * .1f, t, h - t * 1.1f),
        'b' to floatArrayOf(w - t, t * 1.1f, w, m - t * .1f), 'c' to floatArrayOf(w - t, m + t * .1f, w, h - t * 1.1f))
    for ((k, v) in r) {
        val on = k in segs
        if (on) drawRoundRect(col.copy(.22f), Offset(x + v[0] - 3, v[1] - 3), androidx.compose.ui.geometry.Size(v[2] - v[0] + 6, v[3] - v[1] + 6), androidx.compose.ui.geometry.CornerRadius(t / 2))
        drawRoundRect(col.copy(if (on) 1f else .07f), Offset(x + v[0], v[1]), androidx.compose.ui.geometry.Size(v[2] - v[0], v[3] - v[1]), androidx.compose.ui.geometry.CornerRadius(t / 3))
    }
}
@Composable fun SevenSeg(text: String, color: Color, h: Dp, colonOn: Boolean) {
    var w = 0.dp
    text.forEach { w += (if (it == ':') h * .22f else h * .56f) + h * .14f }
    Canvas(Modifier.size(w, h)) {
        val hp = size.height; var x = 0f
        for (ch in text) {
            if (ch == ':') { val a = if (colonOn) 1f else .12f; val rr = hp * .06f
                drawCircle(color.copy(a), rr, Offset(x + hp * .11f, hp * .33f)); drawCircle(color.copy(a), rr, Offset(x + hp * .11f, hp * .67f)); x += hp * .36f }
            else { digit(x, hp * .56f, hp, ch, color); x += hp * .70f }
        }
    }
}
@Composable fun Clock(prefs: Prefs, accent: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h24 = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE)
    val h = if (prefs.is24) h24 else (if (h24 % 12 == 0) 12 else h24 % 12)
    val hs = if (!prefs.is24 && h < 10) " $h" else "%02d".format(h)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            SevenSeg("$hs:" + "%02d".format(m), accent, 84.dp, (now / 500) % 2 == 0L)
            if (!prefs.is24) Text(if (h24 < 12) " AM" else " PM", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault()).format(Date(now)).replaceFirstChar { it.uppercase() }, color = Color.White.copy(.9f), fontSize = 18.sp)
    }
}

// ---------- Pantallas ----------
fun open(c: Context, action: String) = try { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { Toast.makeText(c, "No disponible", Toast.LENGTH_SHORT).show() }
fun powerOff(c: Context) {
    try { c.startActivity(Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN").putExtra("android.intent.extra.KEY_CONFIRM", false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) {
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot -p")) }
        catch (e2: Exception) { Toast.makeText(c, "Este equipo no permite apagar desde apps (requiere root)", Toast.LENGTH_LONG).show() }
    }
}

@Composable fun Main(prefs: Prefs) {
    val ctx = LocalContext.current
    val accent = ACCENTS[prefs.accent]
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    DisposableEffect(Unit) {
        val lc = (ctx as ComponentActivity).lifecycle
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) apps = loadApps(ctx) }
        lc.addObserver(ob); onDispose { lc.removeObserver(ob) }
    }
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val net = rememberNet()
    val first = remember { FocusRequester() }
    BackHandler(enabled = !settings && !confirm) { }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawWall(WALLS[prefs.wall]); drawRect(Brush.verticalGradient(listOf(Color.Black.copy(.35f), Color.Transparent, Color.Black.copy(.6f)))) }
        if (settings) SettingsPanel(prefs, accent) { settings = false }
        else Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                CornerBtn("⚙", "Launcher", accent) { settings = true }
                Clock(prefs, accent)
                CornerBtn("⚙", "Android", accent) { open(ctx, Settings.ACTION_SETTINGS) }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Chip("Todas", tab == 0, accent, Modifier.focusRequester(first)) { tab = 0 }
                Chip("★ Favoritos", tab == 1, accent) { tab = 1 }
            }
            Spacer(Modifier.height(14.dp))
            val shown = if (tab == 0) apps else apps.filter { it.pkg in prefs.favs }
            if (shown.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Aún no hay favoritos.\nMantén OK sobre una app para añadirla.", color = Color.White.copy(.8f), fontSize = 18.sp) }
            else LazyVerticalGrid(GridCells.Fixed(6), Modifier.weight(1f), contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(shown.size, key = { shown[it].pkg }) { i ->
                    val a = shown[i]
                    FocusBox(accent, RoundedCornerShape(22.dp), Modifier.fillMaxWidth().aspectRatio(1.15f), onLong = { prefs.toggleFav(a.pkg) },
                        onClick = { try { ctx.startActivity(a.intent) } catch (e: Exception) { } }) {
                        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Image(a.icon, null, Modifier.size(64.dp)); Spacer(Modifier.height(8.dp))
                            Text(a.label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (a.pkg in prefs.favs) Text("★", Modifier.align(Alignment.TopEnd).padding(8.dp), color = accent, fontSize = 16.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                CornerBtn("📶", net, accent) { open(ctx, Settings.ACTION_WIFI_SETTINGS) }
                Text("Mantén OK sobre una app para marcar ★", color = Color.White.copy(.6f), fontSize = 13.sp)
                CornerBtn("⏻", "Apagar", accent) { confirm = true }
            }
        }
        if (confirm) {
            BackHandler { confirm = false }
            val cancel = remember { FocusRequester() }
            LaunchedEffect(Unit) { cancel.requestFocus() }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("¿Apagar el equipo?", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Chip("Cancelar", true, accent, Modifier.focusRequester(cancel)) { confirm = false }
                        Chip("Apagar", false, accent) { confirm = false; powerOff(ctx) }
                    }
                }
            }
        }
    }
}

@Composable fun SettingsPanel(prefs: Prefs, accent: Color, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Column(Modifier.fillMaxSize().padding(40.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xDD0A0A14)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Ajustes del launcher", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Color de acento", color = Color.White.copy(.7f))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) {
            itemsIndexed(ACCENTS) { i, c ->
                FocusBox(c, CircleShape, (if (i == 0) Modifier.focusRequester(fr) else Modifier).size(56.dp), onClick = { prefs.setAccent(i) }) {
                    Box(Modifier.fillMaxSize().padding(10.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) { if (prefs.accent == i) Text("✓", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Text("Reloj", color = Color.White.copy(.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Chip("24 horas", prefs.is24, accent) { prefs.set24(true) }
            Chip("12 horas", !prefs.is24, accent) { prefs.set24(false) }
        }
        Text("Fondo (naturaleza y degradados)", color = Color.White.copy(.7f))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(6.dp)) {
            itemsIndexed(WALLS) { i, w ->
                FocusBox(accent, RoundedCornerShape(14.dp), Modifier.size(160.dp, 92.dp), onClick = { prefs.setWall(i) }) {
                    Canvas(Modifier.fillMaxSize()) { drawWall(w) }
                    if (prefs.wall == i) Text("✓", Modifier.align(Alignment.TopEnd).padding(8.dp), color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Chip("Cerrar", false, accent) { onClose() }
    }
}
