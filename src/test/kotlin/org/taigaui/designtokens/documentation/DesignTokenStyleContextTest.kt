package org.taigaui.designtokens.documentation

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTokenStyleContextTest : LightPlatformCodeInsightFixture4TestCase() {
    override fun setUp() {
        super.setUp()

        myFixture.addFileToProject(
            "angular.json",
            """
            {
              "projects": {
                "test": {
                  "projectType": "application",
                  "root": "",
                  "sourceRoot": "src"
                }
              }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "node_modules/@angular/core/package.json",
            """
            {
              "name": "@angular/core",
              "version": "17.3.0",
              "types": "index.d.ts"
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "node_modules/@angular/core/index.d.ts",
            """
            export interface ComponentMetadata {
                selector?: string;
                template?: string;
                styles?: string | string[];
            }

            export declare function Component(metadata: ComponentMetadata): ClassDecorator;
            """.trimIndent(),
        )
    }

    @Test
    fun `recognizes physical stylesheets`() {
        myFixture.configureByText(
            "styles.less",
            ".example { color: var(--tui-text-primary); }",
        )

        assertTrue(isDesignTokenStyleContext())
    }

    @Test
    fun `recognizes Angular inline styles as injected CSS`() {
        configureAngularFile(
            """
            import {Component} from '@angular/core';

            @Component({
                selector: 'example',
                template: '',
                styles: `
                    .example {
                        color: var(--tui-text-primary);
                    }
                `,
            })
            export class ExampleComponent {}
            """.trimIndent(),
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertTrue(isDesignTokenStyleContext())
    }

    @Test
    fun `does not treat ordinary TypeScript strings as styles`() {
        myFixture.configureByText(
            "example.ts",
            "const value = 'var(--tui-text-primary)';",
        )

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        assertFalse(isDesignTokenStyleContext())
    }

    private fun isDesignTokenStyleContext(): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            myFixture.editor.isDesignTokenStyleContext(tokenOffset())
        }

    private fun tokenOffset(): Int =
        myFixture.editor.document.text
            .indexOf("--tui-text-primary")
            .takeIf { offset -> offset >= 0 }
            ?.plus(2)
            ?: error("Token was not found")

    private fun configureAngularFile(text: String) {
        val file = myFixture.addFileToProject("src/component.ts", text)

        myFixture.configureFromExistingVirtualFile(file.virtualFile)
    }
}
