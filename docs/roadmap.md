# Implementation roadmap

The plugin is developed in small reviewable stages. Each production change should leave the project in a buildable state and pass the relevant tests, ktlint, detekt, plugin structure checks, and CI.

## Stage 1 — Project scaffold

Status: implemented.

- Configure Kotlin and IntelliJ Platform Gradle Plugin 2.x.
- Target WebStorm with bundled JavaScript and CSS plugins.
- Add plugin metadata, a smoke test, and CI.
- Document the intended architecture and local development commands.

## Stage 2 — Package discovery and token index

Status: implemented.

- Locate the nearest `node_modules/@taiga-ui/design-tokens` package for the current project module.
- Read the installed package version from `package.json`.
- Extract CSS, SCSS, and Less custom-property declarations through the bundled stylesheet PSI.
- Preserve each physical declaration's raw value, source file, line number, and outer-to-inner selector chain.
- Classify declarations as mobile when they are published under a `mobile` path or enclosed by a `[tuiPlatform='android'|'ios']` or `[data-platform='android'|'ios']` selector; classify every other declaration as desktop.
- Classify declarations as light or dark when the context is encoded by the source path or a `[tuiTheme='light'|'dark']` selector; retain an unspecified theme when neither theme is encoded.
- Treat a selector list whose branches all express the same platform/theme context as one physical declaration and one logical context, regardless of attribute order or descendant-selector layout.
- Treat CSS, Less, and SCSS as parallel source representations rather than separate semantic variants.
- Group only exact duplicates with the same token name, context, and raw value into one logical variant.
- Retain every physical CSS, Less, and SCSS origin together with its selector chain on the logical variant.
- Keep different raw values separate until recursive resolution, even when they may resolve to the same terminal value.
- Cache immutable indexes by normalized real package root and installed version.
- Share one cache entry between logical npm or pnpm aliases that resolve to the same real package.
- Replace stale entries when the installed version changes or one logical package path resolves to another real target.
- Invalidate only the package affected by CSS, SCSS, Less, `package.json`, package-root, or relevant directory changes.
- Keep unrelated project files, package documentation, and other monorepo packages cached.
- Remove affected entries on VFS events and rebuild lazily on the next request instead of scanning during the write action.
- Serialize concurrent cache misses so one installed package is scanned only once.
- Do not retain a failed build as a permanent cache result.
- Cover npm, pnpm symlinks, monorepo layouts, selector nesting, selector lists, VFS changes, concurrent requests, and the pinned real npm package with tests.

## Stage 3 — Recursive value resolution

Status: implemented.

- Parse nested and compound `var(...)` expressions with a balanced scanner rather than regex replacement.
- Ignore `var(...)` text inside quoted strings and comments.
- Resolve multiple references inside one value while preserving all non-reference text.
- Resolve token-to-token references recursively until a terminal value is reached.
- Preserve the active resolution context when a mobile lookup selects a compatible desktop declaration.
- For a mobile dark reference, prefer candidates in this order: mobile/dark, mobile/unspecified, desktop/dark, desktop/unspecified.
- For a mobile light reference, use the equivalent light order and never fall back to a conflicting theme.
- For a desktop reference, stay on desktop and prefer the exact theme before an unspecified theme.
- For an unspecified theme, use only unspecified-theme candidates and never guess between light and dark.
- Treat multiple distinct candidates at the same precedence as ambiguous instead of silently choosing one.
- Keep raw declaration values unchanged and return resolved values as separate structured results.
- Preserve the complete nested reference tree, selected variants, declaration contexts, active contexts, and fallback usage.
- Support nested and empty `var(...)` fallbacks.
- Use a fallback for missing or invalid referenced values, while keeping static ambiguity explicit.
- Detect cycles with the active context included in the resolution node.
- Do not allow a fallback inside a cyclic token definition to hide its own cycle; allow an outer consumer fallback to recover from an invalid cyclic token.
- Return explicit missing, ambiguous, circular, and invalid-expression reasons.
- Detect terminal hex, named, and standard CSS color-function values for later previews.
- Collapse equivalent resolved terminal values for presentation, including canonical color equivalence, without losing root or reference-tree origins.
- Expose grouped resolution results through the project service.
- Cover pure parser and resolver semantics, project-service cache invalidation, and the pinned real npm package with tests.

