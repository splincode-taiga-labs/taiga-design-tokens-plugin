# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring and using Taiga UI CSS custom properties and icon names directly in the editor.

The plugin reads the Taiga UI packages installed in the current project instead of shipping hardcoded catalogs. It combines installed Taiga UI declarations with reachable project styles, resolves effective platform/theme values, and exposes that model through editor completion, unknown-token inspections, and a custom Swing hover popup. It also discovers Taiga UI SVG icons and exposes them through native `@tui.*` completion, selected-item previews, and delayed hover previews.

## Editor experience

### Token completion

Inside CSS, Less, and SCSS `var(...)` expressions, typing `--tui-` opens WebStorm's native completion lookup with design-token names discovered from the current project and installed Taiga UI packages.

The completion source is shared with unknown-token inspection. Installed package tokens and reachable project stylesheet tokens are merged into one name catalog while value resolution still uses the stricter reachable declaration graph.

The native WebStorm lookup remains the primary completion UI. When a `--tui-*` item is selected, a non-focusable preview beside the lookup resolves and shows the effective token value, source grouping, and color where applicable. Moving through the list with the keyboard updates that preview without stealing focus from the editor.

A cold completion request does not build the graph on the completion/UI path. The graph is warmed in a project-service coroutine; the latest token-name snapshot remains usable while an invalidated graph refreshes. If the first completion request starts a cold build, completion is reopened only when the caret is still inside a current `var(--tui-...)` context, so continuing to type does not invalidate the warmup result.

### Icon completion

Inside JavaScript, TypeScript, and HTML strings, typing `@tui.` opens WebStorm's native completion lookup with icon names discovered from the current project:

```ts
const arrow = '@tui.a-arrow-down';
const flag = '@tui.flags.ab';
```

Static HTML and Angular template attributes use the same completion path:

```html
<button iconStart="@tui.fancy.medium.info-circle">Save</button>
<button [iconStart]="'@tui.fancy.medium.info-circle'">Save</button>
```

Directory paths become dot-separated icon namespaces. For example, `@taiga-ui/icons/src/flags/ab.svg` becomes `@tui.flags.ab`.

Public and proprietary icon catalogs are intentionally mutually exclusive. Without `@taiga-ui/proprietary`, completion uses only the installed `@taiga-ui/icons/src/**/*.svg` package. When `@taiga-ui/proprietary` is installed, public icon names are not contributed and proprietary discovery uses the following precedence:

1. installed `@taiga-ui/tds-icons/src/**/*.svg` files;
2. when local `tds-icons` is unavailable, the T-Bank design-token icon catalog at `https://cdn.tbank.ru/core/design-tokens/v1/web/data.json`.

A nested proprietary file such as `fancy/medium/info-circle.svg` becomes `@tui.fancy.medium.info-circle`. CDN groups follow the same mapping, so an `icons` entry such as `"fancy/medium": ["air-hockey"]` becomes `@tui.fancy.medium.air-hockey`. The same group/name pair also identifies the SVG at `https://cdn.tbank.ru/core/design-tokens/v1/web/fancy/medium/air-hockey.svg`.

The native completion list has a compact, fixed-size white SVG preview beside it. Moving through `@tui.*` suggestions with the Up/Down keys immediately updates the selected icon without changing the preview width. The icon name is intentionally omitted from the preview card. SVGs are rendered at 64×64 logical units and wrapped as HiDPI-aware images so Retina displays use the corresponding device-pixel resolution instead of stretching a low-resolution raster.

Hovering a complete `@tui.*` icon reference for one second shows the same SVG preview. Moving to another icon, leaving the icon, selecting text, clicking, dragging, or opening completion cancels the pending hover or closes the current popup.

Filesystem scanning and the optional CDN request never run on the completion/UI path. The icon catalog is warmed in a project-service coroutine and cached by the nearest `node_modules/@taiga-ui` scope. A cold completion request contributes no custom icon items yet and reopens completion after warmup only if the caret is still inside an `@tui.*` string context. If WebStorm already has an HTML attribute-value lookup open, the plugin restarts that lookup after the icon catalog becomes available.

### Inspection

Unknown Taiga UI token names used as the first argument of `var(...)` are highlighted in CSS, Less, and SCSS:

```css
.demo {
    color: var(--tui-text-primry);
}
```

