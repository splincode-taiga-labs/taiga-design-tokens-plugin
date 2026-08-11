# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring and using Taiga UI CSS custom properties directly in the editor.

The plugin reads the Taiga UI packages installed in the current project instead of shipping a hardcoded token catalog. It combines installed Taiga UI declarations with reachable project styles, resolves effective platform/theme values, and exposes that model through editor completion and a custom Swing hover popup.

## Editor experience

### Completion

Inside the first argument of CSS `var(...)`, typing a Taiga UI prefix offers tokens known to the current project:

```css
.alert-icon {
    color: var(--tui-text-);
}
```

Completion is available in CSS, Less, and SCSS. Suggestions come from the existing installed-package and project stylesheet indexes, so project-defined `--tui-*` tokens appear together with tokens from the installed Taiga UI version. The plugin inserts only the token name and does not add another `var(...)` wrapper.

A cold completion request does not build the graph on the completion/UI path. The graph is warmed in a project-service coroutine; the latest token-name snapshot remains usable while an invalidated graph refreshes.

### Hover

Given:

```css
.alert-icon {
    color: var(--tui-text-warning);
}
```

The hover popup shows:

- matching desktop, iOS, Android, light, and dark declarations;
- project-level overrides before installed package declarations;
- the declared value and recursively resolved result without duplicating equal values;
- separate reference chains for distinct results;
- color previews;
- explicit `Not applied` rows for declarations shadowed by a more specific package/project declaration or later project cascade position;
- actions to copy the value, navigate to the declaration, and report a bug.

Equivalent values across complete platform/theme combinations are collapsed into explicit applicability labels. Incomplete combinations stay explicit, so the UI never implies that a value applies to contexts that were not resolved.

Source files, line numbers, selector chains, package origins, and project cascade order remain available in the resolution model. The plugin shows all statically known candidates and does not claim to know one runtime value when DOM state, media queries, unrelated selector specificity, or runtime component load order keep the CSS result ambiguous.

## Architecture

This sequence diagram is the architectural contract for the plugin. Pull requests that change the data flow or introduce a new architectural layer must update the diagram in the same change.

```mermaid
sequenceDiagram
    actor User as Editor user
    participant Completion as Completion contributor
    participant CompletionService as Completion service
    participant Hover as Hover popup controller
    participant Service as Project token service
    participant PackageCache as Installed-package cache
    participant ProjectCache as Project-styles cache
    participant PackageResolver as Package resolver
    participant ProjectGraph as Project stylesheet graph
    participant Scanner as Package scanner
    participant Extractor as CSS/SCSS/Less PSI adapter
    participant Index as Token index
    participant Values as Value resolver
    participant Popup as Swing popup
    participant VFS as VFS/document changes

    rect rgb(245, 245, 245)
        Note over User,CompletionService: Completion flow
        User->>Completion: Type var(--tui-te|)
        Completion->>CompletionService: namesFor(sourceFile)

        alt Token-name snapshot or indexes are ready
            CompletionService-->>Completion: Installed + project token names
            Completion-->>User: Filtered completion items
        else Cold or invalidated graph
            CompletionService-->>Completion: Latest snapshot or no custom items yet
            CompletionService->>Service: Warm completionTokenNames(sourceFile) in background
            Service->>PackageCache: getOrBuild(package index)
            Service->>ProjectCache: getOrBuild(project index)
            CompletionService-->>Completion: Schedule a fresh auto-popup if names changed
            Completion-->>User: Updated completion items
        end
    end

    rect rgb(245, 245, 245)
        Note over User,Popup: Hover flow
        User->>Hover: Hover var(--tui-token)
        Hover->>Service: resolveToken(sourceFile, tokenName)
        Service->>PackageCache: getOrBuild(package index)
        Service->>ProjectCache: getOrBuild(project index)
        PackageCache-->>Service: Installed Taiga UI variants
        ProjectCache-->>Service: Reachable Project styles variants
        Service->>Index: Merge package + project indexes
        Service->>Values: Resolve concrete platform/theme contexts recursively
        Values-->>Service: Resolved groups + reference trees + origins
        Service-->>Hover: Context-grouped results
        Hover->>Popup: Build presentation model
        Popup-->>User: Values, overrides, chains, copy and navigation
    end

    rect rgb(245, 245, 245)
        Note over PackageResolver,Index: Index construction on cache miss
        PackageCache->>PackageResolver: Resolve installed Taiga UI style packages
        PackageResolver-->>PackageCache: Package/style roots and versions
        PackageCache->>Scanner: Scan public package styles
        Scanner->>Extractor: Extract global --tui-* declarations
        Extractor-->>Scanner: Values + selector chains + source origins
        Scanner->>Index: Build context-aware package index

        ProjectCache->>ProjectGraph: Build reachable stylesheet scope
        ProjectGraph->>ProjectGraph: Angular/Nx entrypoints + @import/@use/@forward
        ProjectGraph->>Extractor: Extract reachable project declarations
        Extractor-->>ProjectGraph: Values + selector chains + source origins
        ProjectGraph->>Index: Build project index with cascade order
    end

    VFS->>PackageCache: Invalidate affected installed-package entries
    VFS->>ProjectCache: Invalidate affected project-style entries
    Note over PackageCache,ProjectCache: Rebuild lazily; expensive builds run outside cache monitors
```

