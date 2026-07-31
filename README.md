# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring Taiga UI CSS custom properties directly in the editor.

The plugin reads the version of `@taiga-ui/design-tokens` installed in the current project instead of shipping a hardcoded token catalog. Quick documentation will show the declarations available for desktop, mobile, light, and dark themes.

## Planned experience

Given:

```css
.alert-icon {
    color: var(--tui-text-warning);
}
```

Quick documentation should show:

- the installed `@taiga-ui/design-tokens` version;
- matching desktop and mobile declarations;
- light and dark theme values;
- source files and line numbers;
- color previews when the value can be resolved.

The plugin will show all statically known candidates. It will not claim to know one runtime value when CSS cascade, DOM state, media queries, or project overrides make the result ambiguous.

## Development status

Stage 1 provides the buildable WebStorm plugin scaffold. Stage 2 currently resolves the nearest installed `@taiga-ui/design-tokens` package without invoking Node.js or a package manager. See [the implementation roadmap](docs/roadmap.md) for the following stages.

## Requirements

- JDK 21
- Node.js 22 and npm for the real-package test fixture only
- the checked-in Gradle Wrapper

The installed plugin itself does not require Node.js. The npm dependency in this repository is only a real-world fixture for tests.

## Commands

Run the test suite without installing the npm fixture:

```bash
./gradlew test
```

The real-package test is skipped when `node_modules/@taiga-ui/design-tokens` is absent.

Install the pinned package fixture and run the complete test suite:

```bash
npm ci
./gradlew test
```

Run the sandbox IDE or build the distributable plugin:

```bash
./gradlew runIde
./gradlew buildPlugin
```

`runIde` starts an isolated WebStorm instance with the plugin installed. The distributable ZIP is generated under `build/distributions`.
