package org.taigaui.designtokens.documentation

import java.net.URI

internal data class TaigaDocsSource(
    val majorVersion: Int,
    val contentUri: URI,
    val documentationBaseUri: URI,
    val cacheKey: String,
) {
    fun documentationUri(sectionId: String): URI = documentationBaseUri.resolve(sectionId.trimStart('/'))
}

internal object TaigaDocsSources {
    fun forMajor(majorVersion: Int): TaigaDocsSource? =
        when (majorVersion) {
            4 ->
                TaigaDocsSource(
                    majorVersion = 4,
                    contentUri = URI.create("https://taiga-ui.dev/v4/llms-full.txt"),
                    documentationBaseUri = URI.create("https://taiga-ui.dev/v4/"),
                    cacheKey = "v4",
                )

            5 ->
                TaigaDocsSource(
                    majorVersion = 5,
                    contentUri = URI.create("https://taiga-ui.dev/llms-full.txt"),
                    documentationBaseUri = URI.create("https://taiga-ui.dev/"),
                    cacheKey = "v5",
                )

            else -> null
        }
}
