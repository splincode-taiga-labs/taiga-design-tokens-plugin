# Package-manager workspace fixtures

These fixtures exercise how the plugin discovers Taiga UI packages from source files inside workspaces.

The JVM test materializes the installed package layouts directly, keeping resolver coverage fast and deterministic:

- `npm-workspaces` models npm's hoisted workspace layout, where a dependency is resolved from the workspace root `node_modules`.
- `pnpm-workspaces` models pnpm's isolated layout, where the application has a symlink in its local `node_modules` pointing into the root `.pnpm` virtual store.

Both fixtures use the same nested application shape and declare `@taiga-ui/design-tokens` as an application dependency. The pnpm fixture additionally contains `pnpm-workspace.yaml` and a committed `pnpm-lock.yaml`.

CI also performs a real pnpm installation with `pnpm install --frozen-lockfile`. It verifies that pnpm creates the application-level `@taiga-ui/design-tokens` symlink, that the installed package has the expected version, and that the frozen install does not modify the committed lockfile.
