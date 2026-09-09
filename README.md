# Taiga UI Design Tokens Plugin

WebStorm plugin for working with Taiga UI design tokens and icons directly in the editor.

The plugin reads the Taiga UI packages installed in the current project, so completion and previews match the version the project actually uses.

## Design tokens

### Completion

Inside CSS, Less, and SCSS `var(...)` expressions, typing `--tui-` opens WebStorm's native completion with design-token names from installed Taiga UI packages and project styles.

```css
.button {
    color: var(--tui-text-primary);
}
```

The selected completion item shows a side preview with the effective value, platform/theme variants, and a color swatch when applicable. Deprecated tokens remain discoverable but are visually marked as deprecated.

### Hover preview

Hovering a complete Taiga UI token reference shows its resolved values and sources.

The popup can display:

- effective desktop/mobile and light/dark values;
- color previews;
- project overrides and installed-package values;
- reference chains for tokens that use other `var(...)` values;
- deprecation details and an explicit replacement when the installed token source provides one;
- navigation to the source declaration.

### Unknown-token inspection

Unknown `--tui-*` references are highlighted in CSS, Less, and SCSS.

```css
.demo {
    color: var(--tui-text-primry);
}
```

When a safe match exists, the plugin offers a `Replace with ...` quick fix. Ambiguous or distant names remain warnings without an unsafe automatic replacement.

### Deprecated-token inspection

When an installed or project token declaration is explicitly documented with `@deprecated`, usages are marked as deprecated in CSS, Less, and SCSS. If the metadata names exactly one replacement `--tui-*` token, the plugin offers a `Replace with ...` quick fix.

Replacement names are read from the token source metadata for the installed project version; the plugin does not infer migration targets by fuzzy matching. A project-defined override suppresses package deprecation for the same token name unless the local declaration is itself explicitly deprecated.

### Project overrides

Token completion and value previews include reachable project CSS/Less/SCSS declarations in addition to installed Taiga UI packages.

Project overrides are shown separately from package values so it is clear which value is effectively applied.

## CSS units

### `rem` to `px` inlay hints

CSS, Less, and SCSS declarations with literal `rem` values show their pixel equivalents as unobtrusive editor hints using the browser default root size of `16px`. The same hints are shown for CSS injected into Angular component `styles` template literals.

```css
gap: 1rem;             16px
min-width: 21rem;      336px
```

Angular template style bindings with numeric `rem` literals show the same conversion next to the binding value:

```html
<tui-icon [style.font-size.rem]="1" />          <!-- editor hint: 16px -->
<tui-icon [style.border-width.rem]="0.25" />   <!-- editor hint: 4px -->
```

Dynamic expressions such as `[style.width.rem]="size"` are not evaluated. Multiple `rem` values on the same stylesheet declaration are shown in source order. Inlay hints are visual editor decorations and do not modify the source file.

## Icons

### `@tui.*` completion

Inside JavaScript, TypeScript, HTML, and Angular templates, typing `@tui.` opens native completion with icon names available to the current project.

```ts
const arrow = '@tui.a-arrow-down';
const flag = '@tui.flags.ab';
```

```html
<button iconStart="@tui.fancy.medium.info-circle">Save</button>
<button [iconStart]="'@tui.fancy.medium.info-circle'">Save</button>
```

Nested icon directories become dot-separated names. For example:

```text
@taiga-ui/icons/src/flags/ab.svg
→ @tui.flags.ab
```

### Public and proprietary icons

For public projects, icon names come from the installed `@taiga-ui/icons` package.

When `@taiga-ui/proprietary` is installed, the plugin uses proprietary icons instead:

1. installed `@taiga-ui/tds-icons` when available;
2. otherwise the T-Bank design-token icon catalog as a fallback.

### Icon preview

Selecting an `@tui.*` completion item shows its SVG preview beside the completion list.

Hovering a complete icon reference shows the same preview after a short delay. The preview is HiDPI-aware and remains sharp on Retina displays.

## Project-aware discovery

The plugin does not ship a fixed catalog of token or icon names. It discovers data from the current project, including the nearest installed Taiga UI packages and reachable project styles.

This allows different projects or monorepo packages to use different Taiga UI versions without changing plugin configuration.

## Developer documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Implementation roadmap](docs/roadmap.md)
- [Local debugging](docs/local-debugging.md)
