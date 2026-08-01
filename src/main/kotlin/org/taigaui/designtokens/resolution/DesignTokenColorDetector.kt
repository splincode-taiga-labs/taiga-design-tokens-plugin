package org.taigaui.designtokens.resolution

internal object DesignTokenColorDetector {
    fun detect(value: String): DesignTokenColorValue? {
        val cssText = value.trim()
        val lowercaseValue = cssText.lowercase()

        return when {
            HEX_COLOR.matches(cssText) ->
                DesignTokenColorValue(
                    cssText = cssText,
                    canonicalValue = canonicalHex(cssText),
                    format = DesignTokenColorFormat.HEX,
                )

            lowercaseValue in NAMED_COLORS ->
                DesignTokenColorValue(
                    cssText = cssText,
                    canonicalValue = lowercaseValue,
                    format = DesignTokenColorFormat.NAMED,
                )

            isColorFunction(cssText) ->
                DesignTokenColorValue(
                    cssText = cssText,
                    canonicalValue = canonicalFunction(cssText),
                    format = DesignTokenColorFormat.FUNCTION,
                )

            else -> null
        }
    }

    private fun canonicalHex(value: String): String {
        val digits = value.drop(1).lowercase()
        val expanded =
            when (digits.length) {
                3,
                4,
                -> digits.flatMap { digit -> listOf(digit, digit) }.joinToString("")

                else -> digits
            }

        return "#$expanded"
    }

    private fun isColorFunction(value: String): Boolean {
        val functionName = value.substringBefore('(', missingDelimiterValue = "").lowercase()

        if (functionName !in COLOR_FUNCTIONS || !value.endsWith(')')) {
            return false
        }

        var depth = 0
        var quote: Char? = null
        var escaped = false

        value.forEachIndexed { index, character ->
            when {
                escaped -> escaped = false
                character == '\\' && quote != null -> escaped = true
                quote != null && character == quote -> quote = null
                quote != null -> Unit
                character == '\'' || character == '"' -> quote = character
                character == '(' -> depth++
                character == ')' -> {
                    depth--

                    if (depth < 0 || depth == 0 && index != value.lastIndex) {
                        return false
                    }
                }
            }
        }

        return depth == 0 && quote == null
    }

    private fun canonicalFunction(value: String): String =
        WHITESPACE
            .replace(value.trim(), " ")
            .replace(WHITESPACE_AROUND_PUNCTUATION, "$1")
            .lowercase()

    private val HEX_COLOR = Regex("#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{1}|[0-9a-fA-F]{3}|[0-9a-fA-F]{5})?")
    private val WHITESPACE = Regex("\\s+")
    private val WHITESPACE_AROUND_PUNCTUATION = Regex("\\s*([(),/])\\s*")

    private val COLOR_FUNCTIONS =
        setOf(
            "color",
            "hsl",
            "hsla",
            "hwb",
            "lab",
            "lch",
            "oklab",
            "oklch",
            "rgb",
            "rgba",
        )

    private val NAMED_COLORS =
        """
        aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue
        blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk
        crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki
        darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen
        darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue
        dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite
        gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki
        lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan
        lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen
        lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen
        magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen
        mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream
        mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid
        palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum
        powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown
        seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen
        steelblue tan teal thistle tomato transparent turquoise violet wheat white whitesmoke yellow
        yellowgreen
        """.trimIndent()
            .split(Regex("\\s+"))
            .toSet()
}
