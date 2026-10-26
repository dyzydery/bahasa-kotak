package io.github.dyzydery.bahasakotak.io.pack

/** Pulls pack JSON out of pasted text, which may be Claude's whole reply rather than the bare JSON. */
object PackText {
    private val FENCED_JSON = Regex("```(?:json)?\\s*\\n(.*?)\\n\\s*```", RegexOption.DOT_MATCHES_ALL)

    /** The last fenced block that looks like a pack, else the outermost `{…}`, else the text unchanged. */
    fun extract(text: String): String {
        FENCED_JSON.findAll(text).map { it.groupValues[1].trim() }
            .lastOrNull { it.startsWith("{") && "\"packId\"" in it }
            ?.let { return it }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else text
    }
}