Incomplete prefixes can offer several `Replace with ...` quick fixes, while close typos only offer a conservative replacement when there is a sufficiently close and unambiguous known token. Distant or ambiguous unknown names remain warning-only.

The inspection uses a strict token-name snapshot. A cold or invalidated catalog does not produce warnings from stale data; the catalog is warmed in the background and daemon highlighting is restarted after a fresh snapshot becomes available.

### Hover

Hovering a complete Taiga UI design-token reference resolves its effective declarations and shows a custom Swing popup. Package and project candidates are grouped separately, color values get a swatch, and each source can be navigated to from the popup.

The popup is suppressed while completion is active and while text is selected. Moving directly from one token to another closes the stale popup immediately before the next delayed resolution starts.

## Architecture

This sequence diagram is the architectural contract for the plugin. Pull requests that change the data flow or introduce a new architectural layer must update the diagram in the same change.

```mermaid
sequenceDiagram
    actor User as Editor user
    participant Completion as Token completion contributor
    participant IconCompletion as Icon completion contributor
    participant Inspection as Unknown-token inspection
    participant CompletionService as Token-name service
    participant IconService as Icon catalog service
    participant Lookup as WebStorm lookup
    participant Preview as Token completion preview
    participant IconPreview as Icon completion preview
    participant IconHover as Icon hover controller
    participant IconRenderer as SVG preview renderer
    participant Hover as Token hover controller
    participant Service as Project token service
    participant PackageCache as Installed-package cache
    participant ProjectCache as Project-styles cache
    participant Scanner as Installed-package scanner
    participant ProjectScanner as Project stylesheet scanner
    participant Graph as Import graph
    participant Index as Token index
    participant Values as Value resolver
    participant Popup as Swing popup
    participant IconPackages as Installed icon packages
    participant IconCdn as T-Bank icon catalog
    participant VFS as VFS/document changes

    rect rgb(245, 245, 245)
        Note over User,Preview: Token completion flow
        User->>Completion: Type var(--tui-te|)
        Completion->>CompletionService: namesFor(sourceFile)

        alt Fresh token-name snapshot exists
            CompletionService-->>Completion: Known installed + project token names
        else Snapshot is cold or invalidated
            CompletionService-->>Completion: Last usable names or empty result
            CompletionService->>Service: Warm token graph in background
            Service->>PackageCache: getOrBuild(installed package graph)
            Service->>ProjectCache: getOrBuild(project stylesheet graph)
            PackageCache->>Scanner: Scan installed package styles
            ProjectCache->>ProjectScanner: Discover project stylesheet entrypoints
            ProjectScanner->>Graph: Traverse reachable local imports
            Scanner-->>PackageCache: Installed declarations
            Graph-->>ProjectCache: Reachable project declarations
            Service-->>CompletionService: Fresh token-name catalog
            CompletionService-->>Completion: Restart completion if caret still matches
        end

        Completion->>Lookup: Contribute token-name lookup items
        Lookup-->>User: Native WebStorm completion
        Lookup->>Preview: Selected --tui-* item changed
        Preview->>Service: resolveToken(sourceFile, selectedName)
        Service->>PackageCache: Read installed candidates
        Service->>ProjectCache: Read project candidates
        Service->>Values: Resolve effective contexts
        Values-->>Preview: Resolved values + colors
        Preview-->>User: Side value/color preview
        Note over Hover,Lookup: Token hover is closed and suppressed while lookup is active
    end

    rect rgb(245, 245, 245)
        Note over User,IconRenderer: Icon completion flow
        User->>IconCompletion: Type '@tui.fancy.medium.'
        IconCompletion->>IconService: namesFor(sourceFile)

        alt Cached icon catalog is ready
            IconService-->>IconCompletion: @tui.* icon names
        else Cold icon catalog
            IconService-->>IconCompletion: No custom icon items yet
            alt Proprietary project
                alt Local tds-icons is installed
                    IconService->>IconPackages: Scan @taiga-ui/tds-icons/src SVG files
                else Local tds-icons is unavailable
                    IconService->>IconCdn: Fetch grouped icon catalog
                    IconCdn-->>IconService: Grouped icon paths and names
                end
            else Public project
                IconService->>IconPackages: Scan @taiga-ui/icons/src SVG files
            end
            IconService-->>IconCompletion: Restart lookup if caret still matches @tui.*
        end

        IconCompletion->>Lookup: Contribute dot-path icon items
        Lookup-->>User: Native icon completion list
        Lookup->>IconPreview: Selected @tui.* item changed
        IconPreview->>IconService: svgSourceFor(sourceFile, selectedIcon)
        IconService-->>IconPreview: Local file or CDN SVG source
        IconPreview->>IconRenderer: Render SVG at 64x64 logical size
        IconRenderer-->>IconPreview: HiDPI-aware image
        IconPreview-->>User: Compact SVG preview on white canvas
    end

    rect rgb(245, 245, 245)
        Note over User,IconRenderer: Icon hover flow
        User->>IconHover: Hover complete @tui.* reference
        IconHover->>IconHover: Wait one second while pointer stays on icon
        IconHover->>IconService: svgSourceFor(sourceFile, iconName)
        IconService-->>IconHover: Local file or CDN SVG source
        IconHover->>IconRenderer: Render SVG at 64x64 logical size
        IconRenderer-->>IconHover: HiDPI-aware image
        IconHover-->>User: Compact SVG hover preview on white canvas
    end

    rect rgb(245, 245, 245)
        Note over User,Inspection: Inspection flow
        User->>Inspection: Daemon inspects var(--tui-token)
        Inspection->>CompletionService: strictNamesFor(sourceFile)

        alt Fresh strict snapshot exists
            CompletionService-->>Inspection: Known token names
            Inspection->>Inspection: Warn only if token is unknown
        else Strict snapshot is unavailable
            CompletionService-->>Inspection: No strict catalog yet
            CompletionService->>Service: Warm token graph in background
            Service-->>CompletionService: Fresh token-name catalog
            CompletionService->>Inspection: Restart daemon highlighting
        end
    end

    rect rgb(245, 245, 245)
        Note over User,Popup: Token hover flow
        User->>Hover: Hover var(--tui-token)
        Hover->>Service: resolveToken(sourceFile, tokenName)
        Service->>PackageCache: getOrBuild(reachable resolution index)
        Service->>ProjectCache: getOrBuild(project overrides)

        alt Cached graphs are available
            PackageCache-->>Service: Installed candidates
            ProjectCache-->>Service: Project candidates
        else Graph is cold
            Service->>PackageCache: Build outside cache monitors
            Service->>ProjectCache: Build outside cache monitors
            PackageCache->>Scanner: Scan installed declarations
            ProjectCache->>ProjectScanner: Scan reachable project declarations
            Scanner-->>PackageCache: Installed index
            ProjectScanner-->>ProjectCache: Project index
        end

        Service->>Values: Resolve effective values and references
        Values-->>Hover: Hover model
        Hover->>Popup: Render Swing popup
        Popup-->>User: Values, colors, sources, navigation
    end

    rect rgb(245, 245, 245)
        Note over VFS,ProjectCache: Invalidation flow
        VFS->>PackageCache: Package style changes invalidate installed cache
        VFS->>ProjectCache: Project style changes invalidate project cache
        VFS->>CompletionService: Invalidate token-name snapshot
        VFS->>IconService: Installed icon changes invalidate icon catalog
        Note over PackageCache,ProjectCache: Rebuild lazily with expensive builds outside cache monitors
    end
```