Architecture status:

- implemented: installed Taiga UI package discovery, public stylesheet discovery, PSI extraction, context classification, immutable indexing, targeted cache invalidation, and single-flight cache builds;
- implemented: balanced `var(...)` parsing, recursive reference resolution, structured fallback/cycle results, terminal color detection, and equivalent-result grouping;
- implemented: project stylesheet entrypoint discovery, local import graph traversal, project override semantics, deterministic source/cascade order where it can be proven, and safe ambiguity where it cannot;
- implemented: custom Swing hover UX with loading state, package/project grouping, `Not applied` presentation, copy/navigation actions, and non-blocking cold graph construction;
- implemented in Stage 5: installed + project token-name completion inside the first `var(...)` argument with background cold-cache warmup;
- remaining production work: unknown-token inspections, deprecated-token replacements, optional source details/settings, accessibility validation, diagnostics, verifier matrix, signing, and Marketplace publishing.

Package boundaries:

```text
org.taigaui.designtokens
├── packageinfo    installed-package discovery and metadata
├── index          declarations, variants, contexts, origins, and immutable indexes
├── resolution     var() parsing, candidate selection, recursive resolution, and grouping
├── psi            IntelliJ CSS/SCSS/Less PSI adapter
├── project        package/project graph orchestration, caches, invalidation, and resolution entry point
├── completion     completion-context detection, background warmup, snapshots, and lookup items
└── documentation  hover detection, controller, Swing model, and Swing popup
```

Dependencies point inward. `completion` and `documentation` consume the project-level token service; `project` orchestrates `packageinfo`, `index`, `resolution`, and `psi`; `resolution` depends on immutable contracts from `index`; `psi` adapts IntelliJ Platform syntax trees into index declarations.

### Installed package graph

Installed style discovery starts from the Taiga UI packages reachable from the current project. The package graph understands the published `@taiga-ui/design-tokens` sources, Taiga UI style exports, and proprietary style exports instead of assuming that every token lives in one package.

The installed-package cache is keyed by normalized real package identity/version while retaining logical roots for invalidation. Multiple npm/pnpm aliases that resolve to one physical package can share immutable data. Relevant package CSS, Less, SCSS, metadata, root, symlink-target, or directory changes invalidate only affected entries.

### Project stylesheet graph

Project styles are a separate application layer. The graph discovers Angular/Nx global style entrypoints, conventional `styles.css`/`styles.less`/`styles.scss` entrypoints, and the currently relevant stylesheet. It recursively follows local CSS/Less `@import` plus Sass `@import`, `@use`, and `@forward` edges while leaving external URLs, Sass built-ins, `node_modules`, and installed Taiga UI packages to the package graph.

Reachable project declarations receive project origin metadata and a monotonic cascade order. Project candidates are selected before installed packages, but only for the concrete platform/theme contexts they match. Source order is used only when competing project declarations have equal applicability and the same normalized selector scope; unrelated selectors remain ambiguous rather than pretending that source order alone implements the full browser cascade.

### Threading and invalidation

Cache monitors protect only cache bookkeeping. Expensive graph construction and PSI scanning execute outside those monitors, so document/VFS invalidation cannot freeze the IDE by waiting for a long build. PSI extraction uses non-blocking/cancellable read actions and checks cancellation during traversal.

Hover resolution and cold completion warming run off the UI thread. Completion keeps a token-name snapshot so normal typing can continue to show known suggestions while document edits invalidate and refresh the underlying project graph.

### Resolution model

A `DesignTokenDeclaration` is an immutable physical source fact: its raw value, source file, line, selector chain, package/project origin, and optional project cascade order are preserved. A `DesignTokenVariant` is a logical value candidate identified by token name, context, raw value, and origins. Equivalent physical CSS/Less/SCSS copies are grouped without losing navigation sources.

The value parser scans balanced functions and resolves multiple/nested references and fallbacks without replacing `var(...)` text inside strings or comments. Raw variants are never mutated.

Resolution keeps the active lookup context separate from the declaration context. A mobile lookup may use a compatible desktop fallback while references inside that declaration continue resolving in the original mobile context. Missing, ambiguous, circular, and malformed cases stay explicit.

Project overrides are evaluated as an application layer before installed package fallback. A generic project `:root` token can override all concrete package contexts; a `[tuiTheme='dark']` project declaration affects only its classified dark context; a platform + theme declaration affects only that concrete context. Non-matching project declarations are not allowed to leak back through package fallback.

## Development status

Stages 1 through 4 are implemented. Stage 5 is in progress: project override/cascade support and token-name completion are implemented; inspections, deprecation metadata, release hardening, and publishing remain. See [the implementation roadmap](docs/roadmap.md).

## Requirements

- JDK 21
- Node.js 22 and npm for real-package test fixtures only
- the checked-in Gradle Wrapper

The installed plugin itself does not require Node.js. npm dependencies in this repository are test fixtures only.

## Commands

Run tests without installing npm fixtures:

```bash
./gradlew test
```

Install the pinned package fixtures and run the complete quality gate:

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