## Stage 4 — Token hover and navigation

Status: implemented.

- Detect a `--tui-*` custom property under the pointer only when it is the first argument of `var(...)`.
- Support CSS, SCSS, and Less source files.
- Support offsets at the beginning, middle, end, and immediately after a token name.
- Support several `var(...)` references in one value and token references nested inside fallbacks.
- Ignore declarations, comments, strings, unrelated custom properties, unsupported files, and unknown Taiga UI tokens.
- Register one editor mouse-motion listener and debounce hover requests.
- Render one custom Swing popup instead of combining it with IntelliJ Quick Documentation.
- Keep the popup within the current screen and truncate long values with the full text available in a tooltip.
- Present grouped contexts and immediately useful final values in `Platform` and `Value` rows.
- Keep intermediate `var(...)` expressions out of the summary and expose them in the reference chain instead.
- Preserve and display the original CSS color notation while using the canonical value for grouping and swatch painting.
- Collapse a complete set of equivalent desktop/mobile and light/dark contexts into one explicit applicability label.
- Keep incomplete context combinations listed explicitly instead of implying broader applicability.
- Show recursively resolved terminal values and keep distinct chains in one left-aligned vertical list.
- Keep `Reference chain` collapsed by default and expand it on demand as an accordion.
- Extract a token description from an adjacent CSS, Less, or SCSS comment when all discovered descriptions agree.
- Keep source files, line numbers, and selector contexts in the resolution model.
- Show a checkerboard-backed color swatch for terminal colors.
- Explain missing, ambiguous, circular, and invalid values instead of pretending that one runtime value is known.
- Add `Go to definition` and `Report a bug` actions.
- Cover offset detection, popup-model mapping, and comment extraction with pure tests.
- Document sandbox launch and debugger attachment against a real local project.

## Stage 5 — Production editor features

Status: in progress.

Implemented:

### Project-aware tokens

- Discover project-level global stylesheet entrypoints from Angular and Nx configuration.
- Build a project stylesheet graph across local CSS, Less, and Sass `@import`, `@use`, and `@forward` edges.
- Resolve project-level token overrides before installed Taiga UI package declarations while preserving platform/theme scope.
- Preserve project cascade/source order for reachable stylesheets and equal-scope declarations.
- Keep overridden project and package declarations visible as explicit `Not applied` rows.
- Keep cold project/package graph builds off the UI thread and outside cache monitors.

### Token completion and inspection

- Complete installed and project-defined `--tui-*` token names inside the first argument of CSS `var(...)` without hardcoding a token catalog.
- Register completion explicitly for CSS, Less, and SCSS and auto-open WebStorm's native completion lookup while a `--tui-*` token is typed.
- Reuse the existing package/project indexes for completion and warm cold completion data in the background without invalidating an in-progress token typing session.
- Keep WebStorm's native completion lookup as the primary list instead of rendering a competing token list.
- Suppress and close the hover popup while the native completion lookup is open.
- Show a non-focusable side preview for the currently selected `--tui-*` completion item with effective platform/theme values and color swatches.
- Refresh the side preview as the selected completion item changes through keyboard navigation.
- Highlight unknown installed/project `--tui-*` references in the first `var(...)` argument for CSS, Less, and SCSS.
- Reuse the completion token-name index for inspections without building a cold graph on the inspection/UI path.
- Restart highlighting after background token-index warmup instead of reporting false unknown-token warnings from a stale catalog.
- Offer `Replace with ...` only when the closest known design token is sufficiently close and unambiguous.

### Icon completion and preview

- Complete `@tui.*` icon names in JavaScript, TypeScript, HTML, and Angular templates, including static and bound string attributes.
- Discover public icon names from the installed `@taiga-ui/icons` package instead of shipping a fixed catalog.
- Use installed `@taiga-ui/tds-icons` for proprietary projects when available.
- Fall back to the T-Bank icon catalog for proprietary projects when local TDS icons are unavailable.
- Preserve public/proprietary source precedence and keep the icon subsystem independent from token resolution.
- Show SVG previews for the selected completion item and for complete icon references on hover.
- Keep SVG loading/rendering off the UI thread and render previews sharply on HiDPI displays.

