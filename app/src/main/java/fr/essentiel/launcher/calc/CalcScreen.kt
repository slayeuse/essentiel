package fr.essentiel.launcher.calc

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.essentiel.launcher.Divider
import fr.essentiel.launcher.IconAction
import fr.essentiel.launcher.Icons
import fr.essentiel.launcher.LineIconView
import fr.essentiel.launcher.Nav
import fr.essentiel.launcher.Palette
import fr.essentiel.launcher.ToolScaffold
import fr.essentiel.launcher.bodyStyle
import fr.essentiel.launcher.paper
import fr.essentiel.launcher.serifStyle
import org.json.JSONArray

private object CalcHistory {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("calculatrice", Context.MODE_PRIVATE)
    fun all(ctx: Context): List<Pair<String, String>> = try {
        val a = JSONArray(sp(ctx).getString("historique", "[]"))
        (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } }
    } catch (e: Exception) { emptyList() }
    fun add(ctx: Context, expr: String, result: String) {
        val list = (listOf(expr to result) + all(ctx)).take(40)
        val a = JSONArray()
        list.forEach { (e, r) -> a.put(JSONArray().put(e).put(r)) }
        sp(ctx).edit().putString("historique", a.toString()).apply()
    }
    fun clear(ctx: Context) = sp(ctx).edit().remove("historique").apply()
}

