# Taiga UI Design Tokens Plugin

WebStorm plugin for exploring Taiga UI CSS custom properties directly in the editor.

The plugin will read the version of `@taiga-ui/design-tokens` installed in the current project instead of shipping a hardcoded token catalog. Quick documentation will show the declarations available for desktop, mobile, light, and dark themes.

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

Stage 1 creates the buildable WebStorm plugin scaffold. See [the implementation roadmap](docs/roadmap.md) for the following stages.

## Requirements

- JDK 21
- Gradle 9.5.0

## Commands

```bash
gradle test
gradle runIde
gradle buildPlugin
```

`runIde` starts an isolated WebStorm instance with the plugin installed. The distributable ZIP is generated under `build/distributions`.
