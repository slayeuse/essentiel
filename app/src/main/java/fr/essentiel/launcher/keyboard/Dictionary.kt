package fr.essentiel.launcher.keyboard

import android.content.Context
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Suggestions de mots : dictionnaire français (les 50 000 mots les plus fréquents, ajouté à la compilation)
 * + les mots que tu tapes souvent (appris sur le téléphone, jamais envoyés ailleurs).
 */
object Dictionary {

    private class Entry(val key: String, val word: String, val freq: Int)

    @Volatile private var entries: Array<Entry> = emptyArray()   // triés par clé sans accents
    @Volatile var loaded = false
        private set
    private val learned = ConcurrentHashMap<String, Int>()

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun load(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val list = ArrayList<Entry>(50_000)
            try {
                ctx.assets.open("dict/fr.txt").bufferedReader().useLines { lines ->
                    lines.forEachIndexed { rank, line ->
                        val w = line.substringBefore(' ').trim()
                        if (w.length in 1..30 && w.all { it.isLetter() || it == '\'' || it == '-' }) {
                            list += Entry(normalize(w), w, 50_000 - rank)
                        }
                    }
                }
            } catch (e: Exception) { /* pas de dictionnaire : seulement les mots appris */ }
            entries = list.sortedBy { it.key }.toTypedArray()
            val sp = ctx.getSharedPreferences("clavier", Context.MODE_PRIVATE)
            sp.all.forEach { (k, v) -> if (k.startsWith("w_") && v is Int) learned[k.removePrefix("w_")] = v }
            loaded = true
        }
    }

    /** Apprend un mot tapé (pour le proposer ensuite). */
    fun learn(ctx: Context, word: String) {
        val w = word.trim().trim('.', ',', '!', '?', ';', ':', '"')
        if (w.length < 2 || w.any { it.isDigit() }) return
        val n = (learned[w] ?: 0) + 1
        learned[w] = n
        ctx.getSharedPreferences("clavier", Context.MODE_PRIVATE).edit().putInt("w_$w", n).apply()
    }

    /** Jusqu'à 3 suggestions pour le début de mot tapé. */
    fun suggest(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val key = normalize(prefix)
        val scored = HashMap<String, Int>()
        // mots appris : priorité forte
        learned.forEach { (w, n) -> if (normalize(w).startsWith(key) && w != prefix) scored[w] = 1_000_000 + n }
        // dictionnaire : recherche dichotomique du début de la plage
        val arr = entries
        var lo = 0; var hi = arr.size
        while (lo < hi) { val mid = (lo + hi) / 2; if (arr[mid].key < key) lo = mid + 1 else hi = mid }
        var i = lo
        var scanned = 0
        while (i < arr.size && arr[i].key.startsWith(key) && scanned < 4000) {
            val e = arr[i]
            if (e.word != prefix) scored[e.word] = maxOf(scored[e.word] ?: 0, e.freq + if (e.key == key) 200_000 else 0)
            i++; scanned++
        }
        // conserve les majuscules tapées (ex. « Bon » → « Bonjour »)
        val upper = prefix.firstOrNull()?.isUpperCase() == true
        return scored.entries.sortedByDescending { it.value }.take(3).map { (w, _) ->
            if (upper) w.replaceFirstChar { it.uppercase() } else w
        }
    }
}
