# Implementation roadmap

The plugin will be developed in small reviewable stages. Each stage should leave the project in a buildable state.

## Stage 1 — Project scaffold

- Configure Kotlin and IntelliJ Platform Gradle Plugin 2.x.
- Target WebStorm with bundled JavaScript and CSS plugins.
- Add plugin metadata, a smoke test, and CI.
- Document the intended architecture and local development commands.

## Stage 2 — Package discovery and token index

- Locate the nearest `node_modules/@taiga-ui/design-tokens` package for the current project module.
- Read the installed package version from `package.json`.
- Parse CSS, SCSS, and Less custom-property declarations.
- Preserve each declaration's raw value, source file, and line number.
- Classify declarations by the platform and theme context needed for later resolution.
- Store declarations in a project-level cached index grouped by token name and context.
- Invalidate the cache when token files or package metadata change.
- Cover npm, pnpm symlinks, monorepo layouts, and the pinned real npm package with tests.

## Stage 3 — Recursive value resolution

- Resolve token-to-token references such as `var(--tui-status-warning)` recursively until a terminal value is reached.
- Resolve references within the matching desktop/mobile and light/dark context instead of selecting an arbitrary declaration.
- Keep raw declaration values unchanged and return resolved values as separate structured results.
- Preserve the complete reference chain for documentation and diagnostics.
- Support `var(...)` fallbacks, missing references, and cycle detection.
- Return an explicit unresolved or ambiguous result when static analysis cannot determine one value.
- Detect resolvable terminal color values for later previews.

## Stage 4 — Quick documentation and navigation

- Detect a CSS custom property under the caret inside `var(...)`.
- Implement the IntelliJ Documentation Target API.
- Render all matching Taiga UI declarations grouped by platform and theme.
- Show the raw expression, recursively resolved terminal value, and reference chain.
- Show source files and line numbers for declarations involved in the chain.
- Add color previews for resolved color values.
- Collapse equivalent presentation entries without losing their source locations.
- Add navigation from documentation entries to declarations.
- Explain ambiguous values instead of pretending that one runtime value is known.

## Stage 5 — Production features

- Detect project-level token overrides.
- Add completion metadata and unknown-token inspections.
- Mark deprecated tokens and suggest replacements.
- Add settings, telemetry-free diagnostics, Plugin Verifier checks, signing, and Marketplace publishing.
