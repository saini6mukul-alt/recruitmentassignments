package com.bolo.english.translate

/** Makes Hindi text from speech recognition or typing comparable (spelling variants, punctuation, spacing). */
object HindiNormalizer {
    private const val NUKTA = '़'
    private val nuktaLetters = mapOf(
        'क़' to 'क', 'ख़' to 'ख', 'ग़' to 'ग', 'ज़' to 'ज',
        'ड़' to 'ड', 'ढ़' to 'ढ', 'फ़' to 'फ', 'य़' to 'य',
    )
    private val punctuation = Regex("[\\u0964\\u0965?!,.;:\"'()\\[\\]{}…\\-–—]")

    fun normalize(text: String): String {
        val sb = StringBuilder()
        for (c in text.lowercase()) {
            when (c) {
                NUKTA, '‌', '‍' -> Unit
                'ँ' -> sb.append('ं') // chandrabindu -> anusvara
                else -> sb.append(nuktaLetters[c] ?: c)
            }
        }
        return sb.toString()
            .replace(punctuation, " ")
            .replace("िये", "िए").replace("ाये", "ाए").replace("ये ", "ए ")
            .trim().replace(Regex("\\s+"), " ")
    }

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }
}
