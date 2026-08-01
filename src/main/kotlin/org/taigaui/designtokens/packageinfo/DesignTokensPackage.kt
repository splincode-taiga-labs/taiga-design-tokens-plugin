package org.taigaui.designtokens.packageinfo

import java.nio.file.Path

data class DesignTokensPackage(
    val root: Path,
    val realRoot: Path,
    val version: String,
)
