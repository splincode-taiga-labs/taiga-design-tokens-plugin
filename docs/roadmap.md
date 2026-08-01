# Implementation roadmap

The plugin will be developed in small reviewable stages. Each stage should leave the project in a buildable state and pass tests, ktlint, detekt, and plugin structure checks.

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

## Stage 4 — Quick documentation and navigation

Status: partially implemented.

Implemented:

- Detect a `--tui-*` custom property under the caret only when it is the first argument of `var(...)`.
- Support CSS, SCSS, and Less source files.
- Support caret positions at the beginning, middle, end, and immediately after a token name.
- Support several `var(...)` references in one value and token references nested inside fallbacks.
- Ignore declarations, comments, strings, unrelated custom properties, unsupported files, and unknown Taiga UI tokens.
- Register the IntelliJ Documentation Target API provider.
- Present contexts, declared token values, and final values in a comparison table.
- Collapse a complete set of equivalent desktop/mobile and light/dark contexts into one explicit applicability label.
- Keep incomplete context combinations listed explicitly instead of implying broader applicability.
- Show recursively resolved terminal values and nested reference trees.
- Keep source files, line numbers, and selector contexts in the resolution model without displaying them by default.
- Show a color swatch for terminal hex, named, and CSS color-function values.
- Explain missing, ambiguous, circular, and invalid values instead of pretending that one runtime value is known.
- Cover offset detection and HTML rendering with pure tests.
- Cover the complete real-filesystem-to-PSI-to-project-service-to-documentation-target path with IntelliJ Platform tests.
- Document sandbox launch and debugger attachment against a real local project.

Remaining:

- Add an optional setting for source details and clickable navigation to declarations.
- Add richer color presentation and accessibility checks for swatches.
- Test the packaged plugin manually against representative real monorepos and pnpm layouts.

## Stage 5 — Production features

- Detect project-level token overrides.
- Add completion metadata and unknown-token inspections.
- Mark deprecated tokens and suggest replacements.
- Add settings, telemetry-free diagnostics, Plugin Verifier checks, signing, and Marketplace publishing.
