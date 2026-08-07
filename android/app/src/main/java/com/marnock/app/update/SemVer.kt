package com.marnock.app.update

/** Compare dotted semver-ish strings (ignores leading `v` and `-suffix`). */
object SemVer {
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote)
        val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(raw: String): List<Int> {
        val core = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        if (core.isEmpty() || core == "0.0.0.dev" || core.contains("dev")) return listOf(0, 0, 0)
        return core.split('.').map { it.toIntOrNull() ?: 0 }
    }
}
