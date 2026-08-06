package com.cap.haptics.unity

/**
 * A deliberately tiny JSON writer.
 *
 * `org.json` is right there in the platform, and using it would be the obvious choice — but
 * it is a stub under JVM unit tests, so every test touching the bridge's serialisation would
 * need a device. The payloads here are small, fully under our control, and written once per
 * session, so hand-rolling costs almost nothing and keeps them testable on the JVM.
 *
 * That is the same trade the model layer makes in `:haptics-core`: keeping `android.*` out of
 * the parts worth testing.
 */
internal object Json {

    fun string(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u%04x".format(char.code))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }

    /** Values must already be encoded. */
    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${string(key)}:$value"
        }

    /** Items must already be encoded. */
    fun array(items: List<String>): String = items.joinToString(prefix = "[", postfix = "]")

    fun bool(value: Boolean): String = value.toString()

    fun number(value: Int): String = value.toString()
}
