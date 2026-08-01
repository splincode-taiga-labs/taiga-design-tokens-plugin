# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring Taiga UI CSS custom properties directly in the editor.

The plugin reads the version of `@taiga-ui/design-tokens` installed in the current project instead of shipping a hardcoded token catalog. Quick documentation will show the declarations available for desktop, mobile, light, and dark themes.

## Planned experience

Given:

```css
.alert-icon {
    color: var(--tui-text-warning);
}
```

Quick documentation should show:

- the installed `@taiga-ui/design-tokens` version;
- matching desktop and mobile declarations;
- light and dark theme values;
- both the raw declaration and its recursively resolved value;
- the token-reference chain, source files, and line numbers;
- color previews when the final value is a color.

The plugin will show all statically known candidates. It will not claim to know one runtime value when CSS cascade, DOM state, media queries, or project overrides make the result ambiguous.

## Architecture

This sequence diagram is the architectural contract for the plugin. Pull requests that change the data flow or introduce a new architectural layer must update the diagram in the same change.

```mermaid
sequenceDiagram
    actor User as Editor user
    participant Docs as Documentation provider
    participant Service as Project token service
    participant Resolver as Package resolver
    participant Scanner as Package scanner
    participant Finder as Source-file finder
    participant Parser as CSS/SCSS/Less extractor
    participant Classifier as Context classifier
    participant Index as Project token index
    participant Values as Value resolver
    participant FS as Project filesystem

    User->>Docs: Request docs for var(--tui-*)
    Docs->>Service: find(sourceFile, tokenName)
    Service->>Resolver: resolve(sourceFile)
    Resolver->>FS: Find nearest package.json
    FS-->>Resolver: Package root and version
    Resolver-->>Service: DesignTokensPackage

    alt Index absent or invalid
        Service->>Scanner: scan(package)
        Scanner->>Finder: find(package.realRoot)
        Finder->>FS: Walk CSS, SCSS and Less files
        FS-->>Finder: Sorted source files
        Finder-->>Scanner: Source files

        loop Every source file
            Scanner->>Parser: extract(sourceFile)
            Parser->>FS: Read source or PSI
            FS-->>Parser: Syntax tree and source ranges
            Parser-->>Scanner: Raw declarations with selector context
        end

        Scanner-->>Service: Physical declarations
        Service->>Index: build(package.realRoot, declarations)

        loop Every physical declaration
            Index->>Classifier: classify(packageRoot, declaration)
            Classifier-->>Index: Mobile by path/selector, otherwise desktop; light/dark theme
            Index->>Index: Group by name + context + raw value
            Index->>Index: Retain every CSS/Less/SCSS origin
        end

        Index-->>Service: Logical token variants
    end

    Service->>Index: Find variants by token name
    Index-->>Service: Context-grouped logical variants

    loop Every matching logical variant
        Service->>Values: resolve(variant, index)

        loop Every var(--tui-*) reference
            Values->>Index: Find referenced token in context
            Index-->>Values: Candidate logical variants
            Values->>Values: Resolve recursively with fallback and cycle detection
        end

        Values-->>Service: Raw value, final value, chain, or unresolved reason
    end

    Service-->>Docs: Context-grouped resolved candidates
    Docs-->>User: Raw and final values, chain, origins, and color previews
```

Architecture status:

- implemented: package resolver, source-file finder, declaration parser, package scanner, path-based context classifier, and immutable in-memory token index;
- next in Stage 2: selector-aware extraction for `[tuiPlatform='android']` and `[tuiPlatform='ios']`, followed by project-level caching and filesystem invalidation;
- planned for Stage 3: recursive value resolution with fallbacks and cycle detection;
- planned for Stage 4: documentation provider, editor integration, and navigation.

Platform classification has no unknown state. A declaration under a `mobile` package path or a mobile `tuiPlatform` selector is mobile; every other declaration is desktop. Theme may remain unspecified when neither light nor dark context is encoded by the source.

A `DesignTokenDeclaration` is an immutable physical source fact: its `value` remains exactly what was parsed from CSS, SCSS, or Less. A `DesignTokenVariant` is a logical value candidate identified by token name, platform/theme context, and raw value. Exact copies published in CSS, Less, and SCSS become one logical variant with multiple origins rather than duplicate hover entries.

Different raw values are never merged at index time, even when they may later resolve to the same terminal value. Recursive resolution produces a separate result containing the final value, reference chain, fallback usage, or an unresolved reason. This preserves source fidelity while allowing hover documentation to show the actual color or other terminal value.

The package discovery, domain models, classification, indexing, and value-resolution layers remain independent from IntelliJ Platform APIs. Source extraction may use a narrow adapter over the bundled CSS/SCSS/Less PSI so the rest of the architecture does not depend on PSI types.

## Development status

Stage 1 provides the buildable WebStorm plugin scaffold. Stage 2 resolves the nearest installed `@taiga-ui/design-tokens` package, scans CSS, SCSS, and Less files, classifies declarations by platform and theme, and groups parallel source formats into logical token variants without invoking Node.js or a package manager at plugin runtime. See [the implementation roadmap](docs/roadmap.md) for the following stages.

## Requirements

- JDK 21
- Node.js 22 and npm for the real-package test fixture only
- the checked-in Gradle Wrapper

The installed plugin itself does not require Node.js. The npm dependency in this repository is only a real-world fixture for tests.

## Commands

Run the test suite without installing the npm fixture:

```bash
./gradlew test
```

The real-package tests are skipped when `node_modules/@taiga-ui/design-tokens` is absent.

Install the pinned package fixture and run the complete test suite:

```bash
npm ci
./gradlew test
```

Run the sandbox IDE or build the distributable plugin:

```bash
./gradlew runIde
./gradlew buildPlugin
```

`runIde` starts an isolated WebStorm instance with the plugin installed. The distributable ZIP is generated under `build/distributions`.
