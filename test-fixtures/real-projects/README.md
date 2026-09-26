# Real-project validation fixtures

These checked-in projects are release-validation fixtures for [#51](https://github.com/taiga-family-labs/taiga-design-tokens-plugin/issues/51). JVM integration tests exercise their real `angular.json`, Nx `project.json`, stylesheet graph, project overrides, deprecation metadata, and package-context isolation. The same projects can also be opened in the sandbox WebStorm for manual editor validation.

## Matrix

| Fixture | Workspace | Package manager | Taiga UI context | Configuration exercised |
| --- | --- | --- | --- | --- |
| `angular-npm-taiga-v5` | Angular 22.2 | npm | design tokens 0.322 / icons 5.25 | `angular.json` global styles |
| `nx-pnpm-taiga-mixed` / `taiga-v4` | Nx 23.2 + Angular 22.2 | pnpm | design tokens 0.248 / icons 4.93 | Nx `project.json` + nearest package root |
| `nx-pnpm-taiga-mixed` / `taiga-v5` | Nx 23.2 + Angular 22.2 | pnpm | design tokens 0.322 / icons 5.25 | Nx `project.json` + nearest package root |

The mixed Nx workspace intentionally declares Taiga UI packages in each application package. With pnpm's isolated layout, each app receives its own nearest `node_modules/@taiga-ui` symlinks and therefore exercises different effective package contexts inside one monorepo.

## Automated JVM coverage

`RealProjectValidationFixturesTest` copies these projects into temporary workspaces and exercises them through the production `DesignTokenIndexService`.

The tests verify:

- Angular `angular.json` stylesheet discovery;
- Nx `project.json` stylesheet discovery;
- project token overrides and recursive `var(...)` resolution;
- project deprecation metadata and unknown-token catalog behavior;
- different nearest Taiga UI package roots inside one Nx workspace.

The JVM tests intentionally create minimal installed-package directories after copying the checked-in projects. Existing package-resolution tests separately cover real npm/pnpm installation and symlink behavior; the manual checklist below still uses real installs.

## Install for manual validation

### Angular / npm

```bash
cd test-fixtures/real-projects/angular-npm-taiga-v5
npm install --ignore-scripts --no-audit --no-fund --package-lock=false
```

### Nx / pnpm

```bash
cd test-fixtures/real-projects/nx-pnpm-taiga-mixed
corepack pnpm install --ignore-scripts --lockfile=false
```

## Open in the plugin sandbox

From the repository root:

```bash
./gradlew buildPlugin

./gradlew runIde \
  -PdebugProjectPath="$PWD/test-fixtures/real-projects/angular-npm-taiga-v5"
```

For the monorepo:

```bash
./gradlew runIde \
  -PdebugProjectPath="$PWD/test-fixtures/real-projects/nx-pnpm-taiga-mixed"
```

See [local debugging](../../docs/local-debugging.md) for sandbox reset and log inspection.

## Reusable validation cases

Each app contains the same named cases so results can be compared across package contexts.

1. **Project override**
   - Hover `var(--tui-text-secondary)` in `app.component.less`.
   - The effective value must come from the project stylesheet, not the installed package.
   - **Go to definition** must navigate to the local declaration.

2. **Recursive resolution**
   - Hover `var(--tui-validation-alias)`.
   - The chain must resolve through `--tui-validation-base` to the terminal color.

3. **Completion**
   - Delete the end of a `--tui-*` reference and invoke completion.
   - Installed package tokens and project-defined validation tokens must remain discoverable.

4. **Unknown and deprecated inspections**
   - `--tui-validation-missing` must remain unknown.
   - `--tui-validation-old` is deprecated by its adjacent comment and must offer the explicit `--tui-validation-alias` replacement.

5. **Icon completion and preview**
   - Edit the `@tui.search` value in `app.component.html`.
   - Icon completion and preview must use the icon package nearest to the current app.

6. **Stylesheet invalidation**
   - Change `--tui-validation-base` in `theme.less`.
   - Existing hover/completion results must refresh without restarting the IDE.

7. **Unsaved editor state**
   - Change the same declaration without saving the file.
   - Features that consume PSI/document state must reflect the current editor value where supported.

8. **Monorepo context isolation**
   - Repeat completion/hover/icon checks in both Nx apps.
   - Results from one app's installed package version must not leak into the other app.

The JVM tests cover the deterministic model/index parts of this matrix. Editor UI interactions, real package-manager layouts, icon rendering, and unsaved-document behavior remain explicit sandbox checks for follow-up automation in #51.
