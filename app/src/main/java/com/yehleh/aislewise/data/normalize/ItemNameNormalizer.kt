package com.yehleh.aislewise.data.normalize

/**
 * Builds the lookup key used to recognize an item the user has added before,
 * so "Eggs", " eggs" and "egg" all map to the same catalog entry.
 *
 * Only the last word is singularized ("baby carrots" -> "baby carrot"); the rules
 * are deliberately simple and tuned for grocery words.
 */
object ItemNameNormalizer {

    private val UNCHANGED = setOf(
        "molasses", "hummus", "asparagus", "couscous", "swiss", "grits", "brussels", "series", "species",
    )

    /** Singulars ending in "ie", whose plural must not become "-y" (cookies -> cookie). */
    private val IE_SINGULARS = setOf(
        "cookie", "brownie", "veggie", "smoothie", "pie", "hoagie", "pierogie", "rotisserie", "calorie",
        "movie", "tie", "beanie", "goalie", "rookie",
    )

    /** Singulars ending in "o" that pluralize with "-oes" (tomatoes -> tomato). */
    private val O_SINGULARS = setOf("tomato", "potato", "mango", "avocado", "hero", "echo", "cilantro")

    fun normalize(name: String): String {
        val cleaned = name
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}&'\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return cleaned
        val words = cleaned.split(' ')
        return (words.dropLast(1) + singularize(words.last())).joinToString(" ")
    }

    internal fun singularize(word: String): String {
        if (word.length <= 3 || word in UNCHANGED || word.endsWith("'s")) return word
        return when {
            word.endsWith("ies") ->
                if (word.dropLast(1) in IE_SINGULARS) word.dropLast(1) else word.dropLast(3) + "y"
            word.endsWith("oes") ->
                if (word.dropLast(2) in O_SINGULARS) word.dropLast(2) else word.dropLast(1)
            word.endsWith("sses") || word.endsWith("ches") || word.endsWith("shes") ||
                word.endsWith("xes") || word.endsWith("zes") -> word.dropLast(2)
            word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }
    }
}
