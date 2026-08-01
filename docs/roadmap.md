# Implementation roadmap

The plugin will be developed in small reviewable stages. Each stage should leave the project in a buildable state and pass tests, ktlint, detekt, and plugin structure checks.

## Stage 1 — Project scaffold

- Configure Kotlin and IntelliJ Platform Gradle Plugin 2.x.
- Target WebStorm with bundled JavaScript and CSS plugins.
- Add plugin metadata, a smoke test, and CI.
- Document the intended architecture and local development commands.

## Stage 2 — Package discovery and token index

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
- Store logical variants in a project-level cached index grouped by token name.
- Invalidate the cache when token files or package metadata change.
- Cover npm, pnpm symlinks, monorepo layouts, selector nesting, selector lists, and the pinned real npm package with tests.

## Stage 3 — Recursive value resolution

- Resolve token-to-token references such as `var(--tui-status-warning)` recursively until a terminal value is reached.
- Resolve references within a deterministic compatible context rather than selecting an arbitrary declaration.
- For a mobile dark reference, prefer candidates in this order: mobile/dark, mobile/unspecified, desktop/dark, desktop/unspecified.
- For a mobile light reference, use the equivalent light order and never fall back to a conflicting theme.
- For a desktop reference, stay on desktop and prefer the exact theme before an unspecified theme.
- Treat multiple distinct candidates at the same precedence as ambiguous instead of silently choosing one.
- Keep raw declaration values unchanged and return resolved values as separate structured results.
- Preserve the complete reference chain for documentation and diagnostics.
- Support `var(...)` fallbacks, missing references, and cycle detection.
- Return an explicit unresolved or ambiguous result when static analysis cannot determine one value.
- Detect resolvable terminal color values for later previews.
- Collapse semantically equivalent resolved results for presentation without losing any physical origins.

## Stage 4 — Quick documentation and navigation

- Detect a CSS custom property under the caret inside `var(...)`.
- Implement the IntelliJ Documentation Target API.
- Render all matching logical variants grouped by platform and theme.
- Show the raw expression, recursively resolved terminal value, and reference chain.
- Show every relevant source file, line number, and selector context without duplicating equivalent hover entries.
- Add color previews for resolved color values.
- Add navigation from documentation entries to declarations.
- Explain ambiguous values instead of pretending that one runtime value is known.

## Stage 5 — Production features

- Detect project-level token overrides.
- Add completion metadata and unknown-token inspections.
- Mark deprecated tokens and suggest replacements.
- Add settings, telemetry-free diagnostics, Plugin Verifier checks, signing, and Marketplace publishing.