### CSS unit helpers

- Show `rem` → `px` declarative inlay hints in CSS, Less, and SCSS using the fixed browser-default assumption `1rem = 16px`.
- Support stylesheet PSI injected into JavaScript/TypeScript hosts such as Angular component `styles` template literals.
- Support Angular numeric style-unit bindings such as `[style.font-size.rem]="1"` and `[style.border-width.rem]="0.25"`.
- Ignore dynamic Angular expressions instead of guessing runtime values.
- Render unobtrusive text-without-background hints without modifying source files.

Remaining product work:

- Mark deprecated tokens and suggest replacements.
- Add an optional setting for completion/source-detail behavior where useful.
- Add an optional setting for detailed source lists and selector contexts in token hover.

## Stage 6 — Performance and architecture hardening

Status: planned.

Tracked by [#18](https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/18).

Work in small measured PRs, in this order where practical:

1. **Diagnostics and representative fixture**
   - Add telemetry-free timing/counter diagnostics for package scanning, project graph building, PSI extraction, index composition, value resolution, and icon catalog loading.
   - Add a representative Angular/Nx-style fixture with many reachable stylesheets.
   - Capture a baseline before changing cache/index behavior.

2. **Precise project stylesheet invalidation**
   - Store stylesheet dependencies with cached project scopes/indexes.
   - Invalidate only scopes that actually depend on a changed stylesheet.
   - Keep broad invalidation only for structural inputs such as workspace/project configuration and entrypoint changes.

3. **Per-file declaration cache**
   - Cache extracted declarations by normalized stylesheet path and modification state.
   - Reuse unchanged file declarations when rebuilding a project index.

4. **Immutable token resolution snapshot**
   - Cache the installed index, project index, merged index, token-name catalog, and resolver for one effective project context.
   - Avoid rebuilding/merging/sorting the same token indexes on every hover or completion-preview request.

5. **Value parsing and resolution memoization**
   - Cache parsed `var(...)` expressions and safe resolution results inside the owning snapshot/generation.
   - Preserve ambiguity, fallback, and cycle semantics.

6. **Semantic context keys**
   - Replace per-file snapshots where possible with a workspace/project/package context identity.
   - Reuse one snapshot and one cold warmup across files with the same effective context.

7. **Icon catalog invalidation**
   - Refresh local icon catalogs after relevant package/SVG changes without requiring an IDE restart.
   - Define safe refresh behavior for the remote proprietary fallback.

8. **Extensible icon sources**
   - Extract source selection into ordered strategies while preserving current public/proprietary precedence.

9. **Shared cache/warmup infrastructure and entrypoint providers**
   - Consider generation-aware shared cache primitives only after the concrete indexing work makes the common behavior clear.
   - Split stylesheet entrypoint discovery into providers when that clearly reduces complexity and enables additional build systems.

Acceptance goals:

- unrelated stylesheet edits do not rebuild unrelated project token contexts;
- unchanged stylesheets are not reparsed during incremental rebuilds;
- repeated hover/completion preview uses an existing token snapshot instead of repeatedly merging indexes;
- multiple files in one effective context can share cache state and warmups;
- icon catalogs refresh after relevant package changes;
- diagnostics demonstrate reduced redundant work on representative projects.

## Stage 7 — Release and production hardening

Status: planned.

- Add accessibility checks for color swatches, keyboard interaction, focus behavior, and editor hints where applicable.
- Test the packaged plugin manually against representative real Angular/Nx monorepos and pnpm layouts.
- Run Plugin Verifier against the supported WebStorm/IntelliJ Platform matrix.
- Resolve the intended supported IDE range and align `since-build`, target platform, and Java compatibility accordingly.
- Finalize plugin metadata and release notes.
- Add plugin signing.
- Configure JetBrains Marketplace publishing.
- Publish the first production-ready release after the supported IDE matrix and real-project validation are green.
