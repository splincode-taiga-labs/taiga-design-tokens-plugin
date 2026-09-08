package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ProjectStylesheetJsonPsiParserTest : BasePlatformTestCase() {
    fun testReadsStringAndObjectStyleEntriesInTextOrder() {
        val groups =
            ProjectStylesheetJsonPsiParser(project).parseStyleGroups(
                """
                {
                  "projects": {
                    "first": {
                      "styles": [
                        "src/global.css",
                        {"input": "src/theme.scss", "inject": true}
                      ]
                    },
                    "second": {
                      "targets": {
                        "build": {
                          "options": {
                            "styles": [
                              {"input": "apps/second/src/styles.less"}
                            ]
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                listOf("src/global.css", "src/theme.scss"),
                listOf("apps/second/src/styles.less"),
            ),
            groups,
        )
    }

    fun testIgnoresCommentedStylesAndNonArrayProperty() {
        val groups =
            ProjectStylesheetJsonPsiParser(project).parseStyleGroups(
                """
                {
                  // "styles": ["fake.css"],
                  "styles": "ignored.css",
                  "targets": {
                    "build": {
                      "options": {
                        "styles": ["src/real.scss", "README.md"]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(listOf(listOf("src/real.scss")), groups)
    }
}
