package fr.essentiel.launcher.calc

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Calcul d'une expression tapée sur la calculatrice.
 * Gère + − × ÷ ^ %, parenthèses, √, sin cos tan (degrés ou radians), ln, log, π, e, ! et ².
 */
class CalcEngine(private val degrees: Boolean) {
    private var s = ""
    private var i = 0

    fun eval(expr: String): Double? = try {
        s = expr.replace(',', '.').replace(" ", "").replace(" ", "")
        i = 0
        if (s.isEmpty()) null else {
            val v = expression()
            if (i < s.length) null else v.takeIf { !it.isNaN() && !it.isInfinite() }
        }
    } catch (e: Exception) { null }

    private fun peek(): Char? = s.getOrNull(i)
    private fun startsWith(t: String) = s.startsWith(t, i)

    private fun expression(): Double {
        var v = term()
        while (true) {
            when (peek()) {
                '+' -> { i++; v += term() }
                '−', '-' -> { i++; v -= term() }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = power()
        while (true) {
            val c = peek()
            when {
                c == '×' || c == '*' -> { i++; v *= power() }
                c == '÷' || c == '/' -> { i++; v /= power() }
                // multiplication implicite : 2π, 3(4+1), (2)(3), 2√9
                c == '(' || c == 'π' || c == '√' || c == 'e' || startsWith("sin") || startsWith("cos") ||
                    startsWith("tan") || startsWith("ln") || startsWith("log") -> v *= power()
                else -> return v
            }
        }
    }

    private fun power(): Double {
        val base = unary()
        if (peek() == '^') { i++; return base.pow(power()) }
        return base
    }

    private fun unary(): Double = when (peek()) {
        '−', '-' -> { i++; -unary() }
        '+' -> { i++; unary() }
        else -> postfix()
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            when (peek()) {
                '!' -> { i++; v = factorial(v) }
                '%' -> { i++; v /= 100.0 }
                '²' -> { i++; v *= v }
                else -> return v
            }
        }
    }

    private fun func(name: String, f: (Double) -> Double): Double {
        i += name.length
        return f(primary())
    }

    private fun rad(x: Double) = if (degrees) Math.toRadians(x) else x

    private fun primary(): Double {
        val c = peek() ?: throw IllegalStateException()
        return when {
            c == '(' -> {
                i++
                val v = expression()
                if (peek() == ')') i++ // une parenthèse oubliée à la fin est tolérée
                v
            }
            c == 'π' -> { i++; PI }
            c == 'e' -> { i++; E }
            c == '√' -> func("√") { sqrt(it) }
            startsWith("sin") -> func("sin") { cleanTrig(sin(rad(it))) }
            startsWith("cos") -> func("cos") { cleanTrig(cos(rad(it))) }
            startsWith("tan") -> func("tan") { cleanTrig(tan(rad(it))) }
            startsWith("ln") -> func("ln") { ln(it) }
            startsWith("log") -> func("log") { log10(it) }
            c.isDigit() || c == '.' -> {
                val start = i
                while (peek()?.let { it.isDigit() || it == '.' } == true) i++
                s.substring(start, i).toDouble()
            }
            else -> throw IllegalStateException()
        }
    }

    private fun cleanTrig(x: Double) = if (abs(x) < 1e-12) 0.0 else x

    private fun factorial(x: Double): Double {
        if (x < 0 || x != Math.floor(x) || x > 170) throw IllegalStateException()
        var r = 1.0
        for (k in 2..x.toInt()) r *= k
        return r
    }

    companion object {
        /** Affichage à la française : 12 345,67 */
        fun format(v: Double): String {
            if (v == 0.0) return "0"
            val a = abs(v)
            if (a >= 1e15 || a < 1e-9) {
                return String.format(Locale.FRANCE, "%.6E", v).replace(Regex("0+E"), "E").replace(",E", "E")
            }
            val rounded = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
            val sym = DecimalFormatSymbols(Locale.FRANCE).apply { groupingSeparator = ' '; decimalSeparator = ',' }
            val df = DecimalFormat("#,##0.##########", sym)
            return df.format(rounded)
        }
    }
}
