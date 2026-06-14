# Contributing

Thanks for taking an interest! This document covers the local setup and the code style. The style differs from the IDE defaults in a few places, so please skim it before opening a pull request.

## Development setup

See [Building from source](README.md#building-from-source) in the README for prerequisites and how to build. Run the unit tests with:

```sh
./gradlew test
```

An [`.editorconfig`](.editorconfig) is included, so any editor that respects it (Android Studio and VS Code do out of the box) will pick up the indentation automatically.

## Code style

### Indentation

- Indent with **tabs**, in both Kotlin and XML.
- Don't hard-wrap a line just because it's long — rely on the editor's soft-wrapping.

### Braces

Always use braces for `if`, `for`, `while`, etc. — including single-statement bodies and `if`/`else` used as an expression.

```kotlin
// wrong
if (x) return

val index = if (found) position else -1

// right
if (x) {
	return
}

val index = if (found) {
	position
} else {
	-1
}
```

### Comments

Use KDoc `/** ... */` for multi-line comments rather than stacked `//` lines.

### Naming

Avoid abbreviations. Prefer spelled-out names: `windowManager` over `wm`, `displayMetrics` over `dm`.

### Kotlin idioms

- Prefer expression-body functions, and omit the return type when it can be inferred.
- Extract long multi-clause boolean conditions into named local `val`s instead of inlining them.

```kotlin
// right
val onWifi = capabilities?.hasTransport(TRANSPORT_WIFI) == true
val canDownload = online && (forceDownload || !settings.wifiOnly || onWifi)
if (canDownload) {
	// ...
}
```
