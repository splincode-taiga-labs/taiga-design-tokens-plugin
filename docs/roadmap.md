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
- Store declarations in a project-level cached index.
- Invalidate the cache when token files or package metadata change.
- Cover npm, pnpm symlinks, and monorepo layouts with tests.

## Stage 3 — Quick documentation on hover

- Detect a CSS custom property under the caret inside `var(...)`.
- Implement the IntelliJ Documentation Target API.
- Render all matching Taiga UI declarations grouped by platform and theme.
- Show source file and line information.
- Add color previews for resolvable color values.

## Stage 4 — Value resolution and navigation

- Resolve token-to-token references such as `var(--tui-status-warning)`.
- Support fallbacks and cycle detection.
- Collapse duplicate declarations without losing source locations.
- Add navigation from documentation entries to declarations.
- Explain ambiguous values instead of pretending that one runtime value is known.

## Stage 5 — Production features

- Detect project-level token overrides.
- Add completion metadata and unknown-token inspections.
- Mark deprecated tokens and suggest replacements.
- Add settings, telemetry-free diagnostics, Plugin Verifier checks, signing, and Marketplace publishing.
