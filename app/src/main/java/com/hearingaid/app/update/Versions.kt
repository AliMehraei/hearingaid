package com.hearingaid.app.update

/** Release tags look like "v1.2.0"; anything after "-" or "+" (pre-release, build) is ignored. */
object Versions {
    fun parse(tag: String?): List<Int>? {
        if (tag.isNullOrBlank()) return null
        val core = tag.trim().trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3) return null
        return parts + List(3 - parts.size) { 0 }
    }

    /** True when [candidate] is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return true
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /** "1.2" -> "1.2.0", so tags and file names always use three parts. */
    fun normalize(tag: String): String = parse(tag)?.joinToString(".") ?: tag
}
