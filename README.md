# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring Taiga UI CSS custom properties directly in the editor.

The plugin reads the version of `@taiga-ui/design-tokens` installed in the current project instead of shipping a hardcoded token catalog. Hovering a Taiga UI token opens one custom Swing popup with desktop, mobile, light, and dark values.

## Editor experience

Given:

```css
.alert-icon {
    color: var(--tui-text-warning);
}
```

The hover popup shows:

- matching desktop and mobile declarations;
- light and dark theme values;
- the declared value and recursively resolved result without duplicating equal values;
- separate reference chains for distinct results;
- color previews;
- actions to copy the value, navigate to the declaration, and report a bug.

Equivalent values across all four platform/theme combinations are shown as one row labeled `All platforms · Light ☀️ and dark 🌚`. Incomplete combinations stay explicit, so the UI never implies that a value applies to contexts that were not resolved.

Source files, line numbers, and selector chains remain available in the resolution model. The popup uses the first root origin for `Go to definition`; detailed source lists may be exposed later through an optional setting.

The plugin shows all statically known candidates. It does not claim to know one runtime value when CSS cascade, DOM state, media queries, or project overrides make the result ambiguous.

## Architecture

This sequence diagram is the architectural contract for the plugin. Pull requests that change the data flow or introduce a new architectural layer must update the diagram in the same change.

```mermaid
sequenceDiagram
    actor User as Editor user
    participant Listener as Editor mouse listener
    participant Controller as Hover popup controller
    participant Offset as Reference-at-offset finder
    participant Service as Project token service
    participant Cache as Project index cache
    participant Resolver as Package resolver
    participant Scanner as Package scanner
    participant Finder as Source-file finder
    participant Extractor as CSS/SCSS/Less PSI adapter
    participant Classifier as Context classifier
    participant Index as Token index
    participant Values as Value resolver
    participant Parser as var() value parser
    participant Colors as Color detector
    participant Model as Swing popup model
    participant Popup as Swing popup panel
    participant VFS as VFS change listener
    participant FS as Project filesystem

    User->>Listener: Move pointer over var(--tui-*)
    Listener->>Controller: Debounced hover request
    Controller->>Offset: Find token at editor offset
    Offset-->>Controller: Token name or no target
    Controller->>Service: resolveToken(sourceFile, tokenName)
    Service->>Resolver: resolve(sourceFile)
    Resolver->>FS: Find nearest package.json
    FS-->>Resolver: Logical root, real root, and version
    Resolver-->>Service: DesignTokensPackage
    Service->>Cache: getOrBuild(package identity)

    alt Cache miss
        Cache->>Scanner: scan(package)
        Scanner->>Finder: find(package.realRoot)
        Finder->>FS: Walk CSS, SCSS and Less files
        FS-->>Finder: Sorted source files
        Finder-->>Scanner: Source files

        loop Every source file
            Scanner->>Extractor: extract(sourceFile)
            Extractor->>FS: Resolve VirtualFile and PSI
            FS-->>Extractor: Syntax tree and source ranges
            Extractor-->>Scanner: Raw declarations with selector chains
        end

        Scanner-->>Cache: Physical declarations
        Cache->>Index: build(package.realRoot, declarations)

        loop Every physical declaration
            Index->>Classifier: classify(packageRoot, declaration)
            Classifier-->>Index: Platform and theme context
            Index->>Index: Group by name, context, and raw value
            Index->>Index: Retain every CSS, Less, and SCSS origin
        end

        Index-->>Cache: Logical token variants
        Cache->>Cache: Store by real root and version
    else Cache hit
        Cache->>Cache: Reuse immutable index
    end

    Cache-->>Service: DesignTokenIndex
    Service->>Index: Find variants by token name
    Index-->>Service: Context-grouped logical variants

    loop Every matching logical variant
        Service->>Values: resolve(variant, index)
        Values->>Parser: Parse raw value
        Parser-->>Values: Text and reference AST or invalid expression

        loop Every var(--tui-*) reference
            Values->>Index: Find candidate in active context
            Index-->>Values: Selected, missing, or ambiguous candidates
            Values->>Values: Resolve selected value recursively
            Values->>Values: Apply fallback when statically valid
            Values->>Values: Detect cycles in declaration plus active context
        end

        Values->>Colors: Detect terminal color
        Colors-->>Values: Canonical color metadata or none
        Values-->>Service: Final value, reference tree, or unresolved reason
    end

    Service->>Values: Group equivalent terminal results
    Values-->>Service: Resolution groups with root and complete-tree origins
    Service-->>Controller: Context-grouped resolved candidates
    Controller->>Model: Build rows, reference chains, actions, and navigation target
    Model-->>Popup: Presentation data
    Popup-->>User: One custom Swing hover popup

    FS-->>VFS: Relevant package path changed
    VFS->>Cache: Invalidate affected package entries
    Cache->>Cache: Remove entries without rebuilding
    Note over Cache: Rebuild lazily on the next request
```

Architecture status:

- implemented: package discovery, PSI extraction, context classification, immutable indexing, project-level caching, and targeted VFS invalidation;
- implemented in Stage 3: balanced `var(...)` parsing, context-aware recursive resolution, structured fallback and cycle results, color detection, and equivalent-result grouping;
- implemented in Stage 4: caret-offset token detection, CSS/SCSS/Less support, debounced hover handling, a custom Swing popup, context comparison rows, resolved values, reference chains, copy, navigation, and color previews;
- remaining in Stage 4: optional source details and accessibility validation.

