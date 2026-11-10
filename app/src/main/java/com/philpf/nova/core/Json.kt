package com.philpf.nova.core

/**
 * A small JSON reader and writer, for yt-dlp's .info.json files and the app's own storage.
 * Objects become [Map]s (in their order), arrays [List]s, numbers [Long] when whole and
 * [Double] otherwise. (Android's org.json is not available to plain unit tests.)
 */
object Json {
    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipSpace()
        val value = p.value()
        p.skipSpace()
        if (p.i != text.length) throw ParseException("Unexpected text at ${p.i}")
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is String -> quote(value, out)
            is Boolean -> out.append(value)
            is Double -> if (value.isFinite()) out.append(value) else out.append("null")
            is Float -> write(value.toDouble(), out)
            is Number -> out.append(value.toLong())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    quote(k.toString(), out)
                    out.append(':')
                    write(v, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    write(v, out)
                }
                out.append(']')
            }
            else -> quote(value.toString(), out)
        }
    }

    private fun quote(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipSpace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw ParseException("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unexpected '$c' at $i")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("Bad literal at $i")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++
            skipSpace()
            if (peek() == '}') {
                i++
                return map
            }
            while (true) {
                skipSpace()
                if (peek() != '"') throw ParseException("Expected a key at $i")
                val k = str()
                skipSpace()
                if (peek() != ':') throw ParseException("Expected ':' at $i")
                i++
                skipSpace()
                map[k] = value()
                skipSpace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return map
                    }
                    else -> throw ParseException("Expected ',' or '}' at $i")
                }
            }
        }

        private fun arr(): List<Any?> {
            val list = ArrayList<Any?>()
            i++
            skipSpace()
            if (peek() == ']') {
                i++
                return list
            }
            while (true) {
                skipSpace()
                list += value()
                skipSpace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return list
                    }
                    else -> throw ParseException("Expected ',' or ']' at $i")
                }
            }
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun str(): String {
            val out = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) throw ParseException("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseException("Unterminated string")
                        when (val e = s[i++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("Bad escape")
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw ParseException("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Number {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val text = s.substring(start, i)
            return if (text.any { it in ".eE" }) {
                text.toDoubleOrNull() ?: throw ParseException("Bad number at $start")
            } else {
                text.toLongOrNull() ?: text.toDoubleOrNull() ?: throw ParseException("Bad number at $start")
            }
        }
    }
}

/** Typed access to a parsed JSON object. */
internal fun Map<*, *>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

internal fun Map<*, *>.number(key: String): Double? = (this[key] as? Number)?.toDouble()

internal fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()