Architecture status:

- implemented: installed Taiga UI package discovery and declaration scanning;
- implemented: project stylesheet entrypoint discovery, local import graph traversal, project override semantics, deterministic source/cascade order where it can be proven, and safe ambiguity where it cannot;
- implemented: custom Swing hover UX with loading state, package/project grouping, `Not applied` presentation, copy/navigation actions, and non-blocking cold graph construction;
- implemented in Stage 5: installed + project token-name completion through WebStorm's native CSS/Less/SCSS lookup, background cold-cache warmup, live selected-item value/color preview, mutual exclusion between completion and hover, and unknown-token inspection with safe closest-token replacement;
- implemented: `@tui.*` icon completion with mutually exclusive public/proprietary catalogs, static HTML attribute support, local/CDN SVG source discovery, live selected-icon preview, and delayed icon hover preview;
- remaining production work: deprecated-token replacements, optional source details/settings, accessibility validation, diagnostics, verifier matrix, signing, and Marketplace publishing.

Package boundaries:

```text
org.taigaui.designtokens
├── index          immutable declaration/index contracts
├── packageinfo    installed package discovery and style scanning
├── resolution     var() parsing, candidate selection, recursive resolution, and grouping
├── psi            IntelliJ CSS/SCSS/Less PSI adapter
├── project        package/project graph orchestration, caches, invalidation, and resolution entry point
├── completion     token completion, strict inspection names, native lookup integration, and selected-item preview
├── icons          @tui.* completion, local/remote catalogs, SVG preview rendering, and icon hover
└── documentation  token-reference scanning, hover controller, Swing model, and Swing popup
```