Package boundaries:

```text
org.taigaui.designtokens
├── packageinfo    package discovery and installed-package metadata
├── index          physical declarations, logical variants, classification, and indexing
├── resolution     pure var parsing, candidate selection, recursive resolution, and grouping
├── psi            IntelliJ PSI adapter for CSS, SCSS, and Less
├── project        project service, package cache, VFS invalidation, and resolution entry point
└── documentation  offset detection, hover controller, Swing model, and Swing popup
```

Dependencies point inward: `documentation` consumes the project resolution entry point; `project` orchestrates `packageinfo`, `index`, `resolution`, and `psi`; `resolution` depends on immutable contracts from `index`; `psi` depends on extraction contracts from `index`; and `index` depends only on `packageinfo` where installed-package information is required. Package discovery, the cache core, recursive resolution, offset detection, and popup-model mapping remain independently testable.

The project cache is keyed by normalized real package root and installed version. Multiple logical npm or pnpm paths that point to the same physical package reuse one immutable index. A version change or a logical package pointing to a different real target replaces the old entry.

VFS events only remove affected cache entries. CSS, SCSS, Less, `package.json`, package-root replacement, and relevant directory changes invalidate the matching package. Unrelated project files and documentation files inside the package do not rebuild the index. Rebuilding is lazy, so a burst of package-manager events produces at most one scan on the next request.

Platform classification has no unknown state. A declaration under a `mobile` package path or a mobile `tuiPlatform` or `data-platform` selector is mobile; every other declaration is desktop. Theme may remain unspecified when neither light nor dark context is encoded by the source.

A `DesignTokenDeclaration` is an immutable physical source fact: its `value` remains exactly what was parsed from CSS, SCSS, or Less. The declaration also retains its outer-to-inner selector chain. A `DesignTokenVariant` is a logical value candidate identified by token name, platform/theme context, and raw value. Exact copies published in CSS, Less, and SCSS become one logical variant with multiple origins rather than duplicate popup entries.

The value parser scans balanced functions and emits text/reference parts. It resolves several references inside compound values, nested fallbacks, and empty fallbacks without replacing `var(...)` text inside quoted strings or comments. Raw variant values are never mutated.

Reference selection is deterministic. Mobile light/dark first uses the matching mobile theme, then mobile unspecified, then the matching desktop theme, and finally desktop unspecified. Desktop references stay on desktop. Unspecified themes never guess between light and dark. Several distinct variants at the first compatible precedence produce an explicit ambiguity.

The active resolution context is separate from the selected declaration context. When a mobile lookup uses a compatible desktop declaration, references inside that declaration continue to resolve against the original mobile context, matching how default custom-property declarations and mobile overrides interact.

A `DesignTokenValueResolution` is either resolved or unresolved. Resolved results contain the final value, nested reference tree, fallback decisions, and optional canonical color metadata. Unresolved results retain explicit missing, ambiguous, circular, or invalid-expression reasons. Cycle nodes include both the declaration context and active resolution context. A fallback inside a cyclic token cannot hide its own cycle, while an outer consumer fallback may recover from an invalid cyclic referenced value.

`DesignTokenResolutionGroup` collapses equal terminal values for presentation. Hex colors such as `#fff` and `#FFFFFF` share a canonical key. Each group retains root origins separately from all origins reachable through selected references and evaluated fallbacks.

The hover listener only resolves a token when the pointer offset is inside a `--tui-*` name used as the first argument of `var(...)`. It ignores declarations, comments, strings, unrelated custom properties, unknown tokens, and unsupported file types. Nested fallback references and several references in one value are resolved independently.

The Swing popup renders one row per distinct terminal result with `Platform` and `Value` columns. It shows the original CSS color notation, uses the canonical color only for grouping and swatch painting, and displays separate reference chains for distinct results.

## Development status

Stages 1 through 3 provide the buildable WebStorm plugin scaffold, installed-package discovery, PSI extraction, logical indexing, project caching, VFS invalidation, and pure recursive value resolution. Stage 4 connects those results to one custom Swing hover popup. See [the implementation roadmap](docs/roadmap.md) for the remaining work.

## Requirements

- JDK 21
- Node.js 22 and npm for the real-package test fixture only
- the checked-in Gradle Wrapper

The installed plugin itself does not require Node.js. The npm dependency in this repository is only a real-world fixture for tests.

## Commands

Run tests without installing the npm fixture:

```bash
./gradlew test
```

The real-package tests are skipped when `node_modules/@taiga-ui/design-tokens` is absent.

Install the pinned package fixture and run the complete quality gate:

```bash
npm ci
./gradlew check
```

`check` runs tests, ktlint formatting checks, and detekt static analysis. Apply safe formatting fixes with:

```bash
./gradlew ktlintFormat
```

Run the sandbox IDE or build the distributable plugin:

```bash
./gradlew runIde
./gradlew buildPlugin
```

Open a real local project directly in the sandbox:

```bash
./gradlew runIde \
  -PdebugProjectPath="/absolute/path/to/project"
```

See [local debugging](docs/local-debugging.md) for using an installed WebStorm build, attaching a debugger on port 5005, inspecting sandbox logs, and resetting sandbox state.

`runIde` starts an isolated WebStorm instance with the plugin installed. The distributable ZIP is generated under `build/distributions`.
