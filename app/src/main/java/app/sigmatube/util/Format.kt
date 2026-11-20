package app.sigmatube.util

import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale

object Format {

    private val locale = Locale.FRANCE

    /** 1234 -> "1,2 k", 3_400_000 -> "3,4 M". */
    fun compact(value: Long): String = when {
        value < 0 -> ""
        value < 1_000 -> value.toString()
        value < 1_000_000 -> scaled(value / 1_000.0, "k")
        value < 1_000_000_000 -> scaled(value / 1_000_000.0, "M")
        else -> scaled(value / 1_000_000_000.0, "Md")
    }

    private fun scaled(v: Double, suffix: String): String {
        val text = if (v < 10) String.format(locale, "%.1f", v).removeSuffix(",0") else v.toLong().toString()
        return "$text $suffix"
    }

    fun views(count: Long): String? = when {
        count < 0 -> null
        count == 1L -> "1 vue"
        count >= 1_000_000 -> "${compact(count)} de vues"
        else -> "${compact(count)} vues"
    }

    fun subscribers(count: Long): String? =
        if (count < 0) null else "${compact(count)} abonnés"

    /** 3725 -> "1:02:05", 65 -> "1:05". */
    fun duration(seconds: Long): String? {
        if (seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun position(ms: Long): String = duration(ms.coerceAtLeast(0) / 1000) ?: "0:00"

    fun relative(date: OffsetDateTime?, fallback: String?): String? {
        if (date == null) return fallback
        val d = Duration.between(date, OffsetDateTime.now())
        val days = d.toDays()
        return when {
            d.toMinutes() < 1 -> "à l'instant"
            d.toHours() < 1 -> "il y a ${d.toMinutes()} min"
            days < 1 -> "il y a ${d.toHours()} h"
            days < 7 -> plural(days, "jour")
            days < 30 -> plural(days / 7, "semaine")
            days < 365 -> "il y a ${days / 30} mois"
            else -> plural(days / 365, "an")
        }
    }

    private fun plural(n: Long, unit: String) = "il y a $n $unit${if (n > 1) "s" else ""}"

    /** "Chaîne · 1,2 k vues · il y a 3 jours", skipping missing parts. */
    fun meta(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
}