Dependencies point inward. `completion` and `documentation` consume the project-level token service; `project` orchestrates `packageinfo`, `index`, `resolution`, and `psi`; `resolution` depends on immutable contracts from `index`; `psi` adapts IntelliJ Platform syntax trees into index declarations. `icons` is independent of the token-resolution graph and reads only the nearest installed icon-package scope plus the optional proprietary CDN fallback.

### Installed package graph

The installed-package scanner starts from the nearest `node_modules/@taiga-ui` scope for the source file. It resolves the installed Taiga UI packages and follows style imports to preserve package precedence and override behavior.

When `@taiga-ui/proprietary` is installed, the value-resolution graph intentionally keeps its reachability rules: declarations in unrelated Taiga UI package files do not become active merely because they exist somewhere under `node_modules`. The broader token-name catalog used by completion and inspection is separate and scans public style roots for known names without changing value-resolution semantics.

### Project stylesheet graph

Project stylesheet discovery starts from the current source file and configured style entrypoints, then follows local CSS/Less/SCSS imports. Reachable project declarations are indexed as an application layer over installed package declarations.

The project cache is separate from the installed-package cache because the two layers have different invalidation rates and resolution semantics.

### Cache and threading

Cache monitors protect only cache bookkeeping. Expensive graph construction and filesystem work happen outside synchronized sections.

Hover resolution, cold completion warming, and selected-item preview resolution run off the UI thread. Completion keeps a token-name snapshot so normal typing can continue to show known suggestions while document edits invalidate and refresh the underlying project graph. The completion preview listens to native lookup selection changes without requesting focus, and stale preview jobs are cancelled when keyboard navigation selects another token.

Icon catalog discovery and SVG loading/rendering also run off the UI thread. Installed SVG trees are scanned once per nearest `node_modules/@taiga-ui` scope and cached for subsequent completion and hover requests. The network fallback is attempted only when `@taiga-ui/proprietary` is present and a local `@taiga-ui/tds-icons/src` directory is not available. Selected-icon and hover previews share the cached catalog and render SVG sources at a fixed logical size with an explicit HiDPI wrapper so the same preview remains sharp on Retina displays.

Unknown-token inspection shares the same background token-name warmup but uses strict freshness. If the index is cold or invalidated, the inspection pass returns without warnings, the graph is warmed in the background, and WebStorm highlighting is restarted only after a fresh installed + project token catalog is available. Completion and inspection callbacks waiting on the same warmup are preserved independently.

### Resolution model

Project overrides are evaluated as an application layer before installed package candidates. The resolver keeps deterministic order only where the source graph proves it; otherwise conflicting candidates remain ambiguous instead of inventing an order.

Recursive `var(...)` references are resolved through the same context-aware candidate selection model, with cycle protection and grouped source information preserved for hover rendering.

## Development status

Stages 1 through 4 are implemented. Stage 5 is in progress: project override/cascade support, native token-name completion with live selected-token preview, unknown-token inspection with safe typo replacement, and `@tui.*` icon completion with selected-icon and delayed hover previews are implemented; deprecation metadata, release hardening, and publishing remain. See [the implementation roadmap](docs/roadmap.md).

## Requirements

- IntelliJ Platform / WebStorm 2025.3.6;
- Java 21;
- Gradle wrapper from the repository;
- Node.js/npm only for real-package integration fixtures.

## Local development

Install the root npm fixtures:

```bash
npm ci
```

Run checks and build the plugin:

```bash
./gradlew check buildPlugin
```

Run the isolated WebStorm sandbox:

```bash
./gradlew runIde
```

Open a real local project directly in the sandbox:

```bash
./gradlew runIde -PsandboxProject=/absolute/path/to/project
```

See [local debugging](docs/local-debugging.md) for using an installed WebStorm build, attaching a debugger on port 5005, inspecting sandbox logs, and resetting sandbox state.

`runIde` starts an isolated WebStorm instance with the plugin installed. The distributable ZIP is generated under `build/distributions`.