private const val OPS = "+−×÷^"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalcScreen(@Suppress("UNUSED_PARAMETER") nav: Nav) {
    val ctx = LocalContext.current
    val view = LocalView.current
    var expr by remember { mutableStateOf("") }
    var justEvaluated by remember { mutableStateOf(false) }
    var scientific by remember { mutableStateOf(false) }
    var degrees by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(CalcHistory.all(ctx)) }

    val preview = remember(expr, degrees) {
        if (expr.isBlank() || expr.none { it in OPS || it in "%!²√πe(" } && !expr.contains("sin") && !expr.contains("cos")) null
        else CalcEngine(degrees).eval(expr)?.let { CalcEngine.format(it) }
    }

    fun press(k: String) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val last = expr.lastOrNull()
        when (k) {
            "C" -> { expr = ""; justEvaluated = false }
            "⌫" -> { expr = expr.dropLast(if (expr.endsWith("sin(") || expr.endsWith("cos(") || expr.endsWith("tan(") || expr.endsWith("log(")) 4 else if (expr.endsWith("ln(")) 3 else 1); justEvaluated = false }
            "=" -> {
                val r = CalcEngine(degrees).eval(expr)
                if (r != null) {
                    val res = CalcEngine.format(r)
                    CalcHistory.add(ctx, expr, res); history = CalcHistory.all(ctx)
                    expr = res.replace(" ", ""); justEvaluated = true
                }
            }
            "()" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                expr += if (open > 0 && last != null && (last.isDigit() || last == ')' || last == 'π' || last == '%' || last == '!' || last == '²')) ")" else "("
                justEvaluated = false
            }
            "±" -> {
                // change le signe du dernier nombre
                val idx = expr.indexOfLast { !(it.isDigit() || it == ',') }
                val start = idx + 1
                expr = if (idx >= 0 && expr[idx] == '−' && (idx == 0 || expr[idx - 1] in "(+−×÷^")) expr.removeRange(idx, idx + 1)
                else expr.substring(0, start) + "−" + expr.substring(start)
            }
            "x²" -> expr += "²"
            "1/x" -> { expr = "1÷(" + expr + ")"; justEvaluated = false }
            "sin", "cos", "tan", "ln", "log" -> { if (justEvaluated) expr = ""; expr += "$k("; justEvaluated = false }
            "√" -> { if (justEvaluated) expr = ""; expr += "√"; justEvaluated = false }
            else -> {
                if (k in OPS && k.length == 1) {
                    if (expr.isEmpty() && k != "−") return
                    // remplace un opérateur par un autre
                    expr = if (last != null && last in OPS) expr.dropLast(1) + k else expr + k
                } else if (k == ",") {
                    val lastNumber = expr.takeLastWhile { it.isDigit() || it == ',' }
                    if (!lastNumber.contains(',')) expr += if (lastNumber.isEmpty()) "0," else ","
                } else {
                    if (justEvaluated) expr = ""
                    expr += k
                }
                justEvaluated = false
            }
        }
    }

    ToolScaffold("calcul", actions = {
        Text(
            if (scientific) "simple" else "scientifique", style = bodyStyle(13, Palette.Accent, bold = true),
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Palette.Accent, RoundedCornerShape(50))
                .clickable { scientific = !scientific }.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        IconAction(Icons.clock, if (showHistory) Palette.Accent else Palette.Ink) { showHistory = !showHistory }
    }) {
        // ----- Écran -----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showHistory) {
                Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HISTORIQUE", style = bodyStyle(13, Palette.Muted, bold = true), modifier = Modifier.weight(1f))
                        if (history.isNotEmpty()) Text(
                            "effacer", style = bodyStyle(13, Palette.Accent, bold = true),
                            modifier = Modifier.clickable { CalcHistory.clear(ctx); history = emptyList() }.padding(6.dp)
                        )
                    }
                    if (history.isEmpty()) Text("Aucun calcul pour l'instant.", style = bodyStyle(15, Palette.Muted))
                    LazyColumn {
                        items(history) { (e, r) ->
                            Column(
                                Modifier.fillMaxWidth().clickable { expr = r.replace(" ", ""); justEvaluated = true; showHistory = false }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(e, style = bodyStyle(15, Palette.Muted))
                                Text("= $r", style = serifStyle(26))
                            }
                            Divider()
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                    if (scientific) {
                        Text(if (degrees) "DEG" else "RAD", style = bodyStyle(12, Palette.Accent, bold = true),
                            modifier = Modifier.clickable { degrees = !degrees }.padding(4.dp))
                    }
                    val size = when {
                        expr.length > 22 -> 30
                        expr.length > 14 -> 42
                        else -> 64
                    }
                    Text(
                        expr.ifEmpty { "0" }, style = serifStyle(size, if (justEvaluated) Palette.Accent else Palette.Ink),
                        textAlign = TextAlign.End, maxLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (!justEvaluated && preview != null) "= $preview" else " ",
                        style = bodyStyle(24, Palette.Muted), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // ----- Touches -----
        val sci = listOf(
            listOf("sin", "cos", "tan", "π", "e"),
            listOf("ln", "log", "√", "^", "!"),
            listOf("x²", "1/x", "(", ")", "%"),
        )
        if (scientific) {
            sci.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(Palette.Card)
                                .border(1.dp, Palette.Line, RoundedCornerShape(50)).clickable { press(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, style = bodyStyle(15, bold = true)) }
                    }
                }
            }
        }
        val rows = listOf(
            listOf("C", "()", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("±", "0", ",", "="),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val isOp = k in listOf("÷", "×", "−", "+")
                    val isEq = k == "="
                    val isFn = k in listOf("C", "()", "%")
                    Box(
                        Modifier.weight(1f).aspectRatio(if (scientific) 1.45f else 1.15f).clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    isEq -> Palette.Accent
                                    isOp -> Palette.Accent.copy(alpha = 0.14f)
                                    isFn -> Palette.Card
                                    else -> Color(0xFFF2EFEA)
                                }
                            )
                            .border(1.dp, if (isEq) Palette.Accent else Palette.Line, RoundedCornerShape(50))
                            .combinedClickable(
                                onClick = { press(k) },
                                onLongClick = { if (k == "C") { expr = "" } },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "C" -> Text(if (expr.isEmpty()) "AC" else "C", style = bodyStyle(20, Palette.Accent, bold = true))
                            else -> Text(
                                k, style = serifStyle(if (isOp || isEq) 32 else 30, if (isEq) Color.White else if (isOp) Palette.Accent else Palette.Ink)
                            )
                        }
                    }
                }
            }
        }
        // Effacer le dernier caractère : grand bouton discret
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).clickable { press("⌫") }.padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { LineIconView(Icons.backspace, Palette.Muted, 24.dp) }
        }
    }
}

@Suppress("unused")
private fun keepPaper(m: Modifier) = m.paper()
